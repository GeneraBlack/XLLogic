package de.xllogic.common.network;

import de.xllogic.XLLogicMod;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.CraftingCPUBlockEntity;
import de.xllogic.common.blockentity.CraftingIOBlockEntity;
import de.xllogic.common.blockentity.MaterialIOBlockEntity;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.common.network.payload.ComputerRuntimeStatePayload;
import de.xllogic.common.network.payload.ComputerSessionStatus;
import de.xllogic.common.network.payload.CloseComputerSessionPayload;
import de.xllogic.common.network.payload.ExecuteComputerScriptPayload;
import de.xllogic.common.network.payload.HeartbeatComputerSessionPayload;
import de.xllogic.common.network.payload.OpenComputerStatePayload;
import de.xllogic.common.network.payload.OpenEndpointNamingPayload;
import de.xllogic.common.network.payload.ResumeRecoveryDraftPayload;
import de.xllogic.common.network.payload.ResumeRecoveryDraftResultPayload;
import de.xllogic.common.network.payload.SaveComputerStatePayload;
import de.xllogic.common.network.payload.SaveEndpointNamingPayload;
import de.xllogic.common.network.payload.StopComputerScriptPayload;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonExecutionLimits;
import de.xllogic.runtime.PythonExecutionSession;
import de.xllogic.runtime.PythonRuntime;
import de.xllogic.runtime.RuntimeFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class XLNetworking {
    private static final String PROTOCOL_VERSION = "4";
    private static final String SESSION_TARGET_UNAVAILABLE_MESSAGE = "Computer session target is unavailable. Move back into range or reopen after the chunk reloads.";
    private static final String RECOVERY_DRAFT_TARGET_UNAVAILABLE_MESSAGE = "Recovery draft is waiting for the computer to come back into range or reload.";
    private static final String RECOVERY_DRAFT_RESUMED_MESSAGE = "Recovery draft resumed. Server-backed editing restored.";
    private static final String RECOVERY_DRAFT_DIVERGED_MESSAGE = "Recovery draft and server script diverged. Automatic resume is paused until you choose how to resolve the conflict.";
    private static final String EXECUTION_STOPPED_MESSAGE = "Execution stopped by user.";
    private static final String AUTO_START_FAILURE_SUMMARY = "Auto-start failed to initialize the persisted Python program.";
    private static final int MAX_EDIT_DISTANCE = 16;
    private static final PythonRuntime SERVER_RUNTIME = RuntimeFactory.createPythonRuntime();
    private static final Map<ComputerSessionKey, UUID> TRACKED_EDITOR_SESSIONS = new HashMap<>();
    private static final Map<UUID, Set<ComputerSessionKey>> TRACKED_EDITOR_SESSIONS_BY_PLAYER = new HashMap<>();

    private XLNetworking() {
    }

    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(XLNetworking::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(XLNetworking::handlePlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(XLNetworking::handlePlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(XLNetworking::handlePlayerTickPost);
        NeoForge.EVENT_BUS.addListener(XLNetworking::handleChunkUnload);
        NeoForge.EVENT_BUS.addListener(XLNetworking::handleServerStarting);
        NeoForge.EVENT_BUS.addListener(XLNetworking::handleServerStopped);
    }

    public static void openComputerScreen(final ServerPlayer player, final ComputerBlockEntity computer) {
        PacketDistributor.sendToPlayer(player, createOpenComputerStatePayload(player, computer));
    }

    public static void openEndpointNamingScreen(final ServerPlayer player, final NamedNetworkEndpointBlockEntity endpoint) {
        if (player == null || endpoint == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createOpenEndpointNamingPayload(endpoint));
    }

    public static OpenComputerStatePayload createOpenComputerStatePayload(final ServerPlayer player, final ComputerBlockEntity computer) {
        return createOpenComputerStatePayload(player, computer, editorLeaseTimeoutTicks());
    }

    public static ComputerRuntimeStatePayload createComputerRuntimeStatePayload(final ServerPlayer player, final ComputerBlockEntity computer) {
        return createComputerRuntimeStatePayload(player, computer, editorLeaseTimeoutTicks());
    }

    public static ComputerRuntimeStatePayload synchronizeComputerSession(final ServerPlayer player, final ComputerBlockEntity computer) {
        return synchronizeComputerSession(player, computer, editorLeaseTimeoutTicks());
    }

    public static void releaseEditorSessionsForPlayer(final ServerPlayer player) {
        if (player == null || player.serverLevel().getServer() == null) {
            return;
        }

        final Set<ComputerSessionKey> trackedSessions = TRACKED_EDITOR_SESSIONS_BY_PLAYER.remove(player.getUUID());
        if (trackedSessions == null || trackedSessions.isEmpty()) {
            return;
        }

        for (final ComputerSessionKey session : Set.copyOf(trackedSessions)) {
            TRACKED_EDITOR_SESSIONS.remove(session);
            final ServerLevel level = player.serverLevel().getServer().getLevel(session.dimension());
            if (level == null || !level.isLoaded(session.position())) {
                continue;
            }

            if (level.getBlockEntity(session.position()) instanceof ComputerBlockEntity computer) {
                computer.releaseEditor(player);
                synchronizeTrackedEditorSession(computer, editorLeaseTimeoutTicks());
            }
        }
    }

    public static void releaseEditorSessionsForDimensionChange(final ServerPlayer player) {
        releaseEditorSessionsForPlayer(player);
    }

    public static void releaseTrackedEditorSession(final ComputerBlockEntity computer) {
        if (computer == null || !(computer.getLevel() instanceof ServerLevel level)) {
            return;
        }

        final ComputerSessionKey sessionKey = new ComputerSessionKey(level.dimension(), computer.getBlockPos());
        final UUID previousEditorId = TRACKED_EDITOR_SESSIONS.remove(sessionKey);
        if (previousEditorId != null) {
            removeTrackedEditorSession(previousEditorId, sessionKey);
        }
    }

    public static void releaseEditorSessionsForChunkUnload(final LevelChunk chunk) {
        if (chunk == null || !(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }

        for (final var blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
            if (blockEntity instanceof ComputerBlockEntity computer) {
                releaseTrackedEditorSession(computer);
                computer.resetEditorLease();
            }
        }
    }

    public static void resetTrackedEditorSessions() {
        TRACKED_EDITOR_SESSIONS.clear();
        TRACKED_EDITOR_SESSIONS_BY_PLAYER.clear();
    }

    public static void validateTrackedEditorSessionsForPlayer(final ServerPlayer player) {
        if (player == null || player.serverLevel().getServer() == null) {
            return;
        }

        final Set<ComputerSessionKey> trackedSessions = TRACKED_EDITOR_SESSIONS_BY_PLAYER.get(player.getUUID());
        if (trackedSessions == null || trackedSessions.isEmpty()) {
            return;
        }

        final int leaseTimeoutTicks = editorLeaseTimeoutTicks();
        for (final ComputerSessionKey session : Set.copyOf(trackedSessions)) {
            final ServerLevel level = player.serverLevel().getServer().getLevel(session.dimension());
            if (level == null || !level.isLoaded(session.position()) || !(level.getBlockEntity(session.position()) instanceof ComputerBlockEntity computer)) {
                TRACKED_EDITOR_SESSIONS.remove(session);
                removeTrackedEditorSession(player.getUUID(), session);
            } else if (!computer.isEditableBy(player, leaseTimeoutTicks)) {
                synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
            } else {
                final boolean playerStillInReach = player.level().dimension().equals(session.dimension())
                        && player.serverLevel().isLoaded(session.position())
                        && player.blockPosition().distManhattan(session.position()) <= MAX_EDIT_DISTANCE;
                if (!playerStillInReach) {
                    computer.releaseEditor(player);
                    synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
                }
            }
        }
    }

    private static void onRegisterPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(OpenComputerStatePayload.PAYLOAD_TYPE, OpenComputerStatePayload.STREAM_CODEC, XLNetworking::handleOpenComputerState);
        registrar.playToClient(OpenEndpointNamingPayload.TYPE, OpenEndpointNamingPayload.STREAM_CODEC, XLNetworking::handleOpenEndpointNaming);
        registrar.playToClient(ComputerRuntimeStatePayload.PAYLOAD_TYPE, ComputerRuntimeStatePayload.STREAM_CODEC, XLNetworking::handleComputerRuntimeState);
        registrar.playToClient(ResumeRecoveryDraftResultPayload.PAYLOAD_TYPE, ResumeRecoveryDraftResultPayload.STREAM_CODEC, XLNetworking::handleResumeRecoveryDraftResult);
        registrar.playToServer(SaveEndpointNamingPayload.TYPE, SaveEndpointNamingPayload.STREAM_CODEC, XLNetworking::handleSaveEndpointNaming);
        registrar.playToServer(SaveComputerStatePayload.TYPE, SaveComputerStatePayload.STREAM_CODEC, XLNetworking::handleSaveComputerState);
        registrar.playToServer(ExecuteComputerScriptPayload.TYPE, ExecuteComputerScriptPayload.STREAM_CODEC, XLNetworking::handleExecuteComputerScript);
        registrar.playToServer(StopComputerScriptPayload.TYPE, StopComputerScriptPayload.STREAM_CODEC, XLNetworking::handleStopComputerScript);
        registrar.playToServer(HeartbeatComputerSessionPayload.TYPE, HeartbeatComputerSessionPayload.STREAM_CODEC, XLNetworking::handleHeartbeatComputerSession);
        registrar.playToServer(CloseComputerSessionPayload.TYPE, CloseComputerSessionPayload.STREAM_CODEC, XLNetworking::handleCloseComputerSession);
        registrar.playToServer(ResumeRecoveryDraftPayload.PAYLOAD_TYPE, ResumeRecoveryDraftPayload.STREAM_CODEC, XLNetworking::handleResumeRecoveryDraft);
    }

    private static void handleOpenComputerState(final OpenComputerStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.openComputerScreen(payload)).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to open synced computer screen from payload.", exception);
            return null;
        });
    }

    private static void handleOpenEndpointNaming(final OpenEndpointNamingPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.openEndpointNamingScreen(payload)).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to open endpoint naming screen from payload.", exception);
            return null;
        });
    }

    private static void handleComputerRuntimeState(final ComputerRuntimeStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.updateComputerRuntime(payload)).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to update synced computer runtime from payload.", exception);
            return null;
        });
    }

    private static void handleResumeRecoveryDraftResult(final ResumeRecoveryDraftResultPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.applyRecoveryDraftResumeResult(payload)).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to apply recovery-draft resume result from payload.", exception);
            return null;
        });
    }

    private static void handleSaveComputerState(final SaveComputerStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            final ComputerBlockEntity computer = getNearbyComputer(player, payload.computerPos());
            if (computer == null) {
                validateTrackedEditorSessionsForPlayer(player);
                sendUnavailableRuntimeState(player, payload.computerPos(), SESSION_TARGET_UNAVAILABLE_MESSAGE);
                return;
            }

            if (!ensureEditorLease(player, computer)) {
                sendRuntimeState(player, computer);
                return;
            }

            computer.setAutoStartOnLoad(payload.autoStartOnLoad());
            computer.setScript(payload.script());
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to save computer state from payload.", exception);
            return null;
        });
    }

    private static void handleSaveEndpointNaming(final SaveEndpointNamingPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            final NamedNetworkEndpointBlockEntity endpoint = getNearbyEndpoint(player, payload.endpointPos());
            if (endpoint == null) {
                player.sendSystemMessage(Component.literal("Endpoint config target is unavailable. Move closer or reload the chunk."));
                return;
            }

            final String validationError = validateSideAliases(endpoint, payload.sideAliasesTag());
            if (!validationError.isBlank()) {
                player.sendSystemMessage(Component.literal(validationError));
                return;
            }

            final boolean changed = endpoint.applyNamingConfiguration(payload.endpointName(), payload.sideAliasesTag());
            if (changed) {
                refreshConnectedComputersForEndpoint(endpoint);
            }
            player.sendSystemMessage(Component.literal("Saved endpoint config for " + endpoint.getEndpointName() + "."));
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to save endpoint naming payload.", exception);
            return null;
        });
    }

    private static void handleExecuteComputerScript(final ExecuteComputerScriptPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ExecutionRequest request = prepareExecution(context, payload);
            if (request == null) {
                return;
            }

            try {
                final PythonExecutionSession session = SERVER_RUNTIME.startSession(request.script(), request.executionContext(), serverExecutionLimits());
                startExecution(request, session);
            } catch (final RuntimeException exception) {
                XLLogicMod.LOGGER.error("Failed to start computer script session on the server thread.", exception);
                sendExecutionFailure(request.player(), request.computerPos());
            }
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to execute computer script from payload.", exception);
            return null;
        });
    }

    private static void handleHeartbeatComputerSession(final HeartbeatComputerSessionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            final ComputerBlockEntity computer = getNearbyComputer(player, payload.computerPos());
            if (computer != null) {
                final ComputerRuntimeStatePayload runtimeStatePayload = synchronizeComputerSession(player, computer);
                if (runtimeStatePayload != null) {
                    PacketDistributor.sendToPlayer(player, runtimeStatePayload);
                }
            } else {
                validateTrackedEditorSessionsForPlayer(player);
                sendUnavailableRuntimeState(player, payload.computerPos(), SESSION_TARGET_UNAVAILABLE_MESSAGE);
            }
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to process computer session heartbeat.", exception);
            return null;
        });
    }

    private static void handleStopComputerScript(final StopComputerScriptPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            final ComputerBlockEntity computer = getNearbyComputer(player, payload.computerPos());
            if (computer == null) {
                validateTrackedEditorSessionsForPlayer(player);
                sendUnavailableRuntimeState(player, payload.computerPos(), SESSION_TARGET_UNAVAILABLE_MESSAGE);
                return;
            }

            if (!ensureEditorLease(player, computer)) {
                rejectExecution(player, computer, editorLeaseRejectedSummary(computer));
                return;
            }

            if (computer.getRuntimeState().running()) {
                computer.stopExecution(EXECUTION_STOPPED_MESSAGE);
            }
            sendRuntimeState(player, computer);
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to stop computer script session.", exception);
            return null;
        });
    }

    private static void handleCloseComputerSession(final CloseComputerSessionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            final ComputerBlockEntity computer = getSessionComputer(player, payload.computerPos());
            if (computer != null) {
                computer.releaseEditor(player);
                synchronizeTrackedEditorSession(computer, editorLeaseTimeoutTicks());
            }
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to close computer editor session.", exception);
            return null;
        });
    }

    private static void handleResumeRecoveryDraft(final ResumeRecoveryDraftPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            PacketDistributor.sendToPlayer(player, resumeRecoveryDraftSession(player, payload.computerPos(), payload.script(), payload.autoStartOnLoad(), payload.forceOverwrite()));
        }).exceptionally(exception -> {
            XLLogicMod.LOGGER.error("Failed to resume recovery draft session.", exception);
            return null;
        });
    }

    private static ExecutionRequest prepareExecution(final IPayloadContext context, final ExecuteComputerScriptPayload payload) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return null;
        }

        final ComputerBlockEntity computer = getNearbyComputer(player, payload.computerPos());
        if (computer == null) {
            validateTrackedEditorSessionsForPlayer(player);
            sendUnavailableRuntimeState(player, payload.computerPos(), SESSION_TARGET_UNAVAILABLE_MESSAGE);
            return null;
        }

        if (!ensureEditorLease(player, computer)) {
            rejectExecution(player, computer, editorLeaseRejectedSummary(computer));
            return null;
        }

        if (computer.getRuntimeState().running()) {
            sendRuntimeState(player, computer);
            return null;
        }

        if (payload.script().length() > XLServerConfig.INSTANCE.maxExecutableScriptLength()) {
            rejectExecution(player, computer, scriptLengthRejectedSummary(payload.script().length()));
            return null;
        }

        final long cooldownRemainingTicks = computer.executionCooldownRemainingTicks(XLServerConfig.INSTANCE.executionCooldownTicks());
        if (cooldownRemainingTicks > 0L) {
            rejectExecution(player, computer, cooldownRejectedSummary(cooldownRemainingTicks));
            return null;
        }

        computer.refreshConnectedEndpoints();
        if (computer.hasNetworkConflict()) {
            rejectExecution(player, computer, computer.networkConflictMessage());
            return null;
        }

        final List<XLNetworkEndpointSnapshot> endpoints = List.copyOf(computer.getReachableEndpoints());
        return new ExecutionRequest(player, player.serverLevel(), payload.computerPos(), payload.script(), createExecutionContext(player.serverLevel(), payload.computerPos(), endpoints));
    }

    private static void startExecution(final ExecutionRequest request, final PythonExecutionSession session) {
        if (!request.level().isLoaded(request.computerPos())) {
            session.close();
            sendExecutionFailure(request.player(), request.computerPos());
            return;
        }

        if (!(request.level().getBlockEntity(request.computerPos()) instanceof ComputerBlockEntity computer)) {
            session.close();
            sendExecutionFailure(request.player(), request.computerPos());
            return;
        }

        computer.refreshConnectedEndpoints();
        computer.beginExecution(request.script(), session);
        sendRuntimeState(request.player(), computer);
    }

    private static void sendExecutionFailure(final ServerPlayer player, final BlockPos computerPos) {
        sendUnavailableRuntimeState(player, computerPos, "Computer is no longer available for execution. Move back into range or reopen after the chunk reloads.");
    }

    private static void rejectExecution(final ServerPlayer player, final ComputerBlockEntity computer, final String summary) {
        computer.replaceRuntimeState(ComputerRuntimeSnapshot.guardrailRejected(computer.getRuntimeState(), summary));
        sendRuntimeState(player, computer);
    }

    private static ComputerBlockEntity getNearbyComputer(final ServerPlayer player, final BlockPos computerPos) {
        if (!player.level().isLoaded(computerPos) || player.blockPosition().distManhattan(computerPos) > MAX_EDIT_DISTANCE) {
            return null;
        }

        if (player.level().getBlockEntity(computerPos) instanceof ComputerBlockEntity computer) {
            return computer;
        }

        return null;
    }

    private static NamedNetworkEndpointBlockEntity getNearbyEndpoint(final ServerPlayer player, final BlockPos endpointPos) {
        if (!player.level().isLoaded(endpointPos) || player.blockPosition().distManhattan(endpointPos) > MAX_EDIT_DISTANCE) {
            return null;
        }

        return player.level().getBlockEntity(endpointPos) instanceof NamedNetworkEndpointBlockEntity endpoint ? endpoint : null;
    }

    private static OpenEndpointNamingPayload createOpenEndpointNamingPayload(final NamedNetworkEndpointBlockEntity endpoint) {
        return new OpenEndpointNamingPayload(
                endpoint.getBlockPos(),
                endpoint.getEndpointName(),
                endpoint.getEndpointType(),
                describeEndpoint(endpoint),
                endpoint.supportsSideNaming(),
                endpoint.getSideAlias(Direction.DOWN),
                endpoint.getSideAlias(Direction.UP),
                endpoint.getSideAlias(Direction.NORTH),
                endpoint.getSideAlias(Direction.SOUTH),
                endpoint.getSideAlias(Direction.WEST),
                endpoint.getSideAlias(Direction.EAST)
        );
    }

    private static String describeEndpoint(final NamedNetworkEndpointBlockEntity endpoint) {
        if (endpoint instanceof MaterialIOBlockEntity materialIo) {
            return materialIo.describeState();
        }
        if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
            redstoneIo.captureInputs();
            return redstoneIo.describeState();
        }
        if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
            return craftingCpu.describeState();
        }
        if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
            return craftingIo.describeState();
        }
        if (endpoint instanceof XLApiBlockEntity xlApi) {
            return xlApi.describeState();
        }
        return "Endpoint: " + endpoint.getEndpointName() + " | type: " + endpoint.getEndpointType();
    }

    private static String validateSideAliases(final NamedNetworkEndpointBlockEntity endpoint, final net.minecraft.nbt.CompoundTag sideAliasesTag) {
        if (!endpoint.supportsSideNaming()) {
            return "";
        }

        final Set<String> canonicalNames = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            canonicalNames.add(direction.getSerializedName());
        }

        final Set<String> usedAliases = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            final String normalized = NamedNetworkEndpointBlockEntity.normalizeCustomName(sideAliasesTag.getString(direction.getSerializedName()));
            if (normalized.isBlank()) {
                continue;
            }
            if (canonicalNames.contains(normalized)) {
                return "Side alias '" + normalized + "' conflicts with a canonical side name.";
            }
            if (!usedAliases.add(normalized)) {
                return "Side alias '" + normalized + "' is used more than once.";
            }
        }
        return "";
    }

    private static void refreshConnectedComputersForEndpoint(final NamedNetworkEndpointBlockEntity endpoint) {
        if (endpoint == null || endpoint.getLevel() == null || endpoint.getLevel().isClientSide()) {
            return;
        }

        for (final BlockPos computerPos : XLNetworkResolver.resolveComputers(endpoint.getLevel(), endpoint.getBlockPos())) {
            if (endpoint.getLevel().getBlockEntity(computerPos) instanceof ComputerBlockEntity computer) {
                computer.refreshConnectedEndpoints();
            }
        }
    }

    private static ComputerBlockEntity getSessionComputer(final ServerPlayer player, final BlockPos computerPos) {
        if (!player.level().isLoaded(computerPos)) {
            return null;
        }

        if (player.level().getBlockEntity(computerPos) instanceof ComputerBlockEntity computer) {
            return computer;
        }

        return null;
    }

    private static boolean ensureEditorLease(final ServerPlayer player, final ComputerBlockEntity computer) {
        if (computer.isEditableBy(player, editorLeaseTimeoutTicks())) {
            computer.heartbeatEditor(player, editorLeaseTimeoutTicks());
            synchronizeTrackedEditorSession(computer, editorLeaseTimeoutTicks());
            return true;
        }
        if (!computer.hasActiveEditor(editorLeaseTimeoutTicks())) {
            final boolean claimed = computer.claimEditor(player, editorLeaseTimeoutTicks());
            synchronizeTrackedEditorSession(computer, editorLeaseTimeoutTicks());
            return claimed;
        }
        synchronizeTrackedEditorSession(computer, editorLeaseTimeoutTicks());
        return false;
    }

    private static void sendRuntimeState(final ServerPlayer player, final ComputerBlockEntity computer) {
        PacketDistributor.sendToPlayer(player, createComputerRuntimeStatePayload(player, computer));
    }

    private static void sendUnavailableRuntimeState(final ServerPlayer player, final BlockPos computerPos, final String sessionMessage) {
        PacketDistributor.sendToPlayer(player, createUnavailableRuntimeStatePayload(computerPos, sessionMessage));
    }

    public static OpenComputerStatePayload createOpenComputerStatePayload(final ServerPlayer player, final ComputerBlockEntity computer, final int leaseTimeoutTicks) {
        final boolean editable = computer.claimEditor(player, leaseTimeoutTicks);
        synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
        return OpenComputerStatePayload.fromComputer(computer, editable, computer.activeEditorName(leaseTimeoutTicks));
    }

    public static ComputerRuntimeStatePayload createComputerRuntimeStatePayload(final ServerPlayer player, final ComputerBlockEntity computer, final int leaseTimeoutTicks) {
        return createComputerRuntimeStatePayload(player, computer, leaseTimeoutTicks, ComputerSessionStatus.ACTIVE, "");
    }

    public static ComputerRuntimeStatePayload createComputerRuntimeStatePayload(final ServerPlayer player, final ComputerBlockEntity computer,
                                                                                final int leaseTimeoutTicks, final ComputerSessionStatus sessionStatus,
                                                                                final String sessionMessage) {
        synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
        return ComputerRuntimeStatePayload.fromComputer(
                computer,
                computer.isEditableBy(player, leaseTimeoutTicks),
                computer.activeEditorName(leaseTimeoutTicks),
                sessionStatus,
                sessionMessage);
    }

    public static ComputerRuntimeStatePayload createUnavailableRuntimeStatePayload(final BlockPos computerPos, final String sessionMessage) {
        final String safeMessage = sessionMessage == null || sessionMessage.isBlank()
            ? SESSION_TARGET_UNAVAILABLE_MESSAGE
                : sessionMessage;
        return new ComputerRuntimeStatePayload(
                computerPos == null ? BlockPos.ZERO : computerPos.immutable(),
                ComputerRuntimeSnapshot.guardrailRejected(ComputerRuntimeSnapshot.idle(), safeMessage),
                List.of(),
                false,
                "",
                ComputerSessionStatus.TARGET_UNAVAILABLE,
                safeMessage
        );
    }

    public static ComputerRuntimeStatePayload synchronizeComputerSession(final ServerPlayer player, final ComputerBlockEntity computer, final int leaseTimeoutTicks) {
        if (computer.isEditableBy(player, leaseTimeoutTicks)) {
            computer.heartbeatEditor(player, leaseTimeoutTicks);
            synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
            return createComputerRuntimeStatePayload(player, computer, leaseTimeoutTicks);
        }

        if (!computer.hasActiveEditor(leaseTimeoutTicks)) {
            computer.claimEditor(player, leaseTimeoutTicks);
        }
        return createComputerRuntimeStatePayload(player, computer, leaseTimeoutTicks);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final BlockPos computerPos, final String draftScript) {
        return resumeRecoveryDraftSession(player, computerPos, draftScript, false, editorLeaseTimeoutTicks(), false);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final BlockPos computerPos, final String draftScript,
                                                                              final boolean autoStartOnLoad, final boolean forceOverwrite) {
        return resumeRecoveryDraftSession(player, computerPos, draftScript, autoStartOnLoad, editorLeaseTimeoutTicks(), forceOverwrite);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final BlockPos computerPos,
                                                                              final String draftScript, final int leaseTimeoutTicks) {
        return resumeRecoveryDraftSession(player, computerPos, draftScript, false, leaseTimeoutTicks, false);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final BlockPos computerPos,
                                                                              final String draftScript, final int leaseTimeoutTicks,
                                                                              final boolean forceOverwrite) {
        return resumeRecoveryDraftSession(player, computerPos, draftScript, false, leaseTimeoutTicks, forceOverwrite);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final BlockPos computerPos,
                                                                              final String draftScript, final boolean autoStartOnLoad,
                                                                              final int leaseTimeoutTicks, final boolean forceOverwrite) {
        final ComputerBlockEntity computer = getNearbyComputer(player, computerPos);
        if (computer == null) {
            validateTrackedEditorSessionsForPlayer(player);
            return ResumeRecoveryDraftResultPayload.targetUnavailable(computerPos, RECOVERY_DRAFT_TARGET_UNAVAILABLE_MESSAGE);
        }
        return resumeRecoveryDraftSession(player, computer, draftScript, autoStartOnLoad, leaseTimeoutTicks, forceOverwrite);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final ComputerBlockEntity computer,
                                                                              final String draftScript, final int leaseTimeoutTicks) {
        return resumeRecoveryDraftSession(player, computer, draftScript, computer != null && computer.autoStartOnLoad(), leaseTimeoutTicks, false);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final ComputerBlockEntity computer,
                                                                              final String draftScript, final int leaseTimeoutTicks,
                                                                              final boolean forceOverwrite) {
        return resumeRecoveryDraftSession(player, computer, draftScript, computer != null && computer.autoStartOnLoad(), leaseTimeoutTicks, forceOverwrite);
    }

    public static ResumeRecoveryDraftResultPayload resumeRecoveryDraftSession(final ServerPlayer player, final ComputerBlockEntity computer,
                                                                              final String draftScript, final boolean autoStartOnLoad,
                                                                              final int leaseTimeoutTicks, final boolean forceOverwrite) {
        if (player == null || computer == null) {
            return ResumeRecoveryDraftResultPayload.targetUnavailable(BlockPos.ZERO, RECOVERY_DRAFT_TARGET_UNAVAILABLE_MESSAGE);
        }

        final boolean editableBeforeClaim = computer.isEditableBy(player, leaseTimeoutTicks);
        if (!editableBeforeClaim && computer.hasActiveEditor(leaseTimeoutTicks)) {
            synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
            return ResumeRecoveryDraftResultPayload.blockedByOtherEditor(
                    computer.getBlockPos(),
                    computer.activeEditorName(leaseTimeoutTicks),
                    recoveryDraftBlockedSummary(computer, leaseTimeoutTicks));
        }

        final String safeDraftScript = draftScript == null ? "" : draftScript;
        final String currentServerScript = computer.getScript();
        if (!forceOverwrite && !safeDraftScript.equals(currentServerScript)) {
            return ResumeRecoveryDraftResultPayload.diverged(computer.getBlockPos(), currentServerScript, RECOVERY_DRAFT_DIVERGED_MESSAGE);
        }

        boolean editable = editableBeforeClaim;
        if (editable) {
            computer.heartbeatEditor(player, leaseTimeoutTicks);
        } else {
            editable = computer.claimEditor(player, leaseTimeoutTicks);
        }

        if (!editable) {
            synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
            return ResumeRecoveryDraftResultPayload.blockedByOtherEditor(
                    computer.getBlockPos(),
                    computer.activeEditorName(leaseTimeoutTicks),
                    recoveryDraftBlockedSummary(computer, leaseTimeoutTicks));
        }

        if (!safeDraftScript.equals(currentServerScript)) {
            computer.setScript(safeDraftScript);
        }
        if (computer.autoStartOnLoad() != autoStartOnLoad) {
            computer.setAutoStartOnLoad(autoStartOnLoad);
        }
        computer.refreshConnectedEndpoints();
        synchronizeTrackedEditorSession(computer, leaseTimeoutTicks);
        return ResumeRecoveryDraftResultPayload.resumedFromComputer(computer, leaseTimeoutTicks, RECOVERY_DRAFT_RESUMED_MESSAGE);
    }

    public static boolean tryAutoStartComputer(final ComputerBlockEntity computer) {
        if (computer == null || !(computer.getLevel() instanceof ServerLevel level) || level.isClientSide()) {
            return false;
        }

        if (computer.getRuntimeState().running()) {
            return true;
        }

        final String script = computer.getScript();
        if (script == null || script.isBlank() || !level.isLoaded(computer.getBlockPos())) {
            return false;
        }

        if (script.length() > XLServerConfig.INSTANCE.maxExecutableScriptLength()) {
            computer.replaceRuntimeState(ComputerRuntimeSnapshot.guardrailRejected(computer.getRuntimeState(),
                    scriptLengthRejectedSummary(script.length())));
            return false;
        }

        computer.refreshConnectedEndpoints();
        if (computer.hasNetworkConflict()) {
            computer.replaceRuntimeState(ComputerRuntimeSnapshot.guardrailRejected(computer.getRuntimeState(), computer.networkConflictMessage()));
            return false;
        }

        final List<XLNetworkEndpointSnapshot> endpoints = List.copyOf(computer.getReachableEndpoints());
        try {
            final PythonExecutionSession session = SERVER_RUNTIME.startSession(script, createExecutionContext(level, computer.getBlockPos(), endpoints), serverExecutionLimits());
            computer.beginExecution(script, session);
            return true;
        } catch (final RuntimeException exception) {
            XLLogicMod.LOGGER.error("Failed to auto-start persisted computer script.", exception);
            computer.replaceRuntimeState(ComputerRuntimeSnapshot.guardrailRejected(computer.getRuntimeState(), AUTO_START_FAILURE_SUMMARY));
            return false;
        }
    }

    private static PythonExecutionContext createExecutionContext(final ServerLevel level, final BlockPos computerPos, final List<XLNetworkEndpointSnapshot> endpoints) {
        return PythonExecutionContext.forServerExecution(level, "computer_" + computerPos.toShortString(), computerPos, endpoints);
    }

    private static PythonExecutionLimits serverExecutionLimits() {
        final long statementBudget = XLServerConfig.INSTANCE.serverStatementBudget();
        return PythonExecutionLimits.statementBudget(
                statementBudget,
                "Server execution stopped after " + statementBudget + " Python statements to protect the tick loop.");
    }

    private static String scriptLengthRejectedSummary(final int actualScriptLength) {
        final int maxScriptLength = XLServerConfig.INSTANCE.maxExecutableScriptLength();
        return "Script exceeds the server execution limit of " + maxScriptLength + " chars (received " + actualScriptLength + ").";
    }

    private static String cooldownRejectedSummary(final long remainingTicks) {
        final double remainingSeconds = remainingTicks / 20.0D;
        final double roundedSeconds = Math.round(remainingSeconds * 10.0D) / 10.0D;
        final String secondsText = roundedSeconds == Math.rint(roundedSeconds)
                ? Long.toString((long) roundedSeconds)
                : String.format(Locale.ROOT, "%.1f", roundedSeconds);
        return "Computer execution is cooling down. Try again in " + remainingTicks + " tick"
                + (remainingTicks == 1 ? "" : "s")
                + " (" + secondsText + "s).";
    }

    private static String editorLeaseRejectedSummary(final ComputerBlockEntity computer) {
        final String activeEditorName = computer.activeEditorName(editorLeaseTimeoutTicks());
        if (activeEditorName.isBlank()) {
            return "Computer editor lock is no longer available. Reopen the screen to claim editing.";
        }
        return "Computer is currently being edited by " + activeEditorName + ". This screen is read-only until the editor lock is released.";
    }

    private static String recoveryDraftBlockedSummary(final ComputerBlockEntity computer, final int leaseTimeoutTicks) {
        final String activeEditorName = computer.activeEditorName(leaseTimeoutTicks);
        if (activeEditorName.isBlank()) {
            return "Recovery draft cannot resume yet because the editor lock is not available.";
        }
        return "Recovery draft is waiting because " + activeEditorName + " currently holds the editor lock.";
    }

    private static int editorLeaseTimeoutTicks() {
        return XLServerConfig.INSTANCE.editorLeaseTimeoutTicks();
    }

    private static void handlePlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseEditorSessionsForDimensionChange(player);
        }
    }

    private static void handlePlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseEditorSessionsForPlayer(player);
        }
    }

    private static void handlePlayerTickPost(final PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            validateTrackedEditorSessionsForPlayer(player);
        }
    }

    private static void handleChunkUnload(final ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            releaseEditorSessionsForChunkUnload(chunk);
        }
    }

    private static void handleServerStarting(final ServerStartingEvent event) {
        resetTrackedEditorSessions();
    }

    private static void handleServerStopped(final ServerStoppedEvent event) {
        resetTrackedEditorSessions();
    }

    private static void synchronizeTrackedEditorSession(final ComputerBlockEntity computer, final int leaseTimeoutTicks) {
        if (computer == null || !(computer.getLevel() instanceof ServerLevel level)) {
            return;
        }

        final ComputerSessionKey sessionKey = new ComputerSessionKey(level.dimension(), computer.getBlockPos());
        final UUID activeEditorId = computer.activeEditorId(leaseTimeoutTicks);
        final UUID previousEditorId = TRACKED_EDITOR_SESSIONS.remove(sessionKey);
        if (previousEditorId != null) {
            removeTrackedEditorSession(previousEditorId, sessionKey);
        }

        if (activeEditorId != null) {
            TRACKED_EDITOR_SESSIONS.put(sessionKey, activeEditorId);
            TRACKED_EDITOR_SESSIONS_BY_PLAYER.computeIfAbsent(activeEditorId, ignored -> new HashSet<>()).add(sessionKey);
        }
    }

    private static void removeTrackedEditorSession(final UUID playerId, final ComputerSessionKey sessionKey) {
        final Set<ComputerSessionKey> sessions = TRACKED_EDITOR_SESSIONS_BY_PLAYER.get(playerId);
        if (sessions == null) {
            return;
        }

        sessions.remove(sessionKey);
        if (sessions.isEmpty()) {
            TRACKED_EDITOR_SESSIONS_BY_PLAYER.remove(playerId);
        }
    }

    private record ExecutionRequest(ServerPlayer player, ServerLevel level, BlockPos computerPos, String script, PythonExecutionContext executionContext) {
    }

    private record ComputerSessionKey(ResourceKey<Level> dimension, BlockPos position) {
        private ComputerSessionKey {
            position = position.immutable();
        }
    }

    private static final class ClientHooks {
        private ClientHooks() {
        }

        private static void openComputerScreen(final OpenComputerStatePayload payload) {
            de.xllogic.client.XLLogicClient.openComputerScreen(payload.computerPos(), payload.script(), payload.runtimeState(), payload.executionContext(), payload.editable(), payload.activeEditorName(), payload.autoStartOnLoad());
        }

        private static void openEndpointNamingScreen(final OpenEndpointNamingPayload payload) {
            de.xllogic.client.XLLogicClient.openEndpointNamingScreen(payload);
        }

        private static void updateComputerRuntime(final ComputerRuntimeStatePayload payload) {
            de.xllogic.client.XLLogicClient.updateComputerRuntime(payload.computerPos(), payload.runtimeState(), payload.executionContext(), payload.editable(),
                    payload.activeEditorName(), payload.sessionStatus(), payload.sessionMessage());
        }

        private static void applyRecoveryDraftResumeResult(final ResumeRecoveryDraftResultPayload payload) {
            de.xllogic.client.XLLogicClient.applyRecoveryDraftResumeResult(payload);
        }
    }
}
