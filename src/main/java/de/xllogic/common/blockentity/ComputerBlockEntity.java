package de.xllogic.common.blockentity;

import com.google.gson.JsonObject;
import de.xllogic.common.block.ScreenBlock;
import de.xllogic.common.device.XLDefaults;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.network.XLNetworkEndpointSnapshot;
import de.xllogic.common.network.XLNetworkResolver;
import de.xllogic.runtime.ComputerOutputEntry;
import de.xllogic.runtime.ComputerPlanJobSnapshot;
import de.xllogic.common.registry.XLBlockEntities;
import de.xllogic.runtime.ComputerPlanStepSnapshot;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import de.xllogic.runtime.PythonExecutionSession;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ComputerBlockEntity extends BlockEntity {
    private static final String TAG_SCRIPT = "Script";
    private static final String TAG_AUTO_START_ON_LOAD = "AutoStartOnLoad";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_LINKED_SCREENS = "LinkedScreens";
    private static final String TAG_LAST_EXECUTION_SUCCESS = "LastExecutionSuccess";
    private static final String TAG_LAST_EXECUTION_SUMMARY = "LastExecutionSummary";
    private static final String TAG_LAST_OUTPUT = "LastOutput";
    private static final String TAG_LAST_OUTPUT_ENTRIES = "LastOutputEntries";
    private static final String TAG_LAST_PLAN_STEPS = "LastPlanSteps";
    private static final String TAG_LAST_PLAN_JOB = "LastPlanJob";
    private static final String TAG_LAST_EXECUTION_FINISHED_TICK = "LastExecutionFinishedTick";
    private static final String TAG_BRIDGE_INBOX = "BridgeInbox";
    private static final String TAG_NETWORK_ANIMATION_ACTIVE = "NetworkAnimationActive";
    private static final String STALE_RUNNING_SUMMARY = "Running program stopped because active runtime sessions are not persisted across restart or chunk reload.";
    private static final int MAX_BRIDGE_MESSAGES = 64;
    private static final int NETWORK_VALIDATION_INTERVAL_TICKS = 20;

    private String script = XLDefaults.STARTER_SCRIPT;
    private int linkedScreens;
    private ComputerRuntimeSnapshot runtimeState = ComputerRuntimeSnapshot.idle();
    private List<XLNetworkEndpointSnapshot> connectedEndpoints = List.of();
    private List<XLNetworkEndpointSnapshot> reachableEndpoints = List.of();
    private List<XLNetworkEndpointSnapshot> bridgedEndpoints = List.of();
    private List<BridgeMessage> bridgeInbox = List.of();
    private List<BlockPos> linkedScreenPositions = List.of();
    private List<BlockPos> discoveredComputerPositions = List.of();
    private boolean networkConflict;
    private long lastExecutionFinishedTick = Long.MIN_VALUE;
    private UUID activeEditorId;
    private String activeEditorName = "";
    private long lastEditorHeartbeatTick = Long.MIN_VALUE;
    private boolean networkAnimationActive;
    private long lastNetworkValidationTick = Long.MIN_VALUE;
    private long nextExecutionResumeTick = Long.MIN_VALUE;
    private boolean autoStartOnLoad;
    private boolean pendingAutoStart;
    private PythonExecutionSession executionSession;

    public ComputerBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.COMPUTER.get(), pos, blockState);
    }

    public String getScript() {
        return this.script;
    }

    public void setScript(final String script) {
        this.script = script == null ? "" : script;
        this.markStateChanged();
    }

    public boolean autoStartOnLoad() {
        return this.autoStartOnLoad;
    }

    public void setAutoStartOnLoad(final boolean autoStartOnLoad) {
        if (this.autoStartOnLoad == autoStartOnLoad) {
            if (!autoStartOnLoad) {
                this.pendingAutoStart = false;
            }
            return;
        }

        this.autoStartOnLoad = autoStartOnLoad;
        if (!autoStartOnLoad) {
            this.pendingAutoStart = false;
        }
        this.markStateChanged();
    }

    public ComputerRuntimeSnapshot getRuntimeState() {
        return this.runtimeState;
    }

    public void beginExecution(final String script) {
        this.script = script == null ? "" : script;
        this.runtimeState = ComputerRuntimeSnapshot.running(this.runtimeState);
        this.markStateChanged();
    }

    public void beginExecution(final String script, final PythonExecutionSession executionSession) {
        this.closeExecutionSession();
        this.script = script == null ? "" : script;
        this.executionSession = executionSession;
        this.nextExecutionResumeTick = executionSession == null ? Long.MIN_VALUE : currentGameTime();
        this.runtimeState = executionSession == null ? ComputerRuntimeSnapshot.running(this.runtimeState) : executionSession.snapshot();
        if (executionSession != null && executionSession.finished()) {
            this.finishExecution(executionSession.snapshot());
            return;
        }
        this.markStateChanged();
    }

    public void replaceRuntimeState(final ComputerRuntimeSnapshot runtimeState) {
        if (runtimeState == null || !runtimeState.running()) {
            this.closeExecutionSession();
        }
        this.runtimeState = runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState;
        this.markStateChanged();
    }

    public void finishExecution(final ComputerRuntimeSnapshot runtimeState) {
        this.closeExecutionSession();
        this.runtimeState = runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState;
        if (this.level != null && !this.level.isClientSide()) {
            this.lastExecutionFinishedTick = this.level.getGameTime();
        }
        this.markStateChanged();
    }

    public boolean stopExecution(final String summary) {
        if (this.executionSession == null && !this.runtimeState.running()) {
            return false;
        }

        this.finishExecution(ComputerRuntimeSnapshot.guardrailRejected(this.runtimeState, summary));
        return true;
    }

    public long executionCooldownRemainingTicks(final int cooldownTicks) {
        if (cooldownTicks <= 0 || this.runtimeState.running() || this.level == null || this.lastExecutionFinishedTick == Long.MIN_VALUE) {
            return 0L;
        }

        final long elapsedTicks = this.level.getGameTime() - this.lastExecutionFinishedTick;
        return Math.max(0L, cooldownTicks - elapsedTicks);
    }

    public boolean claimEditor(final ServerPlayer player, final int leaseTimeoutTicks) {
        if (player == null) {
            return false;
        }

        this.expireEditorLeaseIfNeeded(leaseTimeoutTicks);
        final UUID playerId = player.getUUID();
        if (this.activeEditorId != null && !this.activeEditorId.equals(playerId)) {
            return false;
        }

        this.activeEditorId = playerId;
        this.activeEditorName = sanitizeEditorName(player.getGameProfile().getName());
        this.lastEditorHeartbeatTick = currentGameTime();
        return true;
    }

    public void heartbeatEditor(final ServerPlayer player, final int leaseTimeoutTicks) {
        if (player == null) {
            return;
        }

        this.expireEditorLeaseIfNeeded(leaseTimeoutTicks);
        if (this.activeEditorId != null && this.activeEditorId.equals(player.getUUID())) {
            this.activeEditorName = sanitizeEditorName(player.getGameProfile().getName());
            this.lastEditorHeartbeatTick = currentGameTime();
        }
    }

    public void releaseEditor(final ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (this.activeEditorId != null && this.activeEditorId.equals(player.getUUID())) {
            this.resetEditorLease();
        }
    }

    public boolean isEditableBy(final ServerPlayer player, final int leaseTimeoutTicks) {
        if (player == null) {
            return false;
        }

        this.expireEditorLeaseIfNeeded(leaseTimeoutTicks);
        return this.activeEditorId != null && this.activeEditorId.equals(player.getUUID());
    }

    public boolean hasActiveEditor(final int leaseTimeoutTicks) {
        this.expireEditorLeaseIfNeeded(leaseTimeoutTicks);
        return this.activeEditorId != null;
    }

    public String activeEditorName(final int leaseTimeoutTicks) {
        this.expireEditorLeaseIfNeeded(leaseTimeoutTicks);
        return this.activeEditorId == null ? "" : this.activeEditorName;
    }

    public UUID activeEditorId(final int leaseTimeoutTicks) {
        this.expireEditorLeaseIfNeeded(leaseTimeoutTicks);
        return this.activeEditorId;
    }

    public String computerId() {
        return computerId(this.worldPosition);
    }

    public static String computerId(final BlockPos computerPos) {
        final BlockPos safePos = computerPos == null ? BlockPos.ZERO : computerPos;
        return sanitizeIdentifier("computer_" + safePos.toShortString());
    }

    public String runtimeStatus() {
        if (this.runtimeState.running()) {
            return "running";
        }
        return this.runtimeState.success() ? "ok" : "error";
    }

    public boolean receiveBridgeMessage(final BridgeMessage message) {
        if (message == null) {
            return false;
        }

        final ArrayList<BridgeMessage> inbox = new ArrayList<>(this.bridgeInbox);
        inbox.add(message);
        while (inbox.size() > MAX_BRIDGE_MESSAGES) {
            inbox.remove(0);
        }
        this.bridgeInbox = List.copyOf(inbox);
        this.markStateChanged();
        return true;
    }

    public int countBridgeMessages(final int uplinkGroup) {
        final int normalizedGroup = Math.floorMod(uplinkGroup, 16);
        int count = 0;
        for (final BridgeMessage message : this.bridgeInbox) {
            if (message.uplinkGroup() == normalizedGroup) {
                count++;
            }
        }
        return count;
    }

    public List<BridgeMessage> peekBridgeMessages(final int uplinkGroup, final int limit) {
        return filterBridgeMessages(uplinkGroup, limit, this.bridgeInbox);
    }

    public List<BridgeMessage> peekBridgeMessagesByChannel(final int uplinkGroup, final int limit, final String channel) {
        return filterBridgeMessages(uplinkGroup, limit, this.bridgeInbox, channel);
    }

    public List<BridgeMessage> drainBridgeMessages(final int uplinkGroup, final int limit) {
        final int normalizedGroup = Math.floorMod(uplinkGroup, 16);
        final int cappedLimit = Math.max(0, Math.min(limit, MAX_BRIDGE_MESSAGES));
        if (cappedLimit <= 0 || this.bridgeInbox.isEmpty()) {
            return List.of();
        }

        final ArrayList<BridgeMessage> drained = new ArrayList<>(Math.min(cappedLimit, this.bridgeInbox.size()));
        final ArrayList<BridgeMessage> remaining = new ArrayList<>(this.bridgeInbox.size());
        for (final BridgeMessage message : this.bridgeInbox) {
            if (message.uplinkGroup() == normalizedGroup && drained.size() < cappedLimit) {
                drained.add(message);
            } else {
                remaining.add(message);
            }
        }

        if (!drained.isEmpty()) {
            this.bridgeInbox = List.copyOf(remaining);
            this.markStateChanged();
        }
        return List.copyOf(drained);
    }

    public List<BridgeMessage> drainBridgeMessagesByChannel(final int uplinkGroup, final int limit, final String channel) {
        final int normalizedGroup = Math.floorMod(uplinkGroup, 16);
        final int cappedLimit = Math.max(0, Math.min(limit, MAX_BRIDGE_MESSAGES));
        if (cappedLimit <= 0 || this.bridgeInbox.isEmpty()) {
            return List.of();
        }

        final ArrayList<BridgeMessage> drained = new ArrayList<>(Math.min(cappedLimit, this.bridgeInbox.size()));
        final ArrayList<BridgeMessage> remaining = new ArrayList<>(this.bridgeInbox.size());
        for (final BridgeMessage message : this.bridgeInbox) {
            if (message.uplinkGroup() == normalizedGroup && matchesChannel(message, channel) && drained.size() < cappedLimit) {
                drained.add(message);
            } else {
                remaining.add(message);
            }
        }

        if (!drained.isEmpty()) {
            this.bridgeInbox = List.copyOf(remaining);
            this.markStateChanged();
        }
        return List.copyOf(drained);
    }

    public void refreshConnectedEndpoints() {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.computer.refreshConnectedEndpoints");
        try {
            if (this.level == null || this.level.isClientSide()) {
                return;
            }

            final List<BlockPos> previousLinkedScreens = this.linkedScreenPositions;
            this.connectedEndpoints = XLNetworkResolver.resolveEndpoints(this.level, this.worldPosition);
            this.discoveredComputerPositions = XLNetworkResolver.resolveComputers(this.level, this.worldPosition);
            this.networkConflict = this.discoveredComputerPositions.size() > 1;
            this.setNetworkAnimationActive(!this.networkConflict);
            this.bridgedEndpoints = this.networkConflict ? List.of() : XLNetworkResolver.resolveBridgedEndpoints(this.level, this.worldPosition);
            this.reachableEndpoints = mergeReachableEndpoints(this.connectedEndpoints, this.bridgedEndpoints);

            if (this.networkConflict) {
                this.invalidateDiscoveredScreens(previousLinkedScreens, resolveDiscoveredScreenPositions(this.level, this.worldPosition, this.connectedEndpoints));
                this.linkedScreenPositions = List.of();
                this.linkedScreens = 0;
                this.markStateChanged();
                return;
            }

            this.linkedScreenPositions = resolveDiscoveredScreenPositions(this.level, this.worldPosition, this.connectedEndpoints);
            this.synchronizeDiscoveredScreens(previousLinkedScreens, this.linkedScreenPositions);
            this.linkedScreens = this.linkedScreenPositions.size();
            this.markStateChanged();
        } finally {
            XLRuntimeDebugger.endSection("server.computer.refreshConnectedEndpoints", debugStartedAt);
        }
    }

    public List<XLNetworkEndpointSnapshot> getConnectedEndpoints() {
        return this.connectedEndpoints;
    }

    public List<XLNetworkEndpointSnapshot> getReachableEndpoints() {
        return this.reachableEndpoints;
    }

    public List<XLNetworkEndpointSnapshot> getBridgedEndpoints() {
        return this.bridgedEndpoints;
    }

    public List<BlockPos> getLinkedScreenPositions() {
        return this.linkedScreenPositions;
    }

    public List<BlockPos> getDiscoveredComputerPositions() {
        return this.discoveredComputerPositions;
    }

    public boolean hasNetworkConflict() {
        return this.networkConflict;
    }

    public boolean isNetworkAnimationActive() {
        return this.networkAnimationActive;
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.tryAutoStartIfNeeded();
        this.advanceExecutionSession();

        final long now = this.level.getGameTime();
        if (this.lastNetworkValidationTick != Long.MIN_VALUE && now - this.lastNetworkValidationTick < NETWORK_VALIDATION_INTERVAL_TICKS) {
            return;
        }

        this.lastNetworkValidationTick = now;
        this.refreshNetworkAnimationState();
    }

    public void refreshNetworkAnimationState() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.computer.refreshNetworkAnimationState");
        try {
            this.setNetworkAnimationActive(XLNetworkResolver.hasValidAnimationNetwork(this.level, this.worldPosition));
        } finally {
            XLRuntimeDebugger.endSection("server.computer.refreshNetworkAnimationState", debugStartedAt);
        }
    }

    public String networkConflictMessage() {
        return "Multiple computers detected on the same cable network. Separate computer segments with XLAPI blocks before discovery or execution.";
    }

    public String describeNetworkSummary() {
        final StringBuilder builder = new StringBuilder("Local endpoints: ")
                .append(this.connectedEndpoints.size())
            .append(" | bridged endpoints: ")
            .append(this.bridgedEndpoints.size())
                .append(" | discovered screens: ")
                .append(this.linkedScreens)
                .append(" | computers on segment: ")
                .append(this.discoveredComputerPositions.isEmpty() ? 1 : this.discoveredComputerPositions.size());
        if (this.networkConflict) {
            builder.append(" | invalid: separate computers with XLAPI");
        }
        return builder.toString();
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_SCRIPT)) {
            final String persistedScript = tag.getString(TAG_SCRIPT);
            final boolean shouldMigrateBundledStarter = this.level == null || !this.level.isClientSide();
            this.script = shouldMigrateBundledStarter
                    ? XLDefaults.migrateBundledStarterScript(persistedScript)
                    : persistedScript;
        } else {
            this.script = XLDefaults.STARTER_SCRIPT;
        }
        this.linkedScreens = tag.getInt(TAG_LINKED_SCREENS);
        this.connectedEndpoints = List.of();
        this.reachableEndpoints = List.of();
        this.bridgedEndpoints = List.of();
        this.bridgeInbox = readBridgeInbox(tag);
        this.linkedScreenPositions = List.of();
        this.discoveredComputerPositions = List.of();
        this.networkConflict = false;
        this.autoStartOnLoad = tag.getBoolean(TAG_AUTO_START_ON_LOAD);
        this.pendingAutoStart = this.autoStartOnLoad && !this.script.isBlank();
        this.networkAnimationActive = tag.getBoolean(TAG_NETWORK_ANIMATION_ACTIVE);
        this.lastExecutionFinishedTick = tag.contains(TAG_LAST_EXECUTION_FINISHED_TICK) ? tag.getLong(TAG_LAST_EXECUTION_FINISHED_TICK) : Long.MIN_VALUE;
        this.lastNetworkValidationTick = Long.MIN_VALUE;
        this.nextExecutionResumeTick = Long.MIN_VALUE;
        this.resetEditorLease();
        this.executionSession = null;

        final boolean running = tag.getBoolean(TAG_RUNNING);
        final boolean success = !tag.contains(TAG_LAST_EXECUTION_SUCCESS) || tag.getBoolean(TAG_LAST_EXECUTION_SUCCESS);
        final String summary = tag.contains(TAG_LAST_EXECUTION_SUMMARY) ? tag.getString(TAG_LAST_EXECUTION_SUMMARY) : "";
        final ComputerRuntimeSnapshot persistedState = new ComputerRuntimeSnapshot(
            false,
            success,
            summary,
            readOutputLines(tag),
            readOutputEntries(tag),
            readPlanSteps(tag),
            readPlanJob(tag));
        this.runtimeState = running
            ? ComputerRuntimeSnapshot.guardrailRejected(persistedState, STALE_RUNNING_SUMMARY)
            : persistedState;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_SCRIPT, this.script);
        tag.putBoolean(TAG_AUTO_START_ON_LOAD, this.autoStartOnLoad);
        tag.putBoolean(TAG_RUNNING, this.runtimeState.running());
        tag.putInt(TAG_LINKED_SCREENS, this.linkedScreens);
        tag.putBoolean(TAG_LAST_EXECUTION_SUCCESS, this.runtimeState.success());
        tag.putString(TAG_LAST_EXECUTION_SUMMARY, this.runtimeState.summary());
        tag.put(TAG_LAST_OUTPUT, writeOutputLines(this.runtimeState.outputLines()));
        tag.put(TAG_LAST_OUTPUT_ENTRIES, writeOutputEntries(this.runtimeState.outputEntries()));
        tag.put(TAG_LAST_PLAN_STEPS, writePlanSteps(this.runtimeState.planStepSnapshots()));
        if (this.runtimeState.planJobSnapshot().hasStatus()) {
            tag.put(TAG_LAST_PLAN_JOB, this.runtimeState.planJobSnapshot().toTag());
        }
        if (this.lastExecutionFinishedTick != Long.MIN_VALUE) {
            tag.putLong(TAG_LAST_EXECUTION_FINISHED_TICK, this.lastExecutionFinishedTick);
        }
        if (this.networkAnimationActive) {
            tag.putBoolean(TAG_NETWORK_ANIMATION_ACTIVE, true);
        }
        tag.put(TAG_BRIDGE_INBOX, writeBridgeInbox(this.bridgeInbox));
    }

    private void markStateChanged() {
        this.setChanged();
        if (this.level != null) {
            final BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    private void tryAutoStartIfNeeded() {
        if (!this.pendingAutoStart) {
            return;
        }

        this.pendingAutoStart = false;
        if (!this.autoStartOnLoad || this.script.isBlank() || this.runtimeState.running()) {
            return;
        }

        XLNetworking.tryAutoStartComputer(this);
    }

    private void advanceExecutionSession() {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.computer.advanceExecutionSession");
        try {
            if (this.executionSession == null) {
                return;
            }

            final ComputerRuntimeSnapshot previousState = this.runtimeState;
            final long now = currentGameTime();
            if (this.nextExecutionResumeTick == Long.MIN_VALUE || now == Long.MIN_VALUE || now >= this.nextExecutionResumeTick) {
                this.executionSession.advanceTick();
                this.nextExecutionResumeTick = now == Long.MIN_VALUE ? Long.MIN_VALUE : now + XLServerConfig.INSTANCE.persistentResumeIntervalTicks();
            }
            final ComputerRuntimeSnapshot updatedState = this.executionSession.snapshot();
            if (this.executionSession.finished()) {
                this.finishExecution(updatedState);
                return;
            }

            if (!updatedState.equals(previousState)) {
                this.runtimeState = updatedState;
                this.markStateChanged();
            }
        } finally {
            XLRuntimeDebugger.endSection("server.computer.advanceExecutionSession", debugStartedAt);
        }
    }

    private void closeExecutionSession() {
        if (this.executionSession == null) {
            return;
        }

        this.executionSession.close();
        this.executionSession = null;
        this.nextExecutionResumeTick = Long.MIN_VALUE;
    }

    private void setNetworkAnimationActive(final boolean networkAnimationActive) {
        if (this.networkAnimationActive == networkAnimationActive) {
            return;
        }

        this.networkAnimationActive = networkAnimationActive;
        this.markStateChanged();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        this.closeExecutionSession();
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    private void expireEditorLeaseIfNeeded(final int leaseTimeoutTicks) {
        if (this.activeEditorId == null || this.level == null || this.lastEditorHeartbeatTick == Long.MIN_VALUE) {
            return;
        }

        if (this.level.getGameTime() - this.lastEditorHeartbeatTick >= leaseTimeoutTicks) {
            this.resetEditorLease();
        }
    }

    public void resetEditorLease() {
        this.activeEditorId = null;
        this.activeEditorName = "";
        this.lastEditorHeartbeatTick = Long.MIN_VALUE;
    }

    private long currentGameTime() {
        return this.level == null ? Long.MIN_VALUE : this.level.getGameTime();
    }

    private static String sanitizeEditorName(final String editorName) {
        if (editorName == null || editorName.isBlank()) {
            return "another player";
        }
        return editorName;
    }

    private void invalidateDiscoveredScreens(final List<BlockPos> previousLinkedScreens, final List<BlockPos> discoveredScreens) {
        for (final BlockPos previousLinkedScreen : previousLinkedScreens) {
            this.clearDiscoveredScreen(previousLinkedScreen);
        }
        for (final BlockPos discoveredScreen : discoveredScreens) {
            this.clearDiscoveredScreen(discoveredScreen);
        }
    }

    private void synchronizeDiscoveredScreens(final List<BlockPos> previousLinkedScreens, final List<BlockPos> discoveredScreens) {
        for (final BlockPos previousScreenPos : previousLinkedScreens) {
            if (!containsPos(discoveredScreens, previousScreenPos)) {
                this.clearDiscoveredScreen(previousScreenPos);
            }
        }

        for (final BlockPos discoveredScreenPos : discoveredScreens) {
            this.bindDiscoveredScreen(discoveredScreenPos);
        }
    }

    private void clearDiscoveredScreen(final BlockPos screenPos) {
        if (this.level == null || !this.level.isLoaded(screenPos)) {
            return;
        }

        if (this.level.getBlockEntity(screenPos) instanceof ScreenBlockEntity screen && this.worldPosition.equals(screen.getLinkedComputerPos())) {
            screen.clearLinkedComputer();
        }
    }

    private void bindDiscoveredScreen(final BlockPos screenPos) {
        if (this.level == null || !this.level.isLoaded(screenPos)) {
            return;
        }

        if (this.level.getBlockEntity(screenPos) instanceof ScreenBlockEntity screen) {
            screen.setLinkedComputerPos(this.worldPosition);
        }
    }

    private static List<BlockPos> resolveDiscoveredScreenPositions(final Level level, final BlockPos computerPos,
                                                                   final List<XLNetworkEndpointSnapshot> endpoints) {
        final ArrayList<BlockPos> discoveredSeeds = new ArrayList<>();
        for (final XLNetworkEndpointSnapshot endpoint : endpoints) {
            if (!endpoint.endpointType().equals("screen") || containsPos(discoveredSeeds, endpoint.pos())) {
                continue;
            }
            discoveredSeeds.add(endpoint.pos().immutable());
        }

        if (level == null || discoveredSeeds.isEmpty()) {
            return List.copyOf(discoveredSeeds);
        }

        final ArrayList<BlockPos> expandedPositions = new ArrayList<>(discoveredSeeds.size());
        final Set<BlockPos> seen = new HashSet<>();
        for (final BlockPos seedPos : discoveredSeeds) {
            expandContiguousScreens(level, computerPos, seedPos, expandedPositions, seen);
        }
        return List.copyOf(expandedPositions);
    }

    private static void expandContiguousScreens(final Level level, final BlockPos computerPos, final BlockPos seedPos,
                                                final List<BlockPos> expandedPositions, final Set<BlockPos> seen) {
        if (!(level.getBlockEntity(seedPos) instanceof ScreenBlockEntity seedScreen)) {
            return;
        }

        final Direction facing = seedScreen.getBlockState().getValue(ScreenBlock.FACING);
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seedPos.immutable());
        while (!queue.isEmpty()) {
            final BlockPos currentPos = queue.removeFirst();
            if (seen.add(currentPos) && belongsToContiguousScreenSurface(level, computerPos, currentPos, facing)) {
                expandedPositions.add(currentPos.immutable());
                enqueueContiguousScreenNeighbors(queue, seen, currentPos, facing);
            }
        }
    }

    private static boolean belongsToContiguousScreenSurface(final Level level, final BlockPos computerPos, final BlockPos screenPos, final Direction facing) {
        if (!(level.getBlockEntity(screenPos) instanceof ScreenBlockEntity screen)) {
            return false;
        }
        if (screen.getBlockState().getValue(ScreenBlock.FACING) != facing) {
            return false;
        }

        final BlockPos linkedComputerPos = screen.getLinkedComputerPos();
        return linkedComputerPos == null || computerPos.equals(linkedComputerPos);
    }

    private static void enqueueContiguousScreenNeighbors(final ArrayDeque<BlockPos> queue, final Set<BlockPos> seen,
                                                         final BlockPos currentPos, final Direction facing) {
        for (final Direction direction : contiguousScreenDirections(facing)) {
            final BlockPos neighborPos = currentPos.relative(direction).immutable();
            if (!seen.contains(neighborPos)) {
                queue.addLast(neighborPos);
            }
        }
    }

    private static Direction[] contiguousScreenDirections(final Direction facing) {
        return new Direction[] {facing.getCounterClockWise(), facing.getClockWise(), Direction.UP, Direction.DOWN};
    }

    private static boolean containsPos(final List<BlockPos> positions, final BlockPos targetPos) {
        for (final BlockPos position : positions) {
            if (position.equals(targetPos)) {
                return true;
            }
        }
        return false;
    }

    private static List<XLNetworkEndpointSnapshot> mergeReachableEndpoints(final List<XLNetworkEndpointSnapshot> localEndpoints, final List<XLNetworkEndpointSnapshot> bridgedEndpoints) {
        if (bridgedEndpoints.isEmpty()) {
            return List.copyOf(localEndpoints);
        }

        final ArrayList<XLNetworkEndpointSnapshot> merged = new ArrayList<>(localEndpoints.size() + bridgedEndpoints.size());
        merged.addAll(localEndpoints);
        merged.addAll(bridgedEndpoints);
        return List.copyOf(merged);
    }

    private static List<BridgeMessage> filterBridgeMessages(final int uplinkGroup, final int limit, final List<BridgeMessage> messages) {
        final int normalizedGroup = Math.floorMod(uplinkGroup, 16);
        final int cappedLimit = Math.max(0, Math.min(limit, MAX_BRIDGE_MESSAGES));
        if (cappedLimit <= 0 || messages.isEmpty()) {
            return List.of();
        }

        final ArrayList<BridgeMessage> filtered = new ArrayList<>(Math.min(cappedLimit, messages.size()));
        for (final BridgeMessage message : messages) {
            if (message.uplinkGroup() == normalizedGroup) {
                filtered.add(message);
                if (filtered.size() >= cappedLimit) {
                    break;
                }
            }
        }
        return List.copyOf(filtered);
    }

    private static List<BridgeMessage> filterBridgeMessages(final int uplinkGroup, final int limit, final List<BridgeMessage> messages, final String channel) {
        final int normalizedGroup = Math.floorMod(uplinkGroup, 16);
        final int cappedLimit = Math.max(0, Math.min(limit, MAX_BRIDGE_MESSAGES));
        if (cappedLimit <= 0 || messages.isEmpty()) {
            return List.of();
        }

        final ArrayList<BridgeMessage> filtered = new ArrayList<>(Math.min(cappedLimit, messages.size()));
        for (final BridgeMessage message : messages) {
            if (message.uplinkGroup() == normalizedGroup && matchesChannel(message, channel)) {
                filtered.add(message);
                if (filtered.size() >= cappedLimit) {
                    break;
                }
            }
        }
        return List.copyOf(filtered);
    }

    private static boolean matchesChannel(final BridgeMessage message, final String channel) {
        return channel == null || channel.isBlank() || message.channel().equals(channel);
    }

    private static String sanitizeIdentifier(final String rawValue) {
        return rawValue == null ? "computer" : rawValue.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    private static List<String> readOutputLines(final CompoundTag tag) {
        if (!tag.contains(TAG_LAST_OUTPUT, Tag.TAG_LIST)) {
            return List.of();
        }

        final ListTag listTag = tag.getList(TAG_LAST_OUTPUT, Tag.TAG_STRING);
        final java.util.ArrayList<String> lines = new java.util.ArrayList<>(listTag.size());
        for (int index = 0; index < listTag.size(); index++) {
            lines.add(listTag.getString(index));
        }
        return List.copyOf(lines);
    }

    private static ListTag writeOutputLines(final List<String> outputLines) {
        final ListTag listTag = new ListTag();
        for (final String line : outputLines) {
            listTag.add(StringTag.valueOf(line));
        }
        return listTag;
    }

    private static List<ComputerOutputEntry> readOutputEntries(final CompoundTag tag) {
        if (!tag.contains(TAG_LAST_OUTPUT_ENTRIES, Tag.TAG_LIST)) {
            return List.of();
        }

        final ListTag listTag = tag.getList(TAG_LAST_OUTPUT_ENTRIES, Tag.TAG_COMPOUND);
        final java.util.ArrayList<ComputerOutputEntry> entries = new java.util.ArrayList<>(listTag.size());
        for (int index = 0; index < listTag.size(); index++) {
            entries.add(ComputerOutputEntry.fromTag(listTag.getCompound(index)));
        }
        return List.copyOf(entries);
    }

    private static ListTag writeOutputEntries(final List<ComputerOutputEntry> outputEntries) {
        final ListTag listTag = new ListTag();
        for (final ComputerOutputEntry outputEntry : outputEntries) {
            listTag.add(outputEntry.toTag());
        }
        return listTag;
    }

    private static List<ComputerPlanStepSnapshot> readPlanSteps(final CompoundTag tag) {
        if (!tag.contains(TAG_LAST_PLAN_STEPS, Tag.TAG_LIST)) {
            return List.of();
        }

        final ListTag listTag = tag.getList(TAG_LAST_PLAN_STEPS, Tag.TAG_COMPOUND);
        final java.util.ArrayList<ComputerPlanStepSnapshot> steps = new java.util.ArrayList<>(listTag.size());
        for (int index = 0; index < listTag.size(); index++) {
            steps.add(ComputerPlanStepSnapshot.fromTag(listTag.getCompound(index)));
        }
        return List.copyOf(steps);
    }

    private static ListTag writePlanSteps(final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        final ListTag listTag = new ListTag();
        for (final ComputerPlanStepSnapshot stepSnapshot : planStepSnapshots) {
            listTag.add(stepSnapshot.toTag());
        }
        return listTag;
    }

    private static ComputerPlanJobSnapshot readPlanJob(final CompoundTag tag) {
        if (!tag.contains(TAG_LAST_PLAN_JOB, Tag.TAG_COMPOUND)) {
            return ComputerPlanJobSnapshot.empty();
        }
        return ComputerPlanJobSnapshot.fromTag(tag.getCompound(TAG_LAST_PLAN_JOB));
    }

    private static List<BridgeMessage> readBridgeInbox(final CompoundTag tag) {
        if (!tag.contains(TAG_BRIDGE_INBOX, Tag.TAG_LIST)) {
            return List.of();
        }

        final ListTag listTag = tag.getList(TAG_BRIDGE_INBOX, Tag.TAG_COMPOUND);
        final ArrayList<BridgeMessage> messages = new ArrayList<>(Math.min(listTag.size(), MAX_BRIDGE_MESSAGES));
        for (int index = 0; index < listTag.size() && messages.size() < MAX_BRIDGE_MESSAGES; index++) {
            messages.add(BridgeMessage.fromTag(listTag.getCompound(index)));
        }
        return List.copyOf(messages);
    }

    private static ListTag writeBridgeInbox(final List<BridgeMessage> bridgeInbox) {
        final ListTag listTag = new ListTag();
        for (final BridgeMessage message : bridgeInbox) {
            listTag.add(message.toTag());
        }
        return listTag;
    }

    public record BridgeMessage(String sourceComputerId, String sourceComputerPosition, String sourceBridgeName, int uplinkGroup, String channel, String payload, long createdGameTime) {
        private static final int MAX_ID_LENGTH = 64;
        private static final int MAX_POSITION_LENGTH = 48;
        private static final int MAX_BRIDGE_NAME_LENGTH = 64;
        private static final int MAX_CHANNEL_LENGTH = 32;
        private static final int MAX_PAYLOAD_LENGTH = 4096;
        private static final String DEFAULT_CHANNEL = "default";
        private static final String DEFAULT_BRIDGE_NAME = "xlapi";
        private static final String TAG_SOURCE_COMPUTER_ID = "SourceComputerId";
        private static final String TAG_SOURCE_COMPUTER_POSITION = "SourceComputerPosition";
        private static final String TAG_SOURCE_BRIDGE_NAME = "SourceBridgeName";
        private static final String TAG_UPLINK_GROUP = "UplinkGroup";
        private static final String TAG_CHANNEL = "Channel";
        private static final String TAG_PAYLOAD = "Payload";
        private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";

        public BridgeMessage {
            sourceComputerId = limit(sourceComputerId, MAX_ID_LENGTH, "computer");
            sourceComputerPosition = limit(sourceComputerPosition, MAX_POSITION_LENGTH, "0, 0, 0");
            sourceBridgeName = limit(sourceBridgeName, MAX_BRIDGE_NAME_LENGTH, DEFAULT_BRIDGE_NAME);
            uplinkGroup = Math.max(0, uplinkGroup);
            channel = normalizeChannel(channel);
            payload = limit(payload, MAX_PAYLOAD_LENGTH, "");
            createdGameTime = Math.max(0L, createdGameTime);
        }

        public CompoundTag toTag() {
            final CompoundTag tag = new CompoundTag();
            tag.putString(TAG_SOURCE_COMPUTER_ID, this.sourceComputerId);
            tag.putString(TAG_SOURCE_COMPUTER_POSITION, this.sourceComputerPosition);
            tag.putString(TAG_SOURCE_BRIDGE_NAME, this.sourceBridgeName);
            tag.putInt(TAG_UPLINK_GROUP, this.uplinkGroup);
            tag.putString(TAG_CHANNEL, this.channel);
            tag.putString(TAG_PAYLOAD, this.payload);
            tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
            return tag;
        }

        public JsonObject toJson() {
            final JsonObject object = new JsonObject();
            object.addProperty("source_id", this.sourceComputerId);
            object.addProperty("source_position", this.sourceComputerPosition);
            object.addProperty("bridge_name", this.sourceBridgeName);
            object.addProperty("bridge_group", this.uplinkGroup);
            object.addProperty("channel", this.channel);
            object.addProperty("payload", this.payload);
            object.addProperty("created_game_time", this.createdGameTime);
            return object;
        }

        public static BridgeMessage fromTag(final CompoundTag tag) {
            return new BridgeMessage(
                    tag.getString(TAG_SOURCE_COMPUTER_ID),
                    tag.getString(TAG_SOURCE_COMPUTER_POSITION),
                    tag.contains(TAG_SOURCE_BRIDGE_NAME) ? tag.getString(TAG_SOURCE_BRIDGE_NAME) : DEFAULT_BRIDGE_NAME,
                    tag.getInt(TAG_UPLINK_GROUP),
                    tag.contains(TAG_CHANNEL) ? tag.getString(TAG_CHANNEL) : DEFAULT_CHANNEL,
                    tag.contains(TAG_PAYLOAD) ? tag.getString(TAG_PAYLOAD) : "",
                    tag.contains(TAG_CREATED_GAME_TIME) ? tag.getLong(TAG_CREATED_GAME_TIME) : 0L
            );
        }

        private static String normalizeChannel(final String value) {
            final String normalized = limit(value, MAX_CHANNEL_LENGTH, DEFAULT_CHANNEL).trim();
            return normalized.isBlank() ? DEFAULT_CHANNEL : normalized;
        }

        private static String limit(final String value, final int maxLength, final String fallback) {
            final String safeValue = value == null ? fallback : value;
            return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
        }
    }
}
