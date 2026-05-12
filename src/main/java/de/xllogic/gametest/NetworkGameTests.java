package de.xllogic.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import de.xllogic.XLLogicMod;
import de.xllogic.common.block.ColoredRedstoneCableBlock;
import de.xllogic.common.block.ScreenBlock;
import de.xllogic.common.blockentity.CraftingCPUBlockEntity;
import de.xllogic.common.blockentity.CraftingIOBlockEntity;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.MaterialIOBlockEntity;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.blockentity.ScreenBlockEntity;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import de.xllogic.common.device.MaterialIOMode;
import de.xllogic.common.device.QueuedPlanReservationMode;
import de.xllogic.common.device.RedstoneIOMode;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.network.XLNetworkResolver;
import de.xllogic.common.network.XLRedstoneBusResolver;
import de.xllogic.common.network.payload.ComputerRuntimeStatePayload;
import de.xllogic.common.network.payload.ComputerSessionStatus;
import de.xllogic.common.network.payload.OpenComputerStatePayload;
import de.xllogic.common.network.payload.RecoveryDraftResumeStatus;
import de.xllogic.common.network.payload.ResumeRecoveryDraftResultPayload;
import de.xllogic.common.registry.XLBlocks;
import de.xllogic.common.util.XLItemFluidAccess;
import de.xllogic.runtime.PythonHostApi;
import de.xllogic.runtime.PythonPeripheralBinding;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

public final class NetworkGameTests {
    private static final String TEMPLATE = "network_empty_9x5x9";
    private static final String BATCH = "xllogic_network";
    private static final String SESSION_BATCH = "xllogic_computer_session";
    private static final String CRAFTING_BATCH = "xllogic_crafting";

    private static final String BLOCKER_FILTER = "filter";
    private static final String BLOCKER_DEVICE_CHANNEL = "device_channel";
    private static final String ALICE = "Alice";
    private static final String BOB = "Bob";
    private static final String SERVER_OWNED_SCRIPT = "print('server-owned')";
    private static final String LOCAL_DRAFT_SCRIPT = "print('local-draft')";
    private static final String FIRST_OPEN_CLAIMS_LOCK = "Alice should claim the editor lock on first open.";
    private static final String BOB_READ_ONLY_WHILE_ALICE_EDITS = "Bob should start as read-only while Alice owns the lock.";
    private static final String OAK_LOG_ITEM_ID = "minecraft:oak_log";
    private static final String OAK_PLANKS_ITEM_ID = "minecraft:oak_planks";
    private static final String COBBLESTONE_ITEM_ID = "minecraft:cobblestone";
    private static final String PLAN_STATUS_BLOCKED = "blocked";
    private static final String PLAN_STATUS_COMPLETED = "completed";
    private static final String PLAN_STATUS_FAILED = "failed";
    private static final String PLAN_ERROR_BUFFER_FULL = "buffer_full";
    private static final String PLAN_ERROR_CPU_UNAVAILABLE = "cpu_unavailable";
    private static final String PLAN_ERROR_INTERMEDIATE_CONTAMINATED = "intermediate_contaminated";
    private static final String PLAN_ERROR_INTERMEDIATE_MISSING = "intermediate_missing";
    private static final String PLAN_ERROR_MATERIAL_MISSING = "material_missing";
    private static final String PLAN_ERROR_OUTPUT_FULL = "output_full";
    private static final String PLAN_ERROR_RECIPE_INVALID = "recipe_invalid";
    private static final String PLAN_ERROR_ROUTE_MISSING = "route_missing";
    private static final String ACTION_HINT_CLEAN_BUFFER = "clean_buffer";
    private static final String ACTION_HINT_RESTORE_INTERMEDIATE = "restore_intermediate";
    private static final String ACTION_HINT_SWITCH_TO_ACTIVE_CYCLE = "switch_to_active_cycle";
    private static final String JSON_ACTION_HINT = "action_hint";
    private static final String JOB_STATUS_BLOCKED = "blocked";
    private static final String JOB_STATUS_COMPLETED = "completed";
    private static final String JOB_STATUS_FAILED = "failed";
    private static final String JOB_STATUS_IDLE = "idle";
    private static final String JOB_STATUS_RESUMABLE = "resumable";
    private static final String QUEUE_RESERVATION_MODE_FULL_QUEUE = "full_queue";
    private static final String QUEUE_RESERVATION_MODE_ACTIVE_CYCLE = "active_cycle";
    private static final String CRAFTING_IO_API_NAME = "crafting_io";
    private static final String CRAFTING_CPU_API_NAME = "crafting_cpu";
    private static final String MATERIAL_IO_TYPE = "material_io";
    private static final String REDSTONE_IO_TYPE = "redstone_io";
    private static final String SOURCE_IO_API_NAME = "source_io";
    private static final String BUFFER_IO_API_NAME = "buffer_io";
    private static final String SINK_IO_API_NAME = "sink_io";
    private static final String MATERIAL_ALIAS_API_NAME = "material_alias_io";
    private static final String REDSTONE_ALIAS_API_NAME = "redstone_alias_io";
    private static final String SIDE_ALIAS_SOURCE = "source_buffer";
    private static final String SIDE_ALIAS_SIGNAL = "signal_bus";
    private static final String SIDE_ALIAS_INPUT = "input_bus";
    private static final String SIDE_ALIAS_OUTPUT = "output_bus";
    private static final String SOURCE_ROUTE_NAME = "source";
    private static final String BUFFER_ROUTE_NAME = "buffer";
    private static final String SINK_ROUTE_NAME = "sink";
    private static final int CRAFTING_PLAN_GRID_SIZE = 7;
    private static final int LEASE_TIMEOUT_TICKS = 4;

    private static final BlockPos COMPUTER_A = new BlockPos(1, 1, 1);
    private static final BlockPos COMPUTER_B = new BlockPos(5, 1, 1);
    private static final BlockPos CABLE_LEFT = new BlockPos(2, 1, 1);
    private static final BlockPos CABLE_CENTER = new BlockPos(3, 1, 1);
    private static final BlockPos CABLE_RIGHT = new BlockPos(4, 1, 1);

    private static final BlockPos BUS_OUTPUT = new BlockPos(1, 1, 1);
    private static final BlockPos BUS_LEFT = new BlockPos(2, 1, 1);
    private static final BlockPos BUS_RIGHT = new BlockPos(3, 1, 1);
    private static final BlockPos BUS_INPUT = new BlockPos(4, 1, 1);
    private static final BlockPos CRAFTING_IO_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CRAFTING_CPU_POS = new BlockPos(3, 1, 1);
    private static final BlockPos CRAFTING_INPUT_CHEST_POS = new BlockPos(2, 1, 1);
    private static final BlockPos CRAFTING_OUTPUT_CHEST_POS = new BlockPos(4, 1, 1);
    private static final BlockPos CRAFTING_SOURCE_IO_POS = new BlockPos(1, 1, 3);
    private static final BlockPos CRAFTING_SOURCE_CHEST_POS = new BlockPos(1, 1, 4);
    private static final BlockPos CRAFTING_BUFFER_IO_POS = new BlockPos(3, 1, 3);
    private static final BlockPos CRAFTING_BUFFER_CHEST_POS = new BlockPos(3, 1, 4);
    private static final BlockPos CRAFTING_SINK_IO_POS = new BlockPos(5, 1, 3);
    private static final BlockPos CRAFTING_SINK_CHEST_POS = new BlockPos(5, 1, 4);
    private static final BlockPos SCREEN_COMPUTER = new BlockPos(1, 1, 1);
    private static final BlockPos SCREEN_CABLE_A = new BlockPos(2, 1, 1);
    private static final BlockPos SCREEN_CABLE_B = new BlockPos(3, 1, 1);
    private static final BlockPos SCREEN_BOTTOM_LEFT = new BlockPos(4, 1, 1);
    private static final BlockPos SCREEN_BOTTOM_RIGHT = new BlockPos(5, 1, 1);
    private static final BlockPos SCREEN_TOP_LEFT = new BlockPos(4, 2, 1);
    private static final BlockPos SCREEN_TOP_RIGHT = new BlockPos(5, 2, 1);
    private static final BlockPos SCREEN_EXTENSION_RIGHT = new BlockPos(6, 1, 1);

    private NetworkGameTests() {
    }

    public static void registerGameTests(final RegisterGameTestsEvent event) {
        event.register(NetworkGameTests.class);
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void discoveryConflictWithoutXlapi(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());
        helper.setBlock(CABLE_LEFT, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(CABLE_CENTER, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(CABLE_RIGHT, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(COMPUTER_B, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity firstComputer = helper.getBlockEntity(COMPUTER_A);
        final ComputerBlockEntity secondComputer = helper.getBlockEntity(COMPUTER_B);
        firstComputer.refreshConnectedEndpoints();
        secondComputer.refreshConnectedEndpoints();
        firstComputer.refreshNetworkAnimationState();
        secondComputer.refreshNetworkAnimationState();

        final XLNetworkResolver.LocalSegmentDebugSnapshot snapshot = XLNetworkResolver.inspectLocalSegment(helper.getLevel(), helper.absolutePos(CABLE_CENTER));
        helper.assertTrue(snapshot.hasComputerConflict(), "Expected a local discovery conflict for two computers on one segment.");
        helper.assertValueEqual(snapshot.computerPositions().size(), 2, "discovery computer count");
        helper.assertValueEqual(snapshot.xlapiBoundaryCount(), 0, "xlapi boundary count");
        helper.assertTrue(firstComputer.hasNetworkConflict(), "First computer should mark the segment as conflicting.");
        helper.assertTrue(secondComputer.hasNetworkConflict(), "Second computer should mark the segment as conflicting.");
        helper.assertFalse(firstComputer.isNetworkAnimationActive(), "First computer animation should stop on an invalid network.");
        helper.assertFalse(secondComputer.isNetworkAnimationActive(), "Second computer animation should stop on an invalid network.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void discoverySeparatedByXlapiBoundary(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());
        helper.setBlock(CABLE_LEFT, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(CABLE_CENTER, XLBlocks.XLAPI_BLOCK.get());
        helper.setBlock(CABLE_RIGHT, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(COMPUTER_B, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity firstComputer = helper.getBlockEntity(COMPUTER_A);
        final ComputerBlockEntity secondComputer = helper.getBlockEntity(COMPUTER_B);
        final XLApiBlockEntity xlApi = helper.getBlockEntity(CABLE_CENTER);
        firstComputer.refreshConnectedEndpoints();
        secondComputer.refreshConnectedEndpoints();
        firstComputer.refreshNetworkAnimationState();
        secondComputer.refreshNetworkAnimationState();
        xlApi.refreshNetworkAnimationState();

        final XLNetworkResolver.LocalSegmentDebugSnapshot leftSnapshot = XLNetworkResolver.inspectLocalSegment(helper.getLevel(), helper.absolutePos(COMPUTER_A));
        final XLNetworkResolver.LocalSegmentDebugSnapshot rightSnapshot = XLNetworkResolver.inspectLocalSegment(helper.getLevel(), helper.absolutePos(COMPUTER_B));
        helper.assertFalse(leftSnapshot.hasComputerConflict(), "XLAPI should separate the left computer from the right segment.");
        helper.assertFalse(rightSnapshot.hasComputerConflict(), "XLAPI should separate the right computer from the left segment.");
        helper.assertValueEqual(leftSnapshot.computerPositions().size(), 1, "left segment computer count");
        helper.assertValueEqual(rightSnapshot.computerPositions().size(), 1, "right segment computer count");
        helper.assertTrue(leftSnapshot.xlapiBoundaryCount() >= 1, "Left segment should report the XLAPI boundary.");
        helper.assertTrue(rightSnapshot.xlapiBoundaryCount() >= 1, "Right segment should report the XLAPI boundary.");
        helper.assertFalse(firstComputer.hasNetworkConflict(), "First computer should remain valid when XLAPI separates the segments.");
        helper.assertFalse(secondComputer.hasNetworkConflict(), "Second computer should remain valid when XLAPI separates the segments.");
        helper.assertTrue(firstComputer.isNetworkAnimationActive(), "First computer animation should run on a valid network.");
        helper.assertTrue(secondComputer.isNetworkAnimationActive(), "Second computer animation should run on a valid network.");
        helper.assertTrue(xlApi.isNetworkAnimationActive(), "XLAPI animation should stay active when it separates two otherwise valid segments.");
        helper.assertValueEqual(XLNetworkResolver.resolveComputers(helper.getLevel(), helper.absolutePos(COMPUTER_A)).size(), 1, "resolved left computers");
        helper.assertValueEqual(XLNetworkResolver.resolveComputers(helper.getLevel(), helper.absolutePos(COMPUTER_B)).size(), 1, "resolved right computers");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void busReportsFilterBlockerForColoredMismatch(final GameTestHelper helper) {
        helper.setBlock(BUS_OUTPUT, XLBlocks.REDSTONE_IO.get());
        helper.setBlock(BUS_LEFT, coloredCable(3));
        helper.setBlock(BUS_RIGHT, coloredCable(11));
        helper.setBlock(BUS_INPUT, XLBlocks.REDSTONE_IO.get());

        final RedstoneIOBlockEntity output = helper.getBlockEntity(BUS_OUTPUT);
        output.setMode(RedstoneIOMode.OUTPUT);
        output.setBusChannel(Direction.EAST, 3);
        output.setSideLevel(Direction.EAST, 9);

        final RedstoneIOBlockEntity input = helper.getBlockEntity(BUS_INPUT);
        input.setMode(RedstoneIOMode.INPUT);
        input.setBusChannel(Direction.WEST, 11);

        final XLRedstoneBusResolver.ChannelFlowDebugSnapshot flow = XLRedstoneBusResolver.inspectChannelFlow(helper.getLevel(), helper.absolutePos(BUS_LEFT), 3);
        helper.assertValueEqual(flow.producerPositions().size(), 1, "producer count for colored mismatch flow");
        helper.assertValueEqual(flow.consumerPositions().size(), 0, "consumer count for colored mismatch flow");
        helper.assertTrue(hasBlockerType(flow, BLOCKER_FILTER), "Expected a filter blocker for differently coloured adjacent cables.");
        helper.assertFalse(hasBlockerType(flow, BLOCKER_DEVICE_CHANNEL), "Colored mismatch should not report a device-channel blocker.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void busReportsDeviceChannelBlocker(final GameTestHelper helper) {
        helper.setBlock(BUS_OUTPUT, XLBlocks.REDSTONE_IO.get());
        helper.setBlock(BUS_LEFT, XLBlocks.REDSTONE_BUS_CABLE.get());
        helper.setBlock(BUS_RIGHT, XLBlocks.REDSTONE_BUS_CABLE.get());
        helper.setBlock(BUS_INPUT, XLBlocks.REDSTONE_IO.get());

        final RedstoneIOBlockEntity output = helper.getBlockEntity(BUS_OUTPUT);
        output.setMode(RedstoneIOMode.OUTPUT);
        output.setBusChannel(Direction.EAST, 3);
        output.setSideLevel(Direction.EAST, 7);

        final RedstoneIOBlockEntity input = helper.getBlockEntity(BUS_INPUT);
        input.setMode(RedstoneIOMode.INPUT);
        input.setBusChannel(Direction.WEST, 4);

        final XLRedstoneBusResolver.ChannelFlowDebugSnapshot flow = XLRedstoneBusResolver.inspectChannelFlow(helper.getLevel(), helper.absolutePos(BUS_LEFT), 3);
        helper.assertValueEqual(flow.producerPositions().size(), 1, "producer count for device-channel mismatch flow");
        helper.assertValueEqual(flow.consumerPositions().size(), 0, "consumer count for device-channel mismatch flow");
        helper.assertTrue(hasBlockerType(flow, BLOCKER_DEVICE_CHANNEL), "Expected a device-channel blocker for a Redstone I/O using another bus channel.");
        helper.assertFalse(hasBlockerType(flow, BLOCKER_FILTER), "Device-channel mismatch should not report a colored filter blocker.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void materialIoSideAliasPersistsAndResolvesInHostApi(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_SOURCE_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_SOURCE_CHEST_POS, Blocks.CHEST.defaultBlockState());

        final MaterialIOBlockEntity materialIo = helper.getBlockEntity(CRAFTING_SOURCE_IO_POS);
        materialIo.setMode(MaterialIOMode.ITEMS_ONLY);
        materialIo.setSideAlias(Direction.SOUTH, SIDE_ALIAS_SOURCE);

        final net.minecraft.nbt.CompoundTag persistedState = materialIo.saveCustomOnly(helper.getLevel().registryAccess());
        final MaterialIOBlockEntity reloadedMaterialIo = new MaterialIOBlockEntity(materialIo.getBlockPos(), materialIo.getBlockState());
        reloadedMaterialIo.setLevel(helper.getLevel());
        reloadedMaterialIo.loadCustomOnly(persistedState, helper.getLevel().registryAccess());

        helper.assertValueEqual(reloadedMaterialIo.getSideAlias(Direction.SOUTH), SIDE_ALIAS_SOURCE, "persisted material I/O south alias");

        final PythonHostApi hostApi = PythonHostApi.server(helper.getLevel(), "material-alias-test", materialIo.getBlockPos(), List.of(
                localBinding(MATERIAL_ALIAS_API_NAME, MATERIAL_IO_TYPE, materialIo.getBlockPos())
        ));
        final PythonHostApi.DeviceBridge device = hostApi.getDevice(MATERIAL_ALIAS_API_NAME);
        helper.assertTrue(device != null, "Material I/O device should be reachable for alias tests.");
        helper.assertValueEqual(device.itemSlotCount(SIDE_ALIAS_SOURCE), 27, "aliased material I/O side should resolve to the south chest inventory");
        helper.assertTrue(device.sideAliasesJson().contains("\"south\":\"" + SIDE_ALIAS_SOURCE + "\""), "side alias JSON should include the persisted south alias.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void materialIoTransfersItemsToAnotherMaterialIo(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_SOURCE_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_SOURCE_CHEST_POS, Blocks.CHEST.defaultBlockState());
        helper.setBlock(CRAFTING_SINK_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_SINK_CHEST_POS, Blocks.CHEST.defaultBlockState());

        final MaterialIOBlockEntity sourceIo = helper.getBlockEntity(CRAFTING_SOURCE_IO_POS);
        final MaterialIOBlockEntity sinkIo = helper.getBlockEntity(CRAFTING_SINK_IO_POS);
        final ChestBlockEntity sourceChest = helper.getBlockEntity(CRAFTING_SOURCE_CHEST_POS);
        final ChestBlockEntity sinkChest = helper.getBlockEntity(CRAFTING_SINK_CHEST_POS);

        clearChest(sourceChest);
        clearChest(sinkChest);
        sourceChest.setItem(0, new ItemStack(Items.OAK_LOG, 5));
        sourceChest.setChanged();

        sourceIo.setMode(MaterialIOMode.ITEMS_ONLY);
        sinkIo.setMode(MaterialIOMode.ITEMS_ONLY);
        sourceIo.setSideAlias(Direction.SOUTH, SIDE_ALIAS_INPUT);
        sinkIo.setSideAlias(Direction.SOUTH, SIDE_ALIAS_OUTPUT);

        final PythonHostApi hostApi = PythonHostApi.server(helper.getLevel(), "material-transfer-test", sourceIo.getBlockPos(), List.of(
                localBinding(SOURCE_IO_API_NAME, MATERIAL_IO_TYPE, sourceIo.getBlockPos()),
                localBinding(SINK_IO_API_NAME, MATERIAL_IO_TYPE, sinkIo.getBlockPos())
        ));
        final PythonHostApi.DeviceBridge device = hostApi.getDevice(SOURCE_IO_API_NAME);
        helper.assertTrue(device != null, "Source Material I/O device should be reachable for endpoint transfer tests.");

        helper.assertValueEqual(device.transferItemTo(SINK_IO_API_NAME, SIDE_ALIAS_INPUT, SIDE_ALIAS_OUTPUT, 0, 3), 3,
                "material I/O should move items directly to another material I/O endpoint");
        helper.assertValueEqual(XLItemFluidAccess.itemId(sourceChest.getItem(0)), OAK_LOG_ITEM_ID, "source chest item id after direct transfer");
        helper.assertValueEqual(sourceChest.getItem(0).getCount(), 2, "source chest count after direct transfer");
        helper.assertValueEqual(XLItemFluidAccess.itemId(sinkChest.getItem(0)), OAK_LOG_ITEM_ID, "sink chest item id after direct transfer");
        helper.assertValueEqual(sinkChest.getItem(0).getCount(), 3, "sink chest count after direct transfer");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void redstoneIoSideAliasResolvesReadWriteAndChannelAccess(final GameTestHelper helper) {
        helper.setBlock(BUS_OUTPUT, XLBlocks.REDSTONE_IO.get());

        final RedstoneIOBlockEntity redstoneIo = helper.getBlockEntity(BUS_OUTPUT);
        redstoneIo.setMode(RedstoneIOMode.OUTPUT);
        redstoneIo.setSideAlias(Direction.EAST, SIDE_ALIAS_SIGNAL);

        final PythonHostApi hostApi = PythonHostApi.server(helper.getLevel(), "redstone-alias-test", redstoneIo.getBlockPos(), List.of(
                localBinding(REDSTONE_ALIAS_API_NAME, REDSTONE_IO_TYPE, redstoneIo.getBlockPos())
        ));
        final PythonHostApi.DeviceBridge device = hostApi.getDevice(REDSTONE_ALIAS_API_NAME);
        helper.assertTrue(device != null, "Redstone I/O device should be reachable for alias tests.");

        helper.assertValueEqual(device.write(SIDE_ALIAS_SIGNAL, 11), 11, "aliased redstone write should target the east side");
        helper.assertValueEqual(device.read(SIDE_ALIAS_SIGNAL), 11, "aliased redstone read should resolve the east side level");
        helper.assertValueEqual(device.setChannel(SIDE_ALIAS_SIGNAL, 7), 7, "aliased bus channel write should target the east side");
        helper.assertValueEqual(device.channel(SIDE_ALIAS_SIGNAL), 7, "aliased bus channel read should resolve the east side");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void craftingCpuSideAliasesResolveDuringCraftExecution(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_CPU_POS, XLBlocks.CRAFTING_CPU.get());
        helper.setBlock(CRAFTING_INPUT_CHEST_POS, Blocks.CHEST.defaultBlockState());
        helper.setBlock(CRAFTING_OUTPUT_CHEST_POS, Blocks.CHEST.defaultBlockState());

        final CraftingCPUBlockEntity craftingCpu = helper.getBlockEntity(CRAFTING_CPU_POS);
        final ChestBlockEntity inputChest = helper.getBlockEntity(CRAFTING_INPUT_CHEST_POS);
        final ChestBlockEntity outputChest = helper.getBlockEntity(CRAFTING_OUTPUT_CHEST_POS);
        clearChest(inputChest);
        clearChest(outputChest);
        craftingCpu.clearRecipe();
        craftingCpu.setRecipeSlot(0, OAK_LOG_ITEM_ID, 1);
        craftingCpu.setSideAlias(Direction.WEST, SIDE_ALIAS_INPUT);
        craftingCpu.setSideAlias(Direction.EAST, SIDE_ALIAS_OUTPUT);
        inputChest.setItem(0, new ItemStack(Items.OAK_LOG, 1));

        final PythonHostApi hostApi = PythonHostApi.server(helper.getLevel(), "crafting-cpu-alias-test", craftingCpu.getBlockPos(), List.of(
                localBinding(CRAFTING_CPU_API_NAME, CRAFTING_CPU_API_NAME, craftingCpu.getBlockPos())
        ));
        final PythonHostApi.DeviceBridge device = hostApi.getDevice(CRAFTING_CPU_API_NAME);
        helper.assertTrue(device != null, "Crafting CPU device should be reachable for alias tests.");

        helper.assertValueEqual(device.craft(SIDE_ALIAS_INPUT, SIDE_ALIAS_OUTPUT, 1), 1, "aliased crafting CPU sides should complete one craft");
        helper.assertValueEqual(XLItemFluidAccess.itemId(outputChest.getItem(0)), OAK_PLANKS_ITEM_ID, "crafting CPU output chest item id after aliased craft");
        helper.assertValueEqual(outputChest.getItem(0).getCount(), 4, "crafting CPU output chest count after aliased craft");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void craftingIoRouteAndMaterialSidesResolveNamedAliases(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_IO_POS, XLBlocks.CRAFTING_IO.get());
        helper.setBlock(CRAFTING_SOURCE_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_SINK_IO_POS, XLBlocks.MATERIAL_IO.get());

        final CraftingIOBlockEntity craftingIo = helper.getBlockEntity(CRAFTING_IO_POS);
        final MaterialIOBlockEntity sourceIo = helper.getBlockEntity(CRAFTING_SOURCE_IO_POS);
        final MaterialIOBlockEntity sinkIo = helper.getBlockEntity(CRAFTING_SINK_IO_POS);
        sourceIo.setMode(MaterialIOMode.ITEMS_ONLY);
        sinkIo.setMode(MaterialIOMode.ITEMS_ONLY);
        sourceIo.setSideAlias(Direction.SOUTH, SIDE_ALIAS_INPUT);
        sinkIo.setSideAlias(Direction.SOUTH, SIDE_ALIAS_OUTPUT);
        craftingIo.setMaterialInputEndpoint(SOURCE_IO_API_NAME);
        craftingIo.setMaterialOutputEndpoint(SINK_IO_API_NAME);

        final PythonHostApi hostApi = PythonHostApi.server(helper.getLevel(), "crafting-io-alias-test", craftingIo.getBlockPos(), List.of(
                localBinding(CRAFTING_IO_API_NAME, CRAFTING_IO_API_NAME, craftingIo.getBlockPos()),
                localBinding(SOURCE_IO_API_NAME, MATERIAL_IO_TYPE, sourceIo.getBlockPos()),
                localBinding(SINK_IO_API_NAME, MATERIAL_IO_TYPE, sinkIo.getBlockPos())
        ));
        final PythonHostApi.DeviceBridge device = hostApi.getDevice(CRAFTING_IO_API_NAME);
        helper.assertTrue(device != null, "Crafting I/O device should be reachable for alias tests.");

        helper.assertValueEqual(device.setMaterialInputSide(SIDE_ALIAS_INPUT), Direction.SOUTH.getSerializedName(), "aliased material input side should resolve to south");
        helper.assertValueEqual(device.setMaterialOutputSide(SIDE_ALIAS_OUTPUT), Direction.SOUTH.getSerializedName(), "aliased material output side should resolve to south");
        helper.assertValueEqual(device.setRoute(SOURCE_ROUTE_NAME, SOURCE_IO_API_NAME, SIDE_ALIAS_INPUT), SOURCE_ROUTE_NAME, "aliased route side should be accepted");
        helper.assertValueEqual(craftingIo.getMaterialInputSide(), Direction.SOUTH, "stored material input side after alias resolution");
        helper.assertValueEqual(craftingIo.getMaterialOutputSide(), Direction.SOUTH, "stored material output side after alias resolution");
        helper.assertValueEqual(craftingIo.getRouteSide(SOURCE_ROUTE_NAME), Direction.SOUTH, "stored route side after alias resolution");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void discoveryExpandsContiguousScreenSurfaceToTwoByTwo(final GameTestHelper helper) {
        helper.setBlock(SCREEN_COMPUTER, XLBlocks.COMPUTER.get());
        helper.setBlock(SCREEN_CABLE_A, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(SCREEN_CABLE_B, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(SCREEN_BOTTOM_LEFT, screen(Direction.SOUTH));
        helper.setBlock(SCREEN_BOTTOM_RIGHT, screen(Direction.SOUTH));
        helper.setBlock(SCREEN_TOP_LEFT, screen(Direction.SOUTH));
        helper.setBlock(SCREEN_TOP_RIGHT, screen(Direction.SOUTH));

        final ComputerBlockEntity computer = helper.getBlockEntity(SCREEN_COMPUTER);
        computer.refreshConnectedEndpoints();

        assertScreenState(helper, SCREEN_BOTTOM_LEFT, 2, 2, true);
        assertScreenState(helper, SCREEN_BOTTOM_RIGHT, 2, 2, false);
        assertScreenState(helper, SCREEN_TOP_LEFT, 2, 2, false);
        assertScreenState(helper, SCREEN_TOP_RIGHT, 2, 2, false);
        helper.assertValueEqual(computer.getLinkedScreenPositions().size(), 4, "linked screen count after contiguous 2x2 discovery");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = BATCH)
    public static void placingAdjacentScreenExtendsExistingPanelWithoutManualRediscovery(final GameTestHelper helper) {
        helper.setBlock(SCREEN_COMPUTER, XLBlocks.COMPUTER.get());
        helper.setBlock(SCREEN_CABLE_A, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(SCREEN_CABLE_B, XLBlocks.NETWORK_CABLE.get());
        helper.setBlock(SCREEN_BOTTOM_LEFT, screen(Direction.SOUTH));
        helper.setBlock(SCREEN_BOTTOM_RIGHT, screen(Direction.SOUTH));

        final ComputerBlockEntity computer = helper.getBlockEntity(SCREEN_COMPUTER);
        computer.refreshConnectedEndpoints();

        assertScreenState(helper, SCREEN_BOTTOM_LEFT, 2, 1, true);
        assertScreenState(helper, SCREEN_BOTTOM_RIGHT, 2, 1, false);
        helper.assertValueEqual(computer.getLinkedScreenPositions().size(), 2, "linked screen count before extension");

        helper.setBlock(SCREEN_EXTENSION_RIGHT, screen(Direction.SOUTH));

        final ComputerBlockEntity refreshedComputer = helper.getBlockEntity(SCREEN_COMPUTER);
        assertScreenState(helper, SCREEN_BOTTOM_LEFT, 3, 1, true);
        assertScreenState(helper, SCREEN_BOTTOM_RIGHT, 3, 1, false);
        assertScreenState(helper, SCREEN_EXTENSION_RIGHT, 3, 1, false);
        helper.assertValueEqual(refreshedComputer.getLinkedScreenPositions().size(), 3, "linked screen count after extension");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void readOnlyViewerReceivesForeignEditorState(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        final OpenComputerStatePayload aliceOpen = XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS);
        final OpenComputerStatePayload bobOpen = XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS);

        helper.assertTrue(aliceOpen.editable(), FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(bobOpen.editable(), "Bob should open the same computer in read-only mode while Alice holds the lock.");
        helper.assertValueEqual(bobOpen.activeEditorName(), ALICE, "read-only active editor name");

        helper.runAfterDelay(2, () -> {
            final ComputerRuntimeStatePayload aliceUpdate = XLNetworking.synchronizeComputerSession(alice, computer, LEASE_TIMEOUT_TICKS);
            helper.assertTrue(aliceUpdate != null, "Active editor heartbeat should return a state payload so the client can clear stale session warnings.");
            helper.assertTrue(aliceUpdate.editable(), "Alice should remain editable while her heartbeat refreshes the lease.");
            helper.assertValueEqual(aliceUpdate.sessionStatus(), ComputerSessionStatus.ACTIVE, "active editor session status");
            final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
            helper.assertTrue(bobUpdate != null, "Read-only viewer should receive session state updates while another player edits.");
            helper.assertFalse(bobUpdate.editable(), "Bob should remain read-only while Alice keeps the lease alive.");
            helper.assertValueEqual(bobUpdate.activeEditorName(), ALICE, "read-only runtime active editor name");
            helper.assertValueEqual(bobUpdate.sessionStatus(), ComputerSessionStatus.ACTIVE, "read-only viewer session status");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void releasedLeaseHandsOffToWaitingViewer(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
            BOB_READ_ONLY_WHILE_ALICE_EDITS);

        computer.releaseEditor(alice);

        final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(bobUpdate != null, "Waiting viewer should receive an update after the lock is released.");
        helper.assertTrue(bobUpdate.editable(), "Bob should take over editing immediately after Alice releases the lock.");
        helper.assertValueEqual(bobUpdate.activeEditorName(), BOB, "handoff editor name after release");
        helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should own the editor lock after the handoff.");
        helper.assertFalse(computer.isEditableBy(alice, LEASE_TIMEOUT_TICKS), "Alice should no longer own the editor lock after releasing it.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void timedOutLeaseHandsOffToWaitingViewer(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
            BOB_READ_ONLY_WHILE_ALICE_EDITS);

        helper.runAfterDelay(LEASE_TIMEOUT_TICKS + 1L, () -> {
            helper.assertFalse(computer.hasActiveEditor(LEASE_TIMEOUT_TICKS), "Alice's editor lease should expire after the configured timeout.");
            final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
            helper.assertTrue(bobUpdate != null, "Waiting viewer should receive a state update when the timed-out lease becomes available.");
            helper.assertTrue(bobUpdate.editable(), "Bob should take over editing after Alice's lease times out.");
            helper.assertValueEqual(bobUpdate.activeEditorName(), BOB, "handoff editor name after timeout");
            helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should own the editor lock after timeout handoff.");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void disconnectOrCrashHandsOffToWaitingViewer(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
            BOB_READ_ONLY_WHILE_ALICE_EDITS);

        XLNetworking.releaseEditorSessionsForPlayer(alice);

        final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(bobUpdate != null, "Waiting viewer should receive a state update after the active editor disconnects.");
        helper.assertTrue(bobUpdate.editable(), "Bob should take over editing immediately after Alice disconnects or crashes.");
        helper.assertValueEqual(bobUpdate.activeEditorName(), BOB, "handoff editor name after disconnect");
        helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should own the editor lock after disconnect handoff.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void reconnectingEditorReclaimsLockAfterDisconnect(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);

        XLNetworking.releaseEditorSessionsForPlayer(alice);

        final ServerPlayer aliceReconnect = mockPlayer(helper, ALICE);
        final OpenComputerStatePayload reconnectOpen = XLNetworking.createOpenComputerStatePayload(aliceReconnect, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(reconnectOpen.editable(), "Alice should be able to reclaim the editor lock immediately after reconnecting.");
        helper.assertValueEqual(reconnectOpen.activeEditorName(), ALICE, "editor name after reconnect");
        helper.assertTrue(computer.isEditableBy(aliceReconnect, LEASE_TIMEOUT_TICKS), "Reconnected Alice should own the editor lock again.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void disconnectingReadOnlyViewerKeepsForeignLockIntact(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
            BOB_READ_ONLY_WHILE_ALICE_EDITS);

        XLNetworking.releaseEditorSessionsForPlayer(bob);

        helper.assertTrue(computer.isEditableBy(alice, LEASE_TIMEOUT_TICKS), "Alice should keep the editor lock when a read-only viewer disconnects.");
        final OpenComputerStatePayload bobReconnectOpen = XLNetworking.createOpenComputerStatePayload(mockPlayer(helper, BOB), computer, LEASE_TIMEOUT_TICKS);
        helper.assertFalse(bobReconnectOpen.editable(), "Bob should still reopen read-only while Alice stays connected as the active editor.");
        helper.assertValueEqual(bobReconnectOpen.activeEditorName(), ALICE, "active editor name after read-only reconnect");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void reopenedViewerClaimsReleasedLease(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
                BOB_READ_ONLY_WHILE_ALICE_EDITS);

        XLNetworking.releaseEditorSessionsForPlayer(bob);
        computer.releaseEditor(alice);

        final ServerPlayer bobReopen = mockPlayer(helper, BOB);
        final OpenComputerStatePayload reopenPayload = XLNetworking.createOpenComputerStatePayload(bobReopen, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(reopenPayload.editable(), "Bob should claim the editor lock when reopening after Alice released it.");
        helper.assertValueEqual(reopenPayload.activeEditorName(), BOB, "editor name after viewer reopen");
        helper.assertTrue(computer.isEditableBy(bobReopen, LEASE_TIMEOUT_TICKS), "Reopened Bob should own the editor lock.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void recoveryDraftResumeRestoresEditableSession(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);

        XLNetworking.releaseEditorSessionsForPlayer(alice);

        final String recoveryDraftScript = computer.getScript();
        final ResumeRecoveryDraftResultPayload resumePayload = XLNetworking.resumeRecoveryDraftSession(alice, computer, recoveryDraftScript, LEASE_TIMEOUT_TICKS);
        helper.assertValueEqual(resumePayload.status(), RecoveryDraftResumeStatus.RESUMED, "resume handshake status");
        helper.assertTrue(resumePayload.editable(), "Resume handshake should restore editable access for the same player.");
        helper.assertValueEqual(resumePayload.activeEditorName(), ALICE, "resume editor name");
        helper.assertValueEqual(computer.getScript(), recoveryDraftScript, "recovery draft should replace the persisted script on resume");
        helper.assertTrue(computer.isEditableBy(alice, LEASE_TIMEOUT_TICKS), "Alice should own the editor lock again after resuming the recovery draft.");
        helper.succeed();
    }

        @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
        public static void recoveryDraftResumeDetectsScriptDivergence(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        computer.setScript(SERVER_OWNED_SCRIPT);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
            FIRST_OPEN_CLAIMS_LOCK);

        XLNetworking.releaseEditorSessionsForPlayer(alice);

        final ResumeRecoveryDraftResultPayload resumePayload = XLNetworking.resumeRecoveryDraftSession(alice, computer, LOCAL_DRAFT_SCRIPT, LEASE_TIMEOUT_TICKS);
        helper.assertValueEqual(resumePayload.status(), RecoveryDraftResumeStatus.DIVERGED, "resume handshake diverged status");
        helper.assertValueEqual(resumePayload.script(), SERVER_OWNED_SCRIPT, "diverged resume should return the current server script snapshot");
        helper.assertValueEqual(computer.getScript(), SERVER_OWNED_SCRIPT, "Diverged resume must not overwrite the persisted server script.");
        helper.assertFalse(computer.hasActiveEditor(LEASE_TIMEOUT_TICKS), "Diverged resume should not silently claim the editor lock.");
        helper.succeed();
        }

        @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
        public static void forcedRecoveryDraftResumeOverwritesServerScriptExplicitly(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        computer.setScript(SERVER_OWNED_SCRIPT);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
            FIRST_OPEN_CLAIMS_LOCK);

        XLNetworking.releaseEditorSessionsForPlayer(alice);

        final ResumeRecoveryDraftResultPayload resumePayload = XLNetworking.resumeRecoveryDraftSession(alice, computer, LOCAL_DRAFT_SCRIPT,
            LEASE_TIMEOUT_TICKS, true);
        helper.assertValueEqual(resumePayload.status(), RecoveryDraftResumeStatus.RESUMED, "forced resume handshake status");
        helper.assertValueEqual(computer.getScript(), LOCAL_DRAFT_SCRIPT, "Forced resume should overwrite the server script only after the explicit force path.");
        helper.assertTrue(computer.isEditableBy(alice, LEASE_TIMEOUT_TICKS), "Alice should own the editor lock after explicitly forcing the resume.");
        helper.succeed();
        }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void recoveryDraftResumeWaitsWhileForeignEditorHoldsLock(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);
        computer.setScript(SERVER_OWNED_SCRIPT);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);

        XLNetworking.releaseEditorSessionsForPlayer(alice);
        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
                "Bob should claim the editor lock before Alice's recovery draft can resume.");

        final ResumeRecoveryDraftResultPayload resumePayload = XLNetworking.resumeRecoveryDraftSession(alice, computer, "print('alice draft')", LEASE_TIMEOUT_TICKS);
        helper.assertValueEqual(resumePayload.status(), RecoveryDraftResumeStatus.BLOCKED_BY_OTHER_EDITOR, "resume handshake blocked status");
        helper.assertValueEqual(resumePayload.activeEditorName(), BOB, "resume blocked editor name");
        helper.assertValueEqual(computer.getScript(), SERVER_OWNED_SCRIPT, "Blocked resume must not overwrite the persisted computer script.");
        helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should keep the editor lock while Alice waits in the recovery draft.");
        helper.assertFalse(computer.isEditableBy(alice, LEASE_TIMEOUT_TICKS), "Alice should stay detached while Bob owns the editor lock.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void teleportingEditorHandsOffToWaitingViewer(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
                BOB_READ_ONLY_WHILE_ALICE_EDITS);

        alice.moveTo(64.0D, 1.0D, 1.0D);
        XLNetworking.validateTrackedEditorSessionsForPlayer(alice);

        final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(bobUpdate != null, "Waiting viewer should receive a state update after the active editor teleports out of range.");
        helper.assertTrue(bobUpdate.editable(), "Bob should take over editing after Alice teleports out of range.");
        helper.assertValueEqual(bobUpdate.activeEditorName(), BOB, "handoff editor name after teleport");
        helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should own the editor lock after teleport handoff.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void dimensionChangingEditorHandsOffToWaitingViewer(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
                BOB_READ_ONLY_WHILE_ALICE_EDITS);

        XLNetworking.releaseEditorSessionsForDimensionChange(alice);

        final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(bobUpdate != null, "Waiting viewer should receive a state update after the active editor changes dimension.");
        helper.assertTrue(bobUpdate.editable(), "Bob should take over editing after Alice changes dimension.");
        helper.assertValueEqual(bobUpdate.activeEditorName(), BOB, "handoff editor name after dimension change");
        helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should own the editor lock after dimension-change handoff.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void chunkUnloadReleasesEditorLeaseForWaitingViewer(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);
        helper.assertFalse(XLNetworking.createOpenComputerStatePayload(bob, computer, LEASE_TIMEOUT_TICKS).editable(),
                BOB_READ_ONLY_WHILE_ALICE_EDITS);

        final LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(COMPUTER_A));
        XLNetworking.releaseEditorSessionsForChunkUnload(chunk);

        final ComputerRuntimeStatePayload bobUpdate = XLNetworking.synchronizeComputerSession(bob, computer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(bobUpdate != null, "Waiting viewer should receive a state update after the computer chunk unloads.");
        helper.assertTrue(bobUpdate.editable(), "Bob should take over editing once the unloading chunk drops Alice's lease.");
        helper.assertValueEqual(bobUpdate.activeEditorName(), BOB, "handoff editor name after chunk unload");
        helper.assertTrue(computer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Bob should own the editor lock after chunk-unload cleanup.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void restartedComputerDoesNotRestoreStaleEditorLease(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        final ServerPlayer alice = mockPlayer(helper, ALICE);
        final ServerPlayer bob = mockPlayer(helper, BOB);

        helper.assertTrue(XLNetworking.createOpenComputerStatePayload(alice, computer, LEASE_TIMEOUT_TICKS).editable(),
                FIRST_OPEN_CLAIMS_LOCK);

        final net.minecraft.nbt.CompoundTag persistedState = computer.saveCustomOnly(helper.getLevel().registryAccess());
        XLNetworking.resetTrackedEditorSessions();

        final ComputerBlockEntity restartedComputer = new ComputerBlockEntity(computer.getBlockPos(), computer.getBlockState());
        restartedComputer.setLevel(helper.getLevel());
        restartedComputer.loadCustomOnly(persistedState, helper.getLevel().registryAccess());

        final OpenComputerStatePayload restartOpen = XLNetworking.createOpenComputerStatePayload(bob, restartedComputer, LEASE_TIMEOUT_TICKS);
        helper.assertTrue(restartOpen.editable(), "Bob should claim editing after a server restart instead of inheriting Alice's stale lease.");
        helper.assertValueEqual(restartOpen.activeEditorName(), BOB, "editor name after restart reload");
        helper.assertTrue(restartedComputer.isEditableBy(bob, LEASE_TIMEOUT_TICKS), "Reloaded computer should expose a fresh editor lease after restart.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = SESSION_BATCH)
    public static void restartedComputerAutoStartsWhenOptionIsEnabled(final GameTestHelper helper) {
        helper.setBlock(COMPUTER_A, XLBlocks.COMPUTER.get());

        final ComputerBlockEntity computer = helper.getBlockEntity(COMPUTER_A);
        computer.setScript("screen.print('restart auto-start')");
        computer.setAutoStartOnLoad(true);

        final net.minecraft.nbt.CompoundTag persistedState = computer.saveCustomOnly(helper.getLevel().registryAccess());

        final ComputerBlockEntity restartedComputer = new ComputerBlockEntity(computer.getBlockPos(), computer.getBlockState());
        restartedComputer.setLevel(helper.getLevel());
        restartedComputer.loadCustomOnly(persistedState, helper.getLevel().registryAccess());

        helper.assertTrue(restartedComputer.autoStartOnLoad(), "Reloaded computer should keep the persisted auto-start option.");
        restartedComputer.serverTick();
        helper.assertTrue(restartedComputer.getRuntimeState().running() || !restartedComputer.getRuntimeState().neverExecuted(),
                "Reloaded computer should start the persisted script automatically on the first server tick.");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanProgressPersistsAcrossReload(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_IO_POS, XLBlocks.CRAFTING_IO.get());

        final CraftingIOBlockEntity craftingIo = helper.getBlockEntity(CRAFTING_IO_POS);
        craftingIo.appendPlanStep(0, 0, 4);
        craftingIo.appendPlanStep(1, 0, 2);
        craftingIo.setQueuedPlanReservationMode(QueuedPlanReservationMode.ACTIVE_CYCLE);
        craftingIo.setQueuedPlanCycles(3);
        craftingIo.applyQueuedPlanStepResult(1);

        helper.assertValueEqual(craftingIo.getQueuedPlanCycles(), 3, "queued plan cycles before reload");
        helper.assertValueEqual(craftingIo.getQueuedPlanCycleIndex(), 0, "queued plan cycle before reload");
        helper.assertValueEqual(craftingIo.getQueuedPlanStepIndex(), 0, "queued plan step before reload");
        helper.assertValueEqual(craftingIo.getQueuedPlanRequestedCrafts(), 3, "queued plan remaining crafts before reload");
        helper.assertValueEqual(craftingIo.getQueuedPlanReservationMode().serializedName(), QUEUE_RESERVATION_MODE_ACTIVE_CYCLE, "queued plan reservation mode before reload");
        helper.assertValueEqual(craftingIo.getQueuedPlanJobStatus().serializedName(), JOB_STATUS_RESUMABLE, "queued plan job status before reload");

        final net.minecraft.nbt.CompoundTag persistedState = craftingIo.saveCustomOnly(helper.getLevel().registryAccess());

        final CraftingIOBlockEntity restartedCraftingIo = new CraftingIOBlockEntity(craftingIo.getBlockPos(), craftingIo.getBlockState());
        restartedCraftingIo.setLevel(helper.getLevel());
        restartedCraftingIo.loadCustomOnly(persistedState, helper.getLevel().registryAccess());

        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanCycles(), 3, "queued plan cycles after reload");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanCycleIndex(), 0, "queued plan cycle after reload");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanStepIndex(), 0, "queued plan step after reload");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanRequestedCrafts(), 3, "queued plan remaining crafts after reload");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanReservationMode().serializedName(), QUEUE_RESERVATION_MODE_ACTIVE_CYCLE, "queued plan reservation mode after reload");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanJobStatus().serializedName(), JOB_STATUS_RESUMABLE, "queued plan job status after reload");

        restartedCraftingIo.applyQueuedPlanStepResult(3);
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanStepIndex(), 1, "queued plan should advance to the next step once the partial step completes");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanRequestedCrafts(), 2, "next queued step crafts");

        restartedCraftingIo.applyQueuedPlanStepResult(2);
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanCycles(), 2, "queued plan should consume one full cycle after the last step finishes");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanCycleIndex(), 1, "queued plan cycle index after completing one cycle");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanStepIndex(), 0, "queued plan should restart at the first step for the next cycle");
        helper.assertValueEqual(restartedCraftingIo.getQueuedPlanRequestedCrafts(), 4, "queued plan should restore the configured crafts for the next cycle");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanResetsOnPlanMutation(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_IO_POS, XLBlocks.CRAFTING_IO.get());

        final CraftingIOBlockEntity craftingIo = helper.getBlockEntity(CRAFTING_IO_POS);
        craftingIo.appendPlanStep(0, 0, 2);
        craftingIo.appendPlanStep(1, 0, 3);
        craftingIo.setQueuedPlanCycles(2);
        craftingIo.applyQueuedPlanStepResult(2);

        helper.assertValueEqual(craftingIo.getQueuedPlanCycleIndex(), 0, "queued plan cycle before mutation");
        helper.assertValueEqual(craftingIo.getQueuedPlanStepIndex(), 1, "queued plan step before mutation");
        helper.assertValueEqual(craftingIo.getQueuedPlanRequestedCrafts(), 3, "queued plan crafts before mutation");

        craftingIo.setPlanStep(0, 0, 0, 5);
        helper.assertValueEqual(craftingIo.getQueuedPlanCycles(), 2, "queued plan cycles should stay queued after a plan mutation");
        helper.assertValueEqual(craftingIo.getQueuedPlanCycleIndex(), 0, "queued plan cycle should restart after a plan mutation");
        helper.assertValueEqual(craftingIo.getQueuedPlanStepIndex(), 0, "queued plan step should restart after a plan mutation");
        helper.assertValueEqual(craftingIo.getQueuedPlanRequestedCrafts(), 5, "queued plan should restart against the updated first step");

        craftingIo.clearPlan();
        helper.assertValueEqual(craftingIo.getQueuedPlanCycles(), 0, "queued plan should clear when the plan is removed");
        helper.assertFalse(craftingIo.hasQueuedPlan(), "queued plan should no longer be active after clearing the plan");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void rebuildPlanFromGridDerivesCraftCountsAndPreservesRoutes(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_IO_POS, XLBlocks.CRAFTING_IO.get());

        final CraftingIOBlockEntity craftingIo = helper.getBlockEntity(CRAFTING_IO_POS);
        craftingIo.clearGrid();
        craftingIo.clearPlan();
        craftingIo.clearRoutes();
        craftingIo.setGridSize(CRAFTING_PLAN_GRID_SIZE);
        craftingIo.appendPlanStep(0, 0, 1, SOURCE_ROUTE_NAME, BUFFER_ROUTE_NAME);
        craftingIo.appendPlanStep(4, 0, 1, BUFFER_ROUTE_NAME, SINK_ROUTE_NAME);
        setCraftingGridSlot(craftingIo, 0, 0, OAK_LOG_ITEM_ID, 3);
        setCraftingGridSlot(craftingIo, 4, 0, OAK_PLANKS_ITEM_ID, 2);
        setCraftingGridSlot(craftingIo, 4, 1, OAK_PLANKS_ITEM_ID, 2);

        helper.assertValueEqual(craftingIo.rebuildPlanFromGrid(), 2, "rebuilt plan should still contain both populated windows");
        helper.assertValueEqual(craftingIo.getPlanStepCrafts(0), 3, "rebuilt first step should infer three crafts from the stacked log input");
        helper.assertValueEqual(craftingIo.getPlanStepCrafts(1), 2, "rebuilt second step should infer two crafts from the stacked plank inputs");
        helper.assertValueEqual(craftingIo.getPlanStepInputRoute(0), SOURCE_ROUTE_NAME, "rebuilt plan should preserve the first step input route");
        helper.assertValueEqual(craftingIo.getPlanStepOutputRoute(0), BUFFER_ROUTE_NAME, "rebuilt plan should preserve the first step output route");
        helper.assertValueEqual(craftingIo.getPlanStepInputRoute(1), BUFFER_ROUTE_NAME, "rebuilt plan should preserve the second step input route");
        helper.assertValueEqual(craftingIo.getPlanStepOutputRoute(1), SINK_ROUTE_NAME, "rebuilt plan should preserve the second step output route");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftReservationBlocksWithoutEnoughMaterials(final GameTestHelper helper) {
        final CraftingReservationFixture fixture = createCraftingReservationFixture(helper);
        fixture.craftingIo().setGridSlot(0, OAK_LOG_ITEM_ID, 1);
        fixture.craftingIo().appendPlanStep(0, 0, 2);
        fixture.craftingIo().setQueuedPlanCycles(1);
        fixture.inputChest().setItem(0, new ItemStack(Items.OAK_LOG, 1));

        helper.assertFalse(fixture.craftingIo().canReserveQueuedPlanStep(fixture.craftingCpu(), fixture.inputHandler(), fixture.outputHandler()),
                "Queued plan step should not reserve when the requested logs are missing.");

        final int crafted = fixture.craftingIo().craftReservedQueuedPlanStep(fixture.craftingCpu(), fixture.inputHandler(), fixture.outputHandler());
        helper.assertValueEqual(crafted, 0, "crafted items when inputs are missing");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanRequestedCrafts(), 2, "queued crafts should remain untouched when reservation fails");
        helper.assertValueEqual(fixture.inputChest().getItem(0).getCount(), 1, "input chest should not be consumed on reservation failure");
        helper.assertTrue(fixture.outputChest().getItem(0).isEmpty(), "output chest should stay empty on reservation failure");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftReservationBlocksWithoutEnoughOutputSpace(final GameTestHelper helper) {
        final CraftingReservationFixture fixture = createCraftingReservationFixture(helper);
        fixture.craftingIo().setGridSlot(0, OAK_LOG_ITEM_ID, 1);
        fixture.craftingIo().appendPlanStep(0, 0, 2);
        fixture.craftingIo().setQueuedPlanCycles(1);
        fixture.inputChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        fixture.outputChest().setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
        fillChestRange(fixture.outputChest(), 1, 27, new ItemStack(Items.COBBLESTONE, 64));

        helper.assertFalse(fixture.craftingIo().canReserveQueuedPlanStep(fixture.craftingCpu(), fixture.inputHandler(), fixture.outputHandler()),
                "Queued plan step should wait while the output inventory cannot fit every remaining craft.");

        final int crafted = fixture.craftingIo().craftReservedQueuedPlanStep(fixture.craftingCpu(), fixture.inputHandler(), fixture.outputHandler());
        helper.assertValueEqual(crafted, 0, "crafted items when output space is insufficient");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanRequestedCrafts(), 2, "queued crafts should remain untouched when output reservation fails");
        helper.assertValueEqual(fixture.inputChest().getItem(0).getCount(), 2, "input chest should not be consumed when output reservation fails");
        helper.assertValueEqual(fixture.outputChest().getItem(0).getCount(), 60, "output chest should keep its original fill level when reservation fails");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftReservationCompletesAtomicStepWhenResourcesFit(final GameTestHelper helper) {
        final CraftingReservationFixture fixture = createCraftingReservationFixture(helper);
        fixture.craftingIo().setGridSlot(0, OAK_LOG_ITEM_ID, 1);
        fixture.craftingIo().appendPlanStep(0, 0, 2);
        fixture.craftingIo().setQueuedPlanCycles(1);
        fixture.inputChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));

        helper.assertTrue(fixture.craftingIo().canReserveQueuedPlanStep(fixture.craftingCpu(), fixture.inputHandler(), fixture.outputHandler()),
                "Queued plan step should reserve once all required inputs and outputs fit.");

        final int crafted = fixture.craftingIo().craftReservedQueuedPlanStep(fixture.craftingCpu(), fixture.inputHandler(), fixture.outputHandler());
        helper.assertValueEqual(crafted, 2, "crafted items when reservation succeeds");
        helper.assertFalse(fixture.craftingIo().hasQueuedPlan(), "queued plan should complete after the reserved step finishes");
        helper.assertTrue(fixture.inputChest().getItem(0).isEmpty(), "input chest should consume both logs after successful crafting");
        helper.assertValueEqual(fixture.outputChest().getItem(0).getCount(), 8, "output chest should receive all planks for the reserved step");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanBlocksWithMaterialMissingErrorClass(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 0, "queued plan should not start when the first routed step lacks source material");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "missing-material blocker should produce one snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_BLOCKED, "missing-material snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_MATERIAL_MISSING,
                "missing-material snapshot should expose material_missing");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).stepIndex(), 0, "missing-material snapshot should point at the first plan step");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_BLOCKED, "missing-material job status should be blocked");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftBlockedJobRequiresExplicitResumeAfterInventoryFix(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);

        final int firstCrafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(firstCrafted, 0, "queued plan should block before any craft when source material is missing");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_BLOCKED, "blocked queued plan job status after first attempt");
        helper.assertTrue(fixture.craftingIoDevice().canResumeQueuedPlan(), "blocked queued plan should require an explicit resume before retrying");

        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 1));

        boolean threw = false;
        try {
            fixture.craftingIoDevice().craftQueuedPlan();
        } catch (final IllegalStateException exception) {
            threw = true;
            helper.assertTrue(exception.getMessage().contains("must be resumed or aborted"), "blocked queued plan should explain the required resume/abort action");
        }

        helper.assertTrue(threw, "blocked queued plan should not rerun until it is explicitly resumed");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "retry guard should not append additional plan snapshots");
        helper.assertValueEqual(fixture.sourceChest().getItem(0).getCount(), 1, "source material should remain untouched until the blocked job is resumed");

        helper.assertValueEqual(fixture.craftingIoDevice().resumeQueuedPlan(), JOB_STATUS_RESUMABLE, "resume should rearm the blocked queued plan");
        helper.assertFalse(fixture.craftingIoDevice().canResumeQueuedPlan(), "resumed queued plan should no longer require an explicit resume");

        final int resumedCrafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(resumedCrafted, 3, "resumed queued plan should craft both routed steps after material arrives");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_COMPLETED, "resumed queued plan should complete after the retry succeeds");
        helper.assertFalse(fixture.craftingIo().hasQueuedPlan(), "resumed queued plan should clear after the retry succeeds");
        helper.assertTrue(isChestEmpty(fixture.sourceChest()), "source chest should be consumed after the resumed queued plan succeeds");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 8, "sink chest should contain the finished routed output after resume");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 3, "resume flow should keep the first blocked snapshot and append two completed steps");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftBlockedJobCanAbort(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 0, "queued plan should block before aborting in the test setup");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_BLOCKED, "blocked queued plan job status before abort");
        helper.assertTrue(fixture.craftingIoDevice().canAbortQueuedPlan(), "blocked queued plan should expose abort capability");

        helper.assertTrue(fixture.craftingIoDevice().abortQueuedPlan(), "abort should clear the blocked queued plan");
        helper.assertFalse(fixture.craftingIo().hasQueuedPlan(), "abort should clear the queued plan state");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanJobStatus().serializedName(), JOB_STATUS_IDLE, "aborted queued plan should reset to idle");
        helper.assertFalse(fixture.craftingIoDevice().canAbortQueuedPlan(), "aborted queued plan should no longer expose abort capability");
        helper.assertFalse(fixture.craftingIoDevice().canResumeQueuedPlan(), "aborted queued plan should no longer require resume");
        helper.assertValueEqual(fixture.craftingIoDevice().craftQueuedPlan(), 0, "aborted queued plan should no longer execute any crafts");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanBlocksWithBufferFullErrorClass(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 1));
        fillChestRange(fixture.bufferChest(), 0, fixture.bufferChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 0, "queued plan should not start when the intermediate buffer route is full");
        helper.assertValueEqual(fixture.sourceChest().getItem(0).getCount(), 1, "source material should remain untouched while the buffer route is full");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "buffer-full blocker should produce one snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_BLOCKED, "buffer-full snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_BUFFER_FULL,
                "buffer-full snapshot should expose buffer_full");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).stepIndex(), 0, "buffer-full snapshot should point at the first plan step");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanBlocksBeforeCurrentStepWhenFutureRouteIsFull(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 1));
        fixture.sinkChest().setItem(0, new ItemStack(Items.STICK, 60));
        fillChestRange(fixture.sinkChest(), 1, fixture.sinkChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 0, "queued plan should not start when a later route cannot fit the remaining plan");
        helper.assertValueEqual(fixture.sourceChest().getItem(0).getCount(), 1, "source materials should stay untouched when a later route blocks the plan");
        helper.assertTrue(isChestEmpty(fixture.bufferChest()), "intermediate buffer should stay untouched while the queued plan is blocked");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 60, "full sink inventory should remain unchanged while the queued plan is blocked");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "blocked queued plan should record one blocked step snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_BLOCKED, "blocked snapshot status for route-capacity failure");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_OUTPUT_FULL,
            "blocked snapshot should classify final sink pressure as output_full");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).stepIndex(), 1, "blocked snapshot should point at the downstream plan step");
        helper.assertTrue(fixture.hostApi().planStepSnapshots().get(0).message().contains("step 2"), "blocked snapshot should point at the later blocked plan step");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanBlocksResumedLaterStepWithoutConsumingBufferedItems(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.craftingIo().applyQueuedPlanStepResult(1);
        fixture.bufferChest().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        fixture.sinkChest().setItem(0, new ItemStack(Items.STICK, 60));
        fillChestRange(fixture.sinkChest(), 1, fixture.sinkChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 0, "resumed queued plan should not consume buffered items when the remaining route is blocked");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanStepIndex(), 1, "queued plan should remain on the resumed step while blocked");
        helper.assertValueEqual(fixture.bufferChest().getItem(0).getCount(), 4, "buffered intermediates should stay intact while the resumed step is blocked");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 60, "blocked sink inventory should remain unchanged for resumed steps");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "blocked resumed plan should record one blocked snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).stepIndex(), 1, "blocked resumed plan should report the resumed step index");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_BLOCKED, "blocked snapshot status for resumed route failure");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_OUTPUT_FULL,
            "blocked resumed plan should classify final sink pressure as output_full");
        helper.succeed();
    }

        @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
        public static void queuedCraftPlanFailsWhenTrackedIntermediateDisappears(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.craftingIo().applyQueuedPlanStepResult(1);
        fixture.craftingIo().setTrackedIntermediateExpectation(BUFFER_ROUTE_NAME, OAK_PLANKS_ITEM_ID, 4);

        boolean threw = false;
        try {
            fixture.craftingIoDevice().craftQueuedPlan();
        } catch (final IllegalStateException exception) {
            threw = true;
            helper.assertTrue(exception.getMessage().contains("tracked intermediate item"),
                "tracked-intermediate failure should explain the missing buffered item");
        }

        helper.assertTrue(threw, "queued plan should throw when tracked intermediates disappear before the resumed step");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "tracked-intermediate failure should record one failed snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_FAILED,
            "tracked-intermediate snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_INTERMEDIATE_MISSING,
            "tracked-intermediate snapshot should expose intermediate_missing");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).stepIndex(), 1,
            "tracked-intermediate snapshot should point at the resumed downstream step");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_FAILED,
            "tracked-intermediate job status should be failed");
        helper.assertTrue(fixture.craftingIoDevice().canResumeQueuedPlan(),
            "failed tracked-intermediate job should require an explicit resume");

        final JsonObject queuedPlanState = JsonParser.parseString(fixture.craftingIoDevice().queuedPlanStateJson()).getAsJsonObject();
        final JsonArray trackedIntermediates = queuedPlanState.getAsJsonArray("tracked_intermediates");
        helper.assertValueEqual(queuedPlanState.get("job_status").getAsString(), JOB_STATUS_FAILED,
            "queued plan state should export the failed job status");
        helper.assertValueEqual(queuedPlanState.get("error_class").getAsString(), PLAN_ERROR_INTERMEDIATE_MISSING,
            "queued plan state should export the intermediate_missing error class");
        helper.assertValueEqual(queuedPlanState.get(JSON_ACTION_HINT).getAsString(), ACTION_HINT_RESTORE_INTERMEDIATE,
            "queued plan state should suggest restoring the missing intermediate");
        helper.assertValueEqual(queuedPlanState.get("tracked_intermediate_count").getAsInt(), 4,
            "queued plan state should keep the tracked intermediate total");
        helper.assertValueEqual(trackedIntermediates.size(), 1,
            "queued plan state should export one tracked intermediate entry");
        helper.assertValueEqual(trackedIntermediates.get(0).getAsJsonObject().get("route").getAsString(), BUFFER_ROUTE_NAME,
            "queued plan state should export the tracked intermediate route");
        helper.assertValueEqual(trackedIntermediates.get(0).getAsJsonObject().get("item").getAsString(), OAK_PLANKS_ITEM_ID,
            "queued plan state should export the tracked intermediate item id");
        helper.assertValueEqual(trackedIntermediates.get(0).getAsJsonObject().get("count").getAsInt(), 4,
            "queued plan state should export the tracked intermediate expected count");
        helper.assertTrue(queuedPlanState.get("can_resume").getAsBoolean(),
            "queued plan state should mark the failed tracked-intermediate job as resumable after manual repair");
        helper.succeed();
        }

        @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
        public static void queuedCraftPlanFailsWhenIntermediateRouteContainsForeignItems(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.craftingIo().applyQueuedPlanStepResult(1);
        fixture.craftingIo().setTrackedIntermediateExpectation(BUFFER_ROUTE_NAME, OAK_PLANKS_ITEM_ID, 4);
        fixture.bufferChest().setItem(0, new ItemStack(Items.COBBLESTONE, 1));

        boolean threw = false;
        try {
            fixture.craftingIoDevice().craftQueuedPlan();
        } catch (final IllegalStateException exception) {
            threw = true;
            helper.assertTrue(exception.getMessage().contains("unexpected item"),
                "contaminated intermediate failure should explain the foreign buffer item");
        }

        helper.assertTrue(threw, "queued plan should throw when a tracked intermediate route contains foreign items");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "contaminated intermediate failure should record one failed snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_FAILED,
            "contaminated intermediate snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_INTERMEDIATE_CONTAMINATED,
            "contaminated intermediate snapshot should expose intermediate_contaminated");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_FAILED,
            "contaminated intermediate job status should be failed");

        final JsonObject queuedPlanState = JsonParser.parseString(fixture.craftingIoDevice().queuedPlanStateJson()).getAsJsonObject();
        helper.assertValueEqual(queuedPlanState.get("error_class").getAsString(), PLAN_ERROR_INTERMEDIATE_CONTAMINATED,
            "queued plan state should export the intermediate_contaminated error class");
        helper.assertValueEqual(queuedPlanState.get(JSON_ACTION_HINT).getAsString(), ACTION_HINT_CLEAN_BUFFER,
            "queued plan state should suggest cleaning the contaminated buffer route");
        helper.succeed();
        }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanCompletesAcrossIntermediateRoutesWhenPlanFits(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 1));

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 3, "queued plan should execute both routed steps when the full remaining plan fits");
        helper.assertFalse(fixture.craftingIo().hasQueuedPlan(), "queued plan should complete when every routed step fits");
        helper.assertTrue(isChestEmpty(fixture.sourceChest()), "source chest should consume the reserved material after successful plan execution");
        helper.assertTrue(isChestEmpty(fixture.bufferChest()), "buffer chest should end empty after the downstream step consumes all intermediates");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 8, "sink chest should receive the full routed output");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 2, "successful routed plan should record both completed steps");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_COMPLETED, "first routed step status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(1).status(), PLAN_STATUS_COMPLETED, "second routed step status");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanBlocksBeforeStartWhenLaterQueueCycleCannotFit(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper, 2);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        fixture.sinkChest().setItem(0, new ItemStack(Items.STICK, 56));
        fillChestRange(fixture.sinkChest(), 1, fixture.sinkChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanReservationMode(), QUEUE_RESERVATION_MODE_FULL_QUEUE, "default queued plan reservation mode");

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 0, "queued plan should not start when only a later queue cycle runs out of output capacity");
        helper.assertValueEqual(fixture.sourceChest().getItem(0).getCount(), 2, "source materials should stay untouched when a later queue cycle blocks reservation");
        helper.assertTrue(isChestEmpty(fixture.bufferChest()), "buffer chest should remain untouched when a later queue cycle blocks reservation");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "later-cycle reservation failure should produce one blocked snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_BLOCKED, "later-cycle blocked snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).reservationMode(), QUEUE_RESERVATION_MODE_FULL_QUEUE,
            "later-cycle blocked snapshot should expose full-queue reservation mode");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_OUTPUT_FULL,
                "later-cycle blocked snapshot should classify sink pressure as output_full");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).cycleIndex(), 1, "later-cycle blocked snapshot should point at the second queue cycle");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).stepIndex(), 1, "later-cycle blocked snapshot should point at the second plan step");
        helper.assertTrue(fixture.hostApi().planStepSnapshots().get(0).message().contains("cycle 2 step 2"), "blocked snapshot should point at the later queue cycle and step");
        helper.succeed();
    }

        @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
        public static void queuedCraftPlanReportsReservablePrefixWhenLaterCycleBlocks(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper, 2);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        fixture.sinkChest().setItem(0, new ItemStack(Items.STICK, 56));
        fillChestRange(fixture.sinkChest(), 1, fixture.sinkChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        helper.assertValueEqual(fixture.craftingIoDevice().craftQueuedPlan(), 0,
            "full-queue reservation should still block before executing when only a later cycle cannot fit");

        final JsonObject queuedPlanState = JsonParser.parseString(fixture.craftingIoDevice().queuedPlanStateJson()).getAsJsonObject();
        helper.assertValueEqual(queuedPlanState.get("job_status").getAsString(), JOB_STATUS_BLOCKED,
            "queued plan state should keep the blocked job status for later-cycle pressure");
        helper.assertValueEqual(queuedPlanState.get(JSON_ACTION_HINT).getAsString(), ACTION_HINT_SWITCH_TO_ACTIVE_CYCLE,
            "queued plan state should suggest active-cycle execution when the current cycle is fully reservable");
        helper.assertValueEqual(queuedPlanState.get("reservable_cycles").getAsInt(), 1,
            "queued plan state should report one fully reservable cycle before the later block");
        helper.assertValueEqual(queuedPlanState.get("reservable_steps").getAsInt(), 2,
            "queued plan state should report both steps of the current cycle as reservable");
        helper.assertValueEqual(queuedPlanState.get("blocked_cycle_index").getAsInt(), 1,
            "queued plan state should point at the later blocked cycle");
        helper.assertValueEqual(queuedPlanState.get("blocked_step_index").getAsInt(), 1,
            "queued plan state should point at the later blocked step");
        helper.succeed();
        }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanCanSwitchReservationModeToActiveCycle(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper, 2);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        fixture.sinkChest().setItem(0, new ItemStack(Items.STICK, 56));
        fillChestRange(fixture.sinkChest(), 1, fixture.sinkChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        helper.assertValueEqual(fixture.craftingIoDevice().setQueuedPlanReservationMode(QUEUE_RESERVATION_MODE_ACTIVE_CYCLE),
                QUEUE_RESERVATION_MODE_ACTIVE_CYCLE,
                "queued plan reservation mode setter result");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanReservationMode(), QUEUE_RESERVATION_MODE_ACTIVE_CYCLE,
                "queued plan reservation mode getter result");

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 3, "active-cycle reservation should still execute the current cycle before a later cycle blocks");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanCycles(), 1, "one queue cycle should remain after the current cycle completes under active-cycle reservation");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanCycleIndex(), 1, "queued plan should advance into the second cycle before it blocks");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanStepIndex(), 0, "blocked second cycle should stop before consuming its first step");
        helper.assertValueEqual(fixture.sourceChest().getItem(0).getCount(), 1, "second cycle source material should remain untouched when the later cycle blocks");
        helper.assertTrue(isChestEmpty(fixture.bufferChest()), "buffer chest should end empty after the completed first cycle");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 64, "sink chest should contain the first cycle output before the second cycle blocks");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 3, "active-cycle reservation should record two completed steps and one blocked step");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_COMPLETED, "active-cycle first snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(1).status(), PLAN_STATUS_COMPLETED, "active-cycle second snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(2).status(), PLAN_STATUS_BLOCKED, "active-cycle blocked snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).reservationMode(), QUEUE_RESERVATION_MODE_ACTIVE_CYCLE,
            "active-cycle completed snapshot should expose active-cycle reservation mode");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(2).reservationMode(), QUEUE_RESERVATION_MODE_ACTIVE_CYCLE,
            "active-cycle blocked snapshot should expose active-cycle reservation mode");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(2).errorClass(), PLAN_ERROR_OUTPUT_FULL,
                "active-cycle blocked snapshot should classify sink pressure as output_full");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(2).cycleIndex(), 1, "active-cycle blocked snapshot should point at the second queue cycle");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(2).stepIndex(), 1, "active-cycle blocked snapshot should point at the second plan step");
        helper.assertTrue(fixture.hostApi().planStepSnapshots().get(2).message().contains("cycle 2 step 2"), "active-cycle blocked snapshot should point at the later blocked cycle step");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftReservationModeChangeRearmsBlockedJob(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper, 2);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        fixture.sinkChest().setItem(0, new ItemStack(Items.STICK, 56));
        fillChestRange(fixture.sinkChest(), 1, fixture.sinkChest().getContainerSize(), new ItemStack(Items.COBBLESTONE, 64));

        final int blockedCrafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(blockedCrafted, 0, "full-queue reservation should initially block the queued plan");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_BLOCKED, "initial full-queue run should leave the job blocked");
        helper.assertTrue(fixture.craftingIoDevice().canResumeQueuedPlan(), "blocked full-queue job should initially require resume");

        helper.assertValueEqual(fixture.craftingIoDevice().setQueuedPlanReservationMode(QUEUE_RESERVATION_MODE_ACTIVE_CYCLE),
                QUEUE_RESERVATION_MODE_ACTIVE_CYCLE,
                "reservation mode setter should switch to active-cycle");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_RESUMABLE, "mode change should rearm the blocked queued plan");
        helper.assertFalse(fixture.craftingIoDevice().canResumeQueuedPlan(), "mode change should remove the explicit-resume requirement");

        final int retriedCrafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(retriedCrafted, 3, "active-cycle retry should complete the first queue cycle after the mode change");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanCycles(), 1, "one queue cycle should remain after the rearmed active-cycle retry");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanCycleIndex(), 1, "rearmed active-cycle retry should advance into the second cycle");
        helper.assertValueEqual(fixture.craftingIo().getQueuedPlanStepIndex(), 0, "rearmed active-cycle retry should stop before consuming the second-cycle first step");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_BLOCKED, "rearmed active-cycle retry should block again on the later cycle");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 64, "active-cycle retry should fill the sink with the first cycle output");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 4, "mode-change retry should keep the first blocked snapshot and add three more from the retried run");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanFailsWithRouteMissingErrorClass(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.craftingIo().clearRoutes();

        boolean threw = false;
        try {
            fixture.craftingIoDevice().craftQueuedPlan();
        } catch (final IllegalStateException exception) {
            threw = true;
        }

        helper.assertTrue(threw, "queued plan should throw when a required named route is missing");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "route-missing failure should record one failed snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_FAILED, "route-missing snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_ROUTE_MISSING,
                "route-missing snapshot should expose route_missing");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_FAILED, "route-missing job status should be failed");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanFailsWithRecipeInvalidErrorClass(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.craftingIo().clearGrid();
        setCraftingGridSlot(fixture.craftingIo(), 0, 0, OAK_LOG_ITEM_ID, 1);
        setCraftingGridSlot(fixture.craftingIo(), 1, 0, COBBLESTONE_ITEM_ID, 1);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 1));

        boolean threw = false;
        try {
            fixture.craftingIoDevice().craftQueuedPlan();
        } catch (final IllegalStateException exception) {
            threw = true;
        }

        helper.assertTrue(threw, "queued plan should throw when the current plan step has no valid recipe");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "invalid-recipe failure should record one failed snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_FAILED, "invalid-recipe snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_RECIPE_INVALID,
                "invalid-recipe snapshot should expose recipe_invalid");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_FAILED, "invalid-recipe job status should be failed");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanFailsWithCpuUnavailableErrorClass(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper);
        fixture.craftingIo().setLinkedCpuEndpoint("missing_cpu");

        boolean threw = false;
        try {
            fixture.craftingIoDevice().craftQueuedPlan();
        } catch (final IllegalStateException exception) {
            threw = true;
        }

        helper.assertTrue(threw, "queued plan should throw when the linked crafting cpu is unavailable");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 1, "cpu-unavailable failure should record one failed snapshot");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).status(), PLAN_STATUS_FAILED, "cpu-unavailable snapshot status");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(0).errorClass(), PLAN_ERROR_CPU_UNAVAILABLE,
                "cpu-unavailable snapshot should expose cpu_unavailable");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_FAILED, "cpu-unavailable job status should be failed");
        helper.succeed();
    }

    @GameTest(templateNamespace = XLLogicMod.MOD_ID, template = TEMPLATE, batch = CRAFTING_BATCH)
    public static void queuedCraftPlanCompletesAcrossMultipleQueueCyclesWhenFullyReserved(final GameTestHelper helper) {
        final QueuedCraftPlanRouteFixture fixture = createQueuedCraftPlanRouteFixture(helper, 2);
        fixture.sourceChest().setItem(0, new ItemStack(Items.OAK_LOG, 2));

        final int crafted = fixture.craftingIoDevice().craftQueuedPlan();
        helper.assertValueEqual(crafted, 6, "queued plan should execute every step across both reserved queue cycles");
        helper.assertFalse(fixture.craftingIo().hasQueuedPlan(), "queued plan should clear after all reserved queue cycles complete");
        helper.assertTrue(isChestEmpty(fixture.sourceChest()), "source chest should consume both logs after successful multi-cycle execution");
        helper.assertTrue(isChestEmpty(fixture.bufferChest()), "buffer chest should end empty after successful multi-cycle execution");
        helper.assertValueEqual(fixture.sinkChest().getItem(0).getCount(), 16, "sink chest should receive the combined output of both queue cycles");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().size(), 4, "successful multi-cycle reservation should record all four completed steps");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(3).cycleIndex(), 1, "last snapshot should belong to the second queue cycle");
        helper.assertValueEqual(fixture.hostApi().planStepSnapshots().get(3).status(), PLAN_STATUS_COMPLETED, "last snapshot status for successful multi-cycle execution");
        helper.assertValueEqual(fixture.craftingIoDevice().queuedPlanJobStatus(), JOB_STATUS_COMPLETED, "successful queued plan job status should be completed");
        helper.succeed();
    }

    private static BlockState coloredCable(final int channel) {
        return XLBlocks.coloredRedstoneCable(channel).get().defaultBlockState().setValue(ColoredRedstoneCableBlock.CHANNEL, channel);
    }

    private static BlockState screen(final Direction facing) {
        return XLBlocks.SCREEN.get().defaultBlockState().setValue(ScreenBlock.FACING, facing);
    }

    private static void assertScreenState(final GameTestHelper helper, final BlockPos screenPos, final int expectedSpanX,
                                          final int expectedSpanY, final boolean expectedController) {
        final ScreenBlockEntity screen = helper.getBlockEntity(screenPos);
        helper.assertTrue(screen.hasLinkedComputer(), "Expected screen @ " + screenPos + " to be linked to a computer.");
        helper.assertValueEqual(screen.getSpanX(), expectedSpanX, "spanX for screen @ " + screenPos);
        helper.assertValueEqual(screen.getSpanY(), expectedSpanY, "spanY for screen @ " + screenPos);
        helper.assertValueEqual(screen.isController(), expectedController, "controller flag for screen @ " + screenPos);
        helper.assertTrue(screen.hasLoadedControllerScreen(), "Expected controller screen to stay loaded for screen @ " + screenPos + ".");
    }

    private static CraftingReservationFixture createCraftingReservationFixture(final GameTestHelper helper) {
        helper.setBlock(CRAFTING_IO_POS, XLBlocks.CRAFTING_IO.get());
        helper.setBlock(CRAFTING_CPU_POS, XLBlocks.CRAFTING_CPU.get());
        helper.setBlock(CRAFTING_INPUT_CHEST_POS, Blocks.CHEST.defaultBlockState());
        helper.setBlock(CRAFTING_OUTPUT_CHEST_POS, Blocks.CHEST.defaultBlockState());

        final CraftingIOBlockEntity craftingIo = helper.getBlockEntity(CRAFTING_IO_POS);
        final CraftingCPUBlockEntity craftingCpu = helper.getBlockEntity(CRAFTING_CPU_POS);
        final ChestBlockEntity inputChest = helper.getBlockEntity(CRAFTING_INPUT_CHEST_POS);
        final ChestBlockEntity outputChest = helper.getBlockEntity(CRAFTING_OUTPUT_CHEST_POS);
        clearChest(inputChest);
        clearChest(outputChest);

        final IItemHandler inputHandler = XLItemFluidAccess.getAdjacentItemHandler(helper.getLevel(), craftingCpu.getBlockPos(), Direction.WEST);
        final IItemHandler outputHandler = XLItemFluidAccess.getAdjacentItemHandler(helper.getLevel(), craftingCpu.getBlockPos(), Direction.EAST);
        helper.assertTrue(inputHandler != null, "Crafting CPU should expose an input handler towards the west chest.");
        helper.assertTrue(outputHandler != null, "Crafting CPU should expose an output handler towards the east chest.");

        return new CraftingReservationFixture(craftingIo, craftingCpu, inputChest, outputChest, inputHandler, outputHandler);
    }

    private static QueuedCraftPlanRouteFixture createQueuedCraftPlanRouteFixture(final GameTestHelper helper) {
        return createQueuedCraftPlanRouteFixture(helper, 1);
    }

    private static QueuedCraftPlanRouteFixture createQueuedCraftPlanRouteFixture(final GameTestHelper helper, final int queuedCycles) {
        helper.setBlock(CRAFTING_IO_POS, XLBlocks.CRAFTING_IO.get());
        helper.setBlock(CRAFTING_CPU_POS, XLBlocks.CRAFTING_CPU.get());
        helper.setBlock(CRAFTING_SOURCE_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_BUFFER_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_SINK_IO_POS, XLBlocks.MATERIAL_IO.get());
        helper.setBlock(CRAFTING_SOURCE_CHEST_POS, Blocks.CHEST.defaultBlockState());
        helper.setBlock(CRAFTING_BUFFER_CHEST_POS, Blocks.CHEST.defaultBlockState());
        helper.setBlock(CRAFTING_SINK_CHEST_POS, Blocks.CHEST.defaultBlockState());

        final CraftingIOBlockEntity craftingIo = helper.getBlockEntity(CRAFTING_IO_POS);
        final CraftingCPUBlockEntity craftingCpu = helper.getBlockEntity(CRAFTING_CPU_POS);
        final MaterialIOBlockEntity sourceIo = helper.getBlockEntity(CRAFTING_SOURCE_IO_POS);
        final MaterialIOBlockEntity bufferIo = helper.getBlockEntity(CRAFTING_BUFFER_IO_POS);
        final MaterialIOBlockEntity sinkIo = helper.getBlockEntity(CRAFTING_SINK_IO_POS);
        final ChestBlockEntity sourceChest = helper.getBlockEntity(CRAFTING_SOURCE_CHEST_POS);
        final ChestBlockEntity bufferChest = helper.getBlockEntity(CRAFTING_BUFFER_CHEST_POS);
        final ChestBlockEntity sinkChest = helper.getBlockEntity(CRAFTING_SINK_CHEST_POS);

        clearChest(sourceChest);
        clearChest(bufferChest);
        clearChest(sinkChest);
        sourceIo.setMode(MaterialIOMode.ITEMS_ONLY);
        bufferIo.setMode(MaterialIOMode.ITEMS_ONLY);
        sinkIo.setMode(MaterialIOMode.ITEMS_ONLY);

        craftingIo.clearPlan();
        craftingIo.clearRoutes();
        craftingIo.clearGrid();
        craftingIo.setGridSize(CRAFTING_PLAN_GRID_SIZE);
        craftingIo.setLinkedCpuEndpoint(CRAFTING_CPU_API_NAME);
        craftingIo.setRoute(SOURCE_ROUTE_NAME, SOURCE_IO_API_NAME, Direction.SOUTH);
        craftingIo.setRoute(BUFFER_ROUTE_NAME, BUFFER_IO_API_NAME, Direction.SOUTH);
        craftingIo.setRoute(SINK_ROUTE_NAME, SINK_IO_API_NAME, Direction.SOUTH);
        setCraftingGridSlot(craftingIo, 0, 0, OAK_LOG_ITEM_ID, 1);
        setCraftingGridSlot(craftingIo, 4, 0, OAK_PLANKS_ITEM_ID, 1);
        setCraftingGridSlot(craftingIo, 4, 1, OAK_PLANKS_ITEM_ID, 1);
        craftingIo.appendPlanStep(0, 0, 1, SOURCE_ROUTE_NAME, BUFFER_ROUTE_NAME);
        craftingIo.appendPlanStep(4, 0, 2, BUFFER_ROUTE_NAME, SINK_ROUTE_NAME);
        craftingIo.setQueuedPlanCycles(queuedCycles);

        final PythonHostApi hostApi = PythonHostApi.server(helper.getLevel(), "crafting-test", craftingIo.getBlockPos(), List.of(
            localBinding(CRAFTING_IO_API_NAME, CRAFTING_IO_API_NAME, craftingIo.getBlockPos()),
            localBinding(CRAFTING_CPU_API_NAME, CRAFTING_CPU_API_NAME, craftingCpu.getBlockPos()),
            localBinding(SOURCE_IO_API_NAME, MATERIAL_IO_TYPE, sourceIo.getBlockPos()),
            localBinding(BUFFER_IO_API_NAME, MATERIAL_IO_TYPE, bufferIo.getBlockPos()),
            localBinding(SINK_IO_API_NAME, MATERIAL_IO_TYPE, sinkIo.getBlockPos())
        ));
        final PythonHostApi.DeviceBridge craftingIoDevice = hostApi.getDevice(CRAFTING_IO_API_NAME);
        helper.assertTrue(craftingIoDevice != null, "Crafting I/O device should be reachable for routed queued-plan tests.");
        return new QueuedCraftPlanRouteFixture(craftingIo, sourceChest, bufferChest, sinkChest, hostApi, craftingIoDevice);
    }

    private static void setCraftingGridSlot(final CraftingIOBlockEntity craftingIo,
                                            final int x,
                                            final int y,
                                            final String itemId,
                                            final int count) {
        craftingIo.setGridSlot(y * CRAFTING_PLAN_GRID_SIZE + x, itemId, count);
    }

    private static void clearChest(final ChestBlockEntity chest) {
        fillChestRange(chest, 0, chest.getContainerSize(), ItemStack.EMPTY);
    }

    private static boolean isChestEmpty(final ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (!chest.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void fillChestRange(final ChestBlockEntity chest, final int startSlot, final int endSlotExclusive, final ItemStack stack) {
        for (int slot = startSlot; slot < endSlotExclusive; slot++) {
            chest.setItem(slot, stack.copy());
        }
        chest.setChanged();
    }

    private static PythonPeripheralBinding localBinding(final String apiName, final String type, final BlockPos pos) {
        return new PythonPeripheralBinding(apiName, apiName, type, pos, pos.toShortString(), 0, "local", "", 0,
                "", "", "", "", "", "");
    }

    private static boolean hasBlockerType(final XLRedstoneBusResolver.ChannelFlowDebugSnapshot flow, final String blockerType) {
        return flow.blockers().stream().anyMatch(blocker -> blockerType.equals(blocker.blockerType()));
    }

    private static ServerPlayer mockPlayer(final GameTestHelper helper, final String name) {
        final UUID playerId = UUID.nameUUIDFromBytes(("xllogic:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, name));
    }

    private record CraftingReservationFixture(CraftingIOBlockEntity craftingIo,
                                              CraftingCPUBlockEntity craftingCpu,
                                              ChestBlockEntity inputChest,
                                              ChestBlockEntity outputChest,
                                              IItemHandler inputHandler,
                                              IItemHandler outputHandler) {
    }

    private record QueuedCraftPlanRouteFixture(CraftingIOBlockEntity craftingIo,
                                               ChestBlockEntity sourceChest,
                                               ChestBlockEntity bufferChest,
                                               ChestBlockEntity sinkChest,
                                               PythonHostApi hostApi,
                                               PythonHostApi.DeviceBridge craftingIoDevice) {
    }
}