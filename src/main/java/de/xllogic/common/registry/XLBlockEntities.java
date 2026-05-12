package de.xllogic.common.registry;

import de.xllogic.XLLogicMod;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.ClockBlockEntity;
import de.xllogic.common.blockentity.CraftingCPUBlockEntity;
import de.xllogic.common.blockentity.CraftingIOBlockEntity;
import de.xllogic.common.blockentity.LightSensorBlockEntity;
import de.xllogic.common.blockentity.MaterialIOBlockEntity;
import de.xllogic.common.blockentity.RainSensorBlockEntity;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.blockentity.ScreenBlockEntity;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class XLBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, XLLogicMod.MOD_ID);

    public static final Supplier<BlockEntityType<ComputerBlockEntity>> COMPUTER = BLOCK_ENTITIES.register("computer", () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, XLBlocks.COMPUTER.get()).build(null));
    public static final Supplier<BlockEntityType<ScreenBlockEntity>> SCREEN = BLOCK_ENTITIES.register("screen", () -> BlockEntityType.Builder.of(ScreenBlockEntity::new, XLBlocks.SCREEN.get()).build(null));
    public static final Supplier<BlockEntityType<LightSensorBlockEntity>> LIGHT_SENSOR = BLOCK_ENTITIES.register("light_sensor", () -> BlockEntityType.Builder.of(LightSensorBlockEntity::new, XLBlocks.LIGHT_SENSOR.get()).build(null));
    public static final Supplier<BlockEntityType<ClockBlockEntity>> CLOCK = BLOCK_ENTITIES.register("clock", () -> BlockEntityType.Builder.of(ClockBlockEntity::new, XLBlocks.CLOCK.get()).build(null));
    public static final Supplier<BlockEntityType<RainSensorBlockEntity>> RAIN_SENSOR = BLOCK_ENTITIES.register("rain_sensor", () -> BlockEntityType.Builder.of(RainSensorBlockEntity::new, XLBlocks.RAIN_SENSOR.get()).build(null));
    public static final Supplier<BlockEntityType<XLApiBlockEntity>> XLAPI_BLOCK = BLOCK_ENTITIES.register("xlapi_block", () -> BlockEntityType.Builder.of(XLApiBlockEntity::new, XLBlocks.XLAPI_BLOCK.get()).build(null));
    public static final Supplier<BlockEntityType<RedstoneIOBlockEntity>> REDSTONE_IO = BLOCK_ENTITIES.register("redstone_io", () -> BlockEntityType.Builder.of(RedstoneIOBlockEntity::new, XLBlocks.REDSTONE_IO.get()).build(null));
    public static final Supplier<BlockEntityType<MaterialIOBlockEntity>> MATERIAL_IO = BLOCK_ENTITIES.register("material_io", () -> BlockEntityType.Builder.of(MaterialIOBlockEntity::new, XLBlocks.MATERIAL_IO.get()).build(null));
    public static final Supplier<BlockEntityType<CraftingIOBlockEntity>> CRAFTING_IO = BLOCK_ENTITIES.register("crafting_io", () -> BlockEntityType.Builder.of(CraftingIOBlockEntity::new, XLBlocks.CRAFTING_IO.get()).build(null));
    public static final Supplier<BlockEntityType<CraftingCPUBlockEntity>> CRAFTING_CPU = BLOCK_ENTITIES.register("crafting_cpu", () -> BlockEntityType.Builder.of(CraftingCPUBlockEntity::new, XLBlocks.CRAFTING_CPU.get()).build(null));

    private XLBlockEntities() {
    }
}
