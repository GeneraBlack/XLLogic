package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.blockentity.MaterialIOBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

public final class MaterialIOBlock extends AbstractDeviceBlock {
    public static final MapCodec<MaterialIOBlock> CODEC = simpleCodec(MaterialIOBlock::new);

    public MaterialIOBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MaterialIOBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof MaterialIOBlockEntity materialIo)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                materialIo.cycleMode();
                player.sendSystemMessage(Component.literal(materialIo.describeState()));
            } else if (player instanceof ServerPlayer serverPlayer) {
                XLNetworking.openEndpointNamingScreen(serverPlayer, materialIo);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
