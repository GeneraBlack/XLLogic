package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.network.XLNetworkResolver;
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

public final class NetworkCableBlock extends AbstractCableBlock {
    public static final MapCodec<NetworkCableBlock> CODEC = simpleCodec(NetworkCableBlock::new);

    public NetworkCableBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean canConnectTo(final BlockState state, final BlockGetter level, final BlockPos neighborPos, final BlockState neighborState, final Direction direction) {
        final Block neighborBlock = neighborState.getBlock();
        return neighborBlock == XLBlocks.NETWORK_CABLE.get() || neighborBlock instanceof AbstractDeviceBlock;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        final XLNetworkResolver.LocalSegmentDebugSnapshot snapshot = XLNetworkResolver.inspectLocalSegment(level, pos);
        player.sendSystemMessage(Component.literal("Discovery segment @ " + pos.toShortString() + " | cables: " + snapshot.cableCount() + " | computers: " + snapshot.computerPositions().size() + " | endpoints: " + snapshot.endpoints().size()));
        player.sendSystemMessage(Component.literal(" - xlapi boundaries: " + snapshot.xlapiBoundaryCount() + " | unloaded frontiers: " + snapshot.unloadedBoundaryCount() + " | endpoint types: " + snapshot.endpointTypeSummary()));
        if (snapshot.hasComputerConflict()) {
            player.sendSystemMessage(Component.literal(" - warning: multiple computers share this discovery segment"));
        }

        if (player.isShiftKeyDown()) {
            this.sendDetailedDiscovery(player, snapshot);
        }
        return InteractionResult.SUCCESS;
    }

    private void sendDetailedDiscovery(final Player player, final XLNetworkResolver.LocalSegmentDebugSnapshot snapshot) {
        if (snapshot.computerPositions().isEmpty()) {
            player.sendSystemMessage(Component.literal(" - computers: none"));
        } else {
            for (final BlockPos computerPos : snapshot.computerPositions()) {
                player.sendSystemMessage(Component.literal(" - computer @ " + computerPos.toShortString()));
            }
        }

        for (final XLNetworkResolver.SegmentBoundaryDebugSnapshot boundary : snapshot.boundaries()) {
            player.sendSystemMessage(Component.literal(" - " + boundary.summaryLine()));
        }
        if (snapshot.boundariesTruncated()) {
            player.sendSystemMessage(Component.literal(" - more discovery boundaries omitted"));
        }

        if (snapshot.endpoints().isEmpty()) {
            player.sendSystemMessage(Component.literal(" - endpoints: none"));
            return;
        }

        for (final de.xllogic.common.network.XLNetworkEndpointSnapshot endpoint : snapshot.endpoints()) {
            player.sendSystemMessage(Component.literal(" - endpoint: " + endpoint.summary()));
        }
    }
}