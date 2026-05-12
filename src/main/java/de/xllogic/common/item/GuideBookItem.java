package de.xllogic.common.item;

import de.xllogic.XLLogicMod;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class GuideBookItem extends Item {
    public GuideBookItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            this.openGuideBookClient();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltipComponents,
                                final TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.xllogic.guide_book.tooltip").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.xllogic.guide_book.tooltip_controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void openGuideBookClient() {
        try {
            final Class<?> clientClass = Class.forName("de.xllogic.client.XLLogicClient");
            clientClass.getMethod("openGuideBookScreen").invoke(null);
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            XLLogicMod.LOGGER.error("Failed to open the XL Logic guide book screen.", exception);
        }
    }
}