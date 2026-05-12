package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.network.XLRedstoneBusResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ColoredRedstoneCableBlock extends RedstoneBusCableBlock {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 15);
    public static final int DEFAULT_CHANNEL = 14;

    private final int fixedChannel;
    private final MapCodec<ColoredRedstoneCableBlock> codec;

    public ColoredRedstoneCableBlock(final Properties properties, final int fixedChannel) {
        super(properties);
        this.fixedChannel = Mth.clamp(fixedChannel, 0, 15);
        this.codec = simpleCodec(codecProperties -> new ColoredRedstoneCableBlock(codecProperties, this.fixedChannel));
        this.registerDefaultState(this.defaultBlockState().setValue(CHANNEL, this.fixedChannel));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return this.codec;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        final int currentChannel = state.getValue(CHANNEL);
        final XLRedstoneBusResolver.ChannelFlowDebugSnapshot flow = XLRedstoneBusResolver.inspectChannelFlow(level, pos, currentChannel);
        player.sendSystemMessage(Component.literal("Filtered cable channel: " + currentChannel));
        player.sendSystemMessage(Component.literal(" - " + flow.summaryLine()));
        if (player.isShiftKeyDown()) {
            for (final XLRedstoneBusResolver.RouteHopDebugSnapshot hop : flow.routeHops()) {
                player.sendSystemMessage(Component.literal("   - " + hop.summaryLine()));
            }
            if (flow.routeHopsTruncated()) {
                player.sendSystemMessage(Component.literal("   - weitere Routenhops ausgeblendet"));
            }
            for (final XLRedstoneBusResolver.RouteBlockerDebugSnapshot blocker : flow.blockers()) {
                player.sendSystemMessage(Component.literal("   - " + blocker.summaryLine()));
            }
            if (flow.blockersTruncated()) {
                player.sendSystemMessage(Component.literal("   - weitere Blocker ausgeblendet"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canConnectTo(final BlockState state, final BlockGetter level, final BlockPos neighborPos, final BlockState neighborState, final Direction direction) {
        return XLRedstoneBusResolver.canCableConnectTo(state, neighborState);
    }

    public static int colorForChannel(final int channel) {
        return switch (Mth.clamp(channel, 0, 15)) {
            case 0 -> 0xF9FFFE;
            case 1 -> 0xF9801D;
            case 2 -> 0xC74EBD;
            case 3 -> 0x3AB3DA;
            case 4 -> 0xFED83D;
            case 5 -> 0x80C71F;
            case 6 -> 0xF38BAA;
            case 7 -> 0x474F52;
            case 8 -> 0x9D9D97;
            case 9 -> 0x169C9C;
            case 10 -> 0x8932B8;
            case 11 -> 0x3C44AA;
            case 12 -> 0x835432;
            case 13 -> 0x5E7C16;
            case 14 -> 0xB02E26;
            case 15 -> 0x1D1D21;
            default -> 0xB02E26;
        };
    }

    public int fixedChannel() {
        return this.fixedChannel;
    }
}