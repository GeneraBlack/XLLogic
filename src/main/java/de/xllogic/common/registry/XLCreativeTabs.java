package de.xllogic.common.registry;

import de.xllogic.XLLogicMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class XLCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, XLLogicMod.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.xllogic.main"))
            .icon(() -> new ItemStack(XLBlocks.COMPUTER.get()))
            .displayItems((parameters, output) -> {
                output.accept(XLItems.GUIDE_BOOK.get());
                output.accept(XLBlocks.COMPUTER.get());
                output.accept(XLBlocks.SCREEN.get());
                output.accept(XLBlocks.NETWORK_CABLE.get());
                output.accept(XLBlocks.XLAPI_BLOCK.get());
                output.accept(XLBlocks.REDSTONE_IO.get());
                output.accept(XLBlocks.REDSTONE_BUS_CABLE.get());
                output.accept(XLBlocks.WHITE_REDSTONE_CABLE.get());
                output.accept(XLBlocks.ORANGE_REDSTONE_CABLE.get());
                output.accept(XLBlocks.MAGENTA_REDSTONE_CABLE.get());
                output.accept(XLBlocks.LIGHT_BLUE_REDSTONE_CABLE.get());
                output.accept(XLBlocks.YELLOW_REDSTONE_CABLE.get());
                output.accept(XLBlocks.LIME_REDSTONE_CABLE.get());
                output.accept(XLBlocks.PINK_REDSTONE_CABLE.get());
                output.accept(XLBlocks.GRAY_REDSTONE_CABLE.get());
                output.accept(XLBlocks.LIGHT_GRAY_REDSTONE_CABLE.get());
                output.accept(XLBlocks.CYAN_REDSTONE_CABLE.get());
                output.accept(XLBlocks.PURPLE_REDSTONE_CABLE.get());
                output.accept(XLBlocks.BLUE_REDSTONE_CABLE.get());
                output.accept(XLBlocks.BROWN_REDSTONE_CABLE.get());
                output.accept(XLBlocks.GREEN_REDSTONE_CABLE.get());
                output.accept(XLBlocks.COLORED_REDSTONE_CABLE.get());
                output.accept(XLBlocks.BLACK_REDSTONE_CABLE.get());
                output.accept(XLBlocks.LIGHT_SENSOR.get());
                output.accept(XLBlocks.CLOCK.get());
                output.accept(XLBlocks.RAIN_SENSOR.get());
                output.accept(XLBlocks.MATERIAL_IO.get());
                output.accept(XLBlocks.CRAFTING_IO.get());
                output.accept(XLBlocks.CRAFTING_CPU.get());
            })
            .build());

    private XLCreativeTabs() {
    }
}
