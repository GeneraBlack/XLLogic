package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.ClockBlockEntity;
import de.xllogic.common.device.ClockSourceMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ClockBlock extends AbstractDeviceBlock {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    public static final MapCodec<ClockBlock> CODEC = simpleCodec(ClockBlock::new);

    public ClockBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ClockBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            final long gameTime = level.getDayTime() % 24000L;
            final String realTime = FORMATTER.format(Instant.now());
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            final String endpointName = blockEntity instanceof ClockBlockEntity clock ? clock.getEndpointName() : "clock";
            player.sendSystemMessage(Component.literal("Endpoint: " + endpointName + " | Game time: " + gameTime + " | Real time: " + realTime + " | Mode hint: " + ClockSourceMode.BOTH));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        return (int) ((level.getDayTime() % 24000L) / 1500L);
    }
}
