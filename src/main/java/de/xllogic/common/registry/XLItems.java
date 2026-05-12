package de.xllogic.common.registry;

import de.xllogic.common.item.GuideBookItem;
import java.util.function.Supplier;
import de.xllogic.XLLogicMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class XLItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(XLLogicMod.MOD_ID);
    public static final Supplier<Item> GUIDE_BOOK = ITEMS.register("guide_book", () -> new GuideBookItem(new Item.Properties().stacksTo(1)));

    static {
        ITEMS.registerSimpleBlockItem(XLBlocks.COMPUTER, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.SCREEN, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.NETWORK_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.XLAPI_BLOCK, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.REDSTONE_IO, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.REDSTONE_BUS_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.WHITE_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.ORANGE_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.MAGENTA_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.LIGHT_BLUE_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.YELLOW_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.LIME_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.PINK_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.GRAY_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.LIGHT_GRAY_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.CYAN_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.PURPLE_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.BLUE_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.BROWN_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.GREEN_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.COLORED_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.BLACK_REDSTONE_CABLE, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.LIGHT_SENSOR, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.CLOCK, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.RAIN_SENSOR, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.MATERIAL_IO, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.CRAFTING_IO, new Item.Properties());
        ITEMS.registerSimpleBlockItem(XLBlocks.CRAFTING_CPU, new Item.Properties());
    }

    private XLItems() {
    }
}
