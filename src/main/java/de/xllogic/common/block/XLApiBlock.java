package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class XLApiBlock extends AbstractDeviceBlock {
    public static final MapCodec<XLApiBlock> CODEC = simpleCodec(XLApiBlock::new);

    public XLApiBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new XLApiBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof XLApiBlockEntity xlapi)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                xlapi.cycleUplinkGroup();
                player.sendSystemMessage(Component.literal("XLAPI uplink group: " + xlapi.getUplinkGroup()));
            } else {
                xlapi.toggleRelayEnabled();
                player.sendSystemMessage(Component.literal(xlapi.describeState()));
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
