package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.network.XLNetworkEndpointSnapshot;
import de.xllogic.common.network.XLNetworkResolver;
import de.xllogic.common.registry.XLBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ComputerBlock extends AbstractDeviceBlock {
    private static final String DISABLED_LABEL = "disabled";

    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);

    public ComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }

        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (blockEntity instanceof ComputerBlockEntity computer) {
                computer.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        computer.refreshConnectedEndpoints();
        if (player.isShiftKeyDown()) {
            this.handleDiscoveryMode(player, pos, computer);
            return InteractionResult.SUCCESS;
        }

        this.openComputer(player, computer);
        return InteractionResult.SUCCESS;
    }

    private void handleDiscoveryMode(final Player player, final BlockPos computerPos, final ComputerBlockEntity computer) {
        final XLNetworkResolver.LocalSegmentDebugSnapshot snapshot = XLNetworkResolver.inspectLocalSegment(computer.getLevel(), computerPos);
        final int cooldownTicks = XLServerConfig.INSTANCE.executionCooldownTicks();
        final long cooldownRemainingTicks = computer.executionCooldownRemainingTicks(cooldownTicks);
        final int editorLeaseTimeoutTicks = XLServerConfig.INSTANCE.editorLeaseTimeoutTicks();
        final int persistentResumeIntervalTicks = XLServerConfig.INSTANCE.persistentResumeIntervalTicks();
        player.sendSystemMessage(Component.literal("Discovery refreshed for computer at " + computerPos.toShortString()));
        player.sendSystemMessage(Component.literal("Computer script length: " + computer.getScript().length() + " chars | " + computer.describeNetworkSummary()));
        player.sendSystemMessage(Component.literal(" - execution model: cooperative tick runtime; yielded slices resume every "
            + persistentResumeIntervalTicks
            + " ticks by default; use 'yield from sleep_ticks(1)' or 'yield from run_loop(step, 1)' in loops"));
        player.sendSystemMessage(Component.literal(" - runtime guardrails: statement budget "
            + describeStatementBudget(XLServerConfig.INSTANCE.serverStatementBudget())
            + " | cooldown " + cooldownTicks + " ticks"
            + (cooldownRemainingTicks > 0 ? " (cooling down: " + cooldownRemainingTicks + " left)" : " (ready)")
            + " | max executable script " + XLServerConfig.INSTANCE.maxExecutableScriptLength() + " chars"));
        player.sendSystemMessage(Component.literal(" - hard limits: slice watchdog " + describeMillisLimit(XLServerConfig.INSTANCE.maxCpuTimeMillis())
            + " | stdout " + describeByteLimit(XLServerConfig.INSTANCE.maxStdoutBytes())
            + " | stderr " + describeByteLimit(XLServerConfig.INSTANCE.maxStderrBytes())));
        player.sendSystemMessage(Component.literal(" - editor lock: "
            + (computer.hasActiveEditor(editorLeaseTimeoutTicks) ? computer.activeEditorName(editorLeaseTimeoutTicks) : "free")
            + " | lease timeout " + editorLeaseTimeoutTicks + " ticks"));
        player.sendSystemMessage(Component.literal(" - cables on segment: " + snapshot.cableCount()
                + " | xlapi boundaries: " + snapshot.xlapiBoundaryCount()
                + " | unloaded frontiers: " + snapshot.unloadedBoundaryCount()
                + " | endpoint types: " + snapshot.endpointTypeSummary()));
        this.sendComputerConflictSummary(player, computer);
        this.sendSegmentBoundarySummary(player, snapshot);
        this.sendLinkedScreenSummary(player, computer);
        this.sendEndpointSummary(player, computer);
    }

    private void sendComputerConflictSummary(final Player player, final ComputerBlockEntity computer) {
        if (!computer.hasNetworkConflict()) {
            return;
        }

        player.sendSystemMessage(Component.literal(" - network conflict: multiple computers share this cable segment"));
        for (final BlockPos computerPos : computer.getDiscoveredComputerPositions()) {
            player.sendSystemMessage(Component.literal(" - computer @ " + computerPos.toShortString()));
        }
        player.sendSystemMessage(Component.literal(" - fix: separate computer segments with XLAPI blocks"));
    }

    private void sendLinkedScreenSummary(final Player player, final ComputerBlockEntity computer) {
        if (computer.getLinkedScreenPositions().isEmpty()) {
            player.sendSystemMessage(Component.literal(" - discovered screens: none"));
            return;
        }

        for (final BlockPos screenPos : computer.getLinkedScreenPositions()) {
            player.sendSystemMessage(Component.literal(" - discovered screen @ " + screenPos.toShortString()));
        }
    }

    private void sendEndpointSummary(final Player player, final ComputerBlockEntity computer) {
        if (computer.getConnectedEndpoints().isEmpty() && computer.getBridgedEndpoints().isEmpty()) {
            player.sendSystemMessage(Component.literal(" - discovered endpoints: none"));
            return;
        }

        for (final XLNetworkEndpointSnapshot endpoint : computer.getConnectedEndpoints()) {
            player.sendSystemMessage(Component.literal(" - local: " + endpoint.summary()));
        }
        for (final XLNetworkEndpointSnapshot endpoint : computer.getBridgedEndpoints()) {
            player.sendSystemMessage(Component.literal(" - bridged: " + endpoint.summary()));
        }
    }

    private void sendSegmentBoundarySummary(final Player player, final XLNetworkResolver.LocalSegmentDebugSnapshot snapshot) {
        if (snapshot.boundaries().isEmpty()) {
            return;
        }

        for (final XLNetworkResolver.SegmentBoundaryDebugSnapshot boundary : snapshot.boundaries()) {
            player.sendSystemMessage(Component.literal(" - " + boundary.summaryLine()));
        }
        if (snapshot.boundariesTruncated()) {
            player.sendSystemMessage(Component.literal(" - more discovery boundaries omitted"));
        }
    }

    private void openComputer(final Player player, final ComputerBlockEntity computer) {
        if (player instanceof ServerPlayer serverPlayer) {
            XLNetworking.openComputerScreen(serverPlayer, computer);
        }
    }

    private static String describeMillisLimit(final int value) {
        return value <= 0 ? DISABLED_LABEL : value + " ms";
    }

    private static String describeStatementBudget(final long value) {
        return value <= 0L ? DISABLED_LABEL : Long.toString(value);
    }

    private static String describeByteLimit(final long value) {
        return value <= 0L ? DISABLED_LABEL : value + " B";
    }
}
