package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.RainSensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RainSensorBlock extends AbstractDeviceBlock {
    public static final MapCodec<RainSensorBlock> CODEC = simpleCodec(RainSensorBlock::new);

    public RainSensorBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new RainSensorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            final String endpointName = blockEntity instanceof RainSensorBlockEntity rainSensor ? rainSensor.getEndpointName() : "rain_sensor";
            player.sendSystemMessage(Component.literal("Endpoint: " + endpointName + " | Raining: " + isRaining(level, pos)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        return isRaining(level, pos) ? 15 : 0;
    }

    private static boolean isRaining(final Level level, final BlockPos pos) {
        return level.isRaining() && level.canSeeSky(pos.above());
    }
}
