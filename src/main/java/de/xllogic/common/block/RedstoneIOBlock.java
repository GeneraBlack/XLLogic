package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.network.XLNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RedstoneIOBlock extends AbstractDeviceBlock {
    public static final MapCodec<RedstoneIOBlock> CODEC = simpleCodec(RedstoneIOBlock::new);

    public RedstoneIOBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new RedstoneIOBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof RedstoneIOBlockEntity redstoneIo)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                redstoneIo.cycleMode();
                player.sendSystemMessage(Component.literal("Redstone I/O mode: " + redstoneIo.getMode()));
            } else {
                redstoneIo.captureInputs();
                if (player instanceof ServerPlayer serverPlayer) {
                    XLNetworking.openEndpointNamingScreen(serverPlayer, redstoneIo);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final net.minecraft.world.level.block.Block block, final BlockPos fromPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RedstoneIOBlockEntity redstoneIo) {
            redstoneIo.captureInputs();
        }
    }

    @Override
    public boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    public int getSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction side) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneIOBlockEntity redstoneIo) {
            return redstoneIo.getSignal(side);
        }
        return 0;
    }

    @Override
    public int getDirectSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction side) {
        return this.getSignal(state, level, pos, side);
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneIOBlockEntity redstoneIo) {
            return redstoneIo.getMaxSignalLevel();
        }
        return 0;
    }
}
