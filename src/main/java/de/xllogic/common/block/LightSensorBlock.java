package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.LightSensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LightSensorBlock extends AbstractDeviceBlock {
    public static final MapCodec<LightSensorBlock> CODEC = simpleCodec(LightSensorBlock::new);

    public LightSensorBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new LightSensorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            final String endpointName = blockEntity instanceof LightSensorBlockEntity sensor ? sensor.getEndpointName() : "light_sensor";
            player.sendSystemMessage(Component.literal("Endpoint: " + endpointName + " | Light level: " + sample(level, pos)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        return sample(level, pos);
    }

    private static int sample(final Level level, final BlockPos pos) {
        return Mth.clamp(level.getMaxLocalRawBrightness(pos), 0, 15);
    }
}
