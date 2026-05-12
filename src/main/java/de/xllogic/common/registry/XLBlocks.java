package de.xllogic.common.registry;

import de.xllogic.XLLogicMod;
import de.xllogic.common.block.ClockBlock;
import de.xllogic.common.block.ColoredRedstoneCableBlock;
import de.xllogic.common.block.ComputerBlock;
import de.xllogic.common.block.CraftingCPUBlock;
import de.xllogic.common.block.CraftingIOBlock;
import de.xllogic.common.block.LightSensorBlock;
import de.xllogic.common.block.MaterialIOBlock;
import de.xllogic.common.block.NetworkCableBlock;
import de.xllogic.common.block.RainSensorBlock;
import de.xllogic.common.block.RedstoneIOBlock;
import de.xllogic.common.block.RedstoneBusCableBlock;
import de.xllogic.common.block.ScreenBlock;
import de.xllogic.common.block.XLApiBlock;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class XLBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(XLLogicMod.MOD_ID);

    public static final DeferredBlock<ComputerBlock> COMPUTER = BLOCKS.registerBlock("computer", ComputerBlock::new, metalDevice(MapColor.COLOR_BLACK));
    public static final DeferredBlock<ScreenBlock> SCREEN = BLOCKS.registerBlock("screen", ScreenBlock::new, glassDevice(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<NetworkCableBlock> NETWORK_CABLE = BLOCKS.registerBlock("network_cable", NetworkCableBlock::new, cable(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<XLApiBlock> XLAPI_BLOCK = BLOCKS.registerBlock("xlapi_block", XLApiBlock::new, metalDevice(MapColor.COLOR_CYAN));
    public static final DeferredBlock<RedstoneIOBlock> REDSTONE_IO = BLOCKS.registerBlock("redstone_io", RedstoneIOBlock::new, metalDevice(MapColor.COLOR_RED));
    public static final DeferredBlock<RedstoneBusCableBlock> REDSTONE_BUS_CABLE = BLOCKS.registerBlock("redstone_bus_cable", RedstoneBusCableBlock::new, cable(MapColor.COLOR_BLACK));
    public static final DeferredBlock<ColoredRedstoneCableBlock> WHITE_REDSTONE_CABLE = BLOCKS.registerBlock("white_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 0), cable(MapColor.SNOW));
    public static final DeferredBlock<ColoredRedstoneCableBlock> ORANGE_REDSTONE_CABLE = BLOCKS.registerBlock("orange_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 1), cable(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<ColoredRedstoneCableBlock> MAGENTA_REDSTONE_CABLE = BLOCKS.registerBlock("magenta_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 2), cable(MapColor.COLOR_MAGENTA));
    public static final DeferredBlock<ColoredRedstoneCableBlock> LIGHT_BLUE_REDSTONE_CABLE = BLOCKS.registerBlock("light_blue_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 3), cable(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<ColoredRedstoneCableBlock> YELLOW_REDSTONE_CABLE = BLOCKS.registerBlock("yellow_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 4), cable(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<ColoredRedstoneCableBlock> LIME_REDSTONE_CABLE = BLOCKS.registerBlock("lime_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 5), cable(MapColor.COLOR_LIGHT_GREEN));
    public static final DeferredBlock<ColoredRedstoneCableBlock> PINK_REDSTONE_CABLE = BLOCKS.registerBlock("pink_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 6), cable(MapColor.COLOR_PINK));
    public static final DeferredBlock<ColoredRedstoneCableBlock> GRAY_REDSTONE_CABLE = BLOCKS.registerBlock("gray_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 7), cable(MapColor.COLOR_GRAY));
    public static final DeferredBlock<ColoredRedstoneCableBlock> LIGHT_GRAY_REDSTONE_CABLE = BLOCKS.registerBlock("light_gray_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 8), cable(MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<ColoredRedstoneCableBlock> CYAN_REDSTONE_CABLE = BLOCKS.registerBlock("cyan_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 9), cable(MapColor.COLOR_CYAN));
    public static final DeferredBlock<ColoredRedstoneCableBlock> PURPLE_REDSTONE_CABLE = BLOCKS.registerBlock("purple_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 10), cable(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<ColoredRedstoneCableBlock> BLUE_REDSTONE_CABLE = BLOCKS.registerBlock("blue_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 11), cable(MapColor.COLOR_BLUE));
    public static final DeferredBlock<ColoredRedstoneCableBlock> BROWN_REDSTONE_CABLE = BLOCKS.registerBlock("brown_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 12), cable(MapColor.COLOR_BROWN));
    public static final DeferredBlock<ColoredRedstoneCableBlock> GREEN_REDSTONE_CABLE = BLOCKS.registerBlock("green_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 13), cable(MapColor.COLOR_GREEN));
    public static final DeferredBlock<ColoredRedstoneCableBlock> COLORED_REDSTONE_CABLE = BLOCKS.registerBlock("colored_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 14), cable(MapColor.COLOR_RED));
    public static final DeferredBlock<ColoredRedstoneCableBlock> BLACK_REDSTONE_CABLE = BLOCKS.registerBlock("black_redstone_cable", properties -> new ColoredRedstoneCableBlock(properties, 15), cable(MapColor.COLOR_BLACK));
    public static final DeferredBlock<LightSensorBlock> LIGHT_SENSOR = BLOCKS.registerBlock("light_sensor", LightSensorBlock::new, sensor(MapColor.GLOW_LICHEN));
    public static final DeferredBlock<ClockBlock> CLOCK = BLOCKS.registerBlock("clock", ClockBlock::new, metalDevice(MapColor.GOLD));
    public static final DeferredBlock<RainSensorBlock> RAIN_SENSOR = BLOCKS.registerBlock("rain_sensor", RainSensorBlock::new, sensor(MapColor.COLOR_BLUE));
    public static final DeferredBlock<MaterialIOBlock> MATERIAL_IO = BLOCKS.registerBlock("material_io", MaterialIOBlock::new, metalDevice(MapColor.METAL));
    public static final DeferredBlock<CraftingIOBlock> CRAFTING_IO = BLOCKS.registerBlock("crafting_io", CraftingIOBlock::new, metalDevice(MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<CraftingCPUBlock> CRAFTING_CPU = BLOCKS.registerBlock("crafting_cpu", CraftingCPUBlock::new, metalDevice(MapColor.DIAMOND));

    private XLBlocks() {
    }

    public static Block[] coloredRedstoneCableBlocks() {
        return new Block[]{
                WHITE_REDSTONE_CABLE.get(),
                ORANGE_REDSTONE_CABLE.get(),
                MAGENTA_REDSTONE_CABLE.get(),
                LIGHT_BLUE_REDSTONE_CABLE.get(),
                YELLOW_REDSTONE_CABLE.get(),
                LIME_REDSTONE_CABLE.get(),
                PINK_REDSTONE_CABLE.get(),
                GRAY_REDSTONE_CABLE.get(),
                LIGHT_GRAY_REDSTONE_CABLE.get(),
                CYAN_REDSTONE_CABLE.get(),
                PURPLE_REDSTONE_CABLE.get(),
                BLUE_REDSTONE_CABLE.get(),
                BROWN_REDSTONE_CABLE.get(),
                GREEN_REDSTONE_CABLE.get(),
                COLORED_REDSTONE_CABLE.get(),
                BLACK_REDSTONE_CABLE.get()
        };
    }

    public static DeferredBlock<ColoredRedstoneCableBlock> coloredRedstoneCable(final int channel) {
        return switch (Mth.clamp(channel, 0, 15)) {
            case 0 -> WHITE_REDSTONE_CABLE;
            case 1 -> ORANGE_REDSTONE_CABLE;
            case 2 -> MAGENTA_REDSTONE_CABLE;
            case 3 -> LIGHT_BLUE_REDSTONE_CABLE;
            case 4 -> YELLOW_REDSTONE_CABLE;
            case 5 -> LIME_REDSTONE_CABLE;
            case 6 -> PINK_REDSTONE_CABLE;
            case 7 -> GRAY_REDSTONE_CABLE;
            case 8 -> LIGHT_GRAY_REDSTONE_CABLE;
            case 9 -> CYAN_REDSTONE_CABLE;
            case 10 -> PURPLE_REDSTONE_CABLE;
            case 11 -> BLUE_REDSTONE_CABLE;
            case 12 -> BROWN_REDSTONE_CABLE;
            case 13 -> GREEN_REDSTONE_CABLE;
            case 14 -> COLORED_REDSTONE_CABLE;
            case 15 -> BLACK_REDSTONE_CABLE;
            default -> COLORED_REDSTONE_CABLE;
        };
    }

    private static BlockBehaviour.Properties metalDevice(final MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.5F).sound(SoundType.METAL).noOcclusion();
    }

    private static BlockBehaviour.Properties glassDevice(final MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.5F).sound(SoundType.GLASS).noOcclusion();
    }

    private static BlockBehaviour.Properties cable(final MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.0F).sound(SoundType.WOOL).noOcclusion();
    }

    private static BlockBehaviour.Properties sensor(final MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F).sound(SoundType.AMETHYST).noOcclusion();
    }
}
