package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.network.XLRedstoneBusResolver;
import de.xllogic.common.registry.XLBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RedstoneBusCableBlock extends AbstractCableBlock {
    public static final MapCodec<RedstoneBusCableBlock> CODEC = simpleCodec(RedstoneBusCableBlock::new);

    public RedstoneBusCableBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean canConnectTo(final BlockState state, final BlockGetter level, final BlockPos neighborPos, final BlockState neighborState, final Direction direction) {
        return XLRedstoneBusResolver.canCableConnectTo(state, neighborState);
    }

    @Override
    protected void handleTopologyChange(final Level level, final BlockPos pos, final BlockState state) {
        XLRedstoneBusResolver.notifyAdjacentNetworks(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        final XLRedstoneBusResolver.BusNetworkDebugSnapshot snapshot = XLRedstoneBusResolver.inspectBus(level, pos);
        player.sendSystemMessage(Component.literal(snapshot.summaryLine()));
        for (final XLRedstoneBusResolver.ChannelFlowDebugSnapshot channel : snapshot.channelFlows()) {
            player.sendSystemMessage(Component.literal(" - " + channel.summaryLine()));
        }

        if (player.isShiftKeyDown()) {
            this.sendDetailedBusState(level, player, snapshot);
        }
        return InteractionResult.SUCCESS;
    }

    protected void sendDetailedBusState(final Level level, final Player player, final XLRedstoneBusResolver.BusNetworkDebugSnapshot snapshot) {
        for (final XLRedstoneBusResolver.ChannelFlowDebugSnapshot channel : snapshot.channelFlows()) {
            player.sendSystemMessage(Component.literal(" - flow: " + channel.summaryLine()));
            for (final XLRedstoneBusResolver.RouteHopDebugSnapshot hop : channel.routeHops()) {
                player.sendSystemMessage(Component.literal("   - " + hop.summaryLine()));
            }
            if (channel.routeHopsTruncated()) {
                player.sendSystemMessage(Component.literal("   - weitere Routenhops ausgeblendet"));
            }
            for (final XLRedstoneBusResolver.RouteBlockerDebugSnapshot blocker : channel.blockers()) {
                player.sendSystemMessage(Component.literal("   - " + blocker.summaryLine()));
            }
            if (channel.blockersTruncated()) {
                player.sendSystemMessage(Component.literal("   - weitere Blocker ausgeblendet"));
            }
        }

        if (snapshot.redstoneIoPositions().isEmpty()) {
            player.sendSystemMessage(Component.literal(" - redstone I/O: none"));
            return;
        }

        for (final BlockPos devicePos : snapshot.redstoneIoPositions()) {
            if (level.getBlockEntity(devicePos) instanceof RedstoneIOBlockEntity redstoneIo) {
                player.sendSystemMessage(Component.literal(" - device: " + redstoneIo.describeState() + " @ " + devicePos.toShortString()));
            }
        }
    }
}