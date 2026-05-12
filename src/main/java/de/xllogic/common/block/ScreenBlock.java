package de.xllogic.common.block;

import com.mojang.serialization.MapCodec;
import de.xllogic.common.blockentity.ScreenBlockEntity;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.ScreenMultiblockManager;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.network.XLNetworkResolver;
import de.xllogic.common.screen.ScreenLayoutMetrics;
import de.xllogic.runtime.ComputerOutputEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ScreenBlock extends AbstractDeviceBlock {
    public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty JOIN_CCW = BooleanProperty.create("join_ccw");
    public static final BooleanProperty JOIN_CW = BooleanProperty.create("join_cw");
    public static final BooleanProperty JOIN_UP = BooleanProperty.create("join_up");
    public static final BooleanProperty JOIN_DOWN = BooleanProperty.create("join_down");
    private static final String TABLE_COLUMN_LIMIT_MESSAGE = "Focused table columns are already at their limit.";
    private static final String TABLE_ROW_LIMIT_MESSAGE = "Focused table rows are already at their limit.";
    private static final String CARD_FIELD_LIMIT_MESSAGE = "Focused card fields are already at their limit.";
    private static final float HIT_TEST_SCREEN_SCALE = ScreenLayoutMetrics.SCREEN_SCALE;
    private static final float HIT_TEST_PANEL_LIFT_PER_EXTRA_ROW = 0.04F;
    private static final float HIT_TEST_LINE_HEIGHT = 9.0F;
    private static final float HIT_TEST_CONTENT_MARGIN = ScreenLayoutMetrics.CONTENT_MARGIN_UNITS;
    private static final float HIT_TEST_ENTRY_GAP = 4.0F;
    private static final float HIT_TEST_PAGE_FOOTER_HEIGHT = 12.0F;
    private static final float HIT_TEST_CENTERED_TEXT_ADVANCE = HIT_TEST_LINE_HEIGHT + 1.0F;
    private static final float HIT_TEST_STATUS_SUMMARY_HEIGHT = HIT_TEST_LINE_HEIGHT + 8.0F;
    private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public ScreenBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(JOIN_CCW, false)
                .setValue(JOIN_CW, false)
                .setValue(JOIN_UP, false)
                .setValue(JOIN_DOWN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean skipRendering(final BlockState state, final BlockState adjacentBlockState, final Direction side) {
        if (adjacentBlockState.is(this) && adjacentBlockState.getValue(FACING) == state.getValue(FACING) && side.getAxis() != state.getValue(FACING).getAxis()) {
            final BooleanProperty ownJoin = joinPropertyForSide(state, side);
            final BooleanProperty adjacentJoin = joinPropertyForSide(adjacentBlockState, side.getOpposite());
            if (ownJoin != null && adjacentJoin != null && state.getValue(ownJoin) && adjacentBlockState.getValue(adjacentJoin)) {
                return true;
            }
        }
        return super.skipRendering(state, adjacentBlockState, side);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            ScreenMultiblockManager.rebuildAround(level, pos);
            this.refreshNearbyComputers(level, pos);
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            ScreenMultiblockManager.rebuildAround(level, pos);
            this.refreshNearbyComputers(level, pos);
        }
    }

    private void refreshNearbyComputers(final Level level, final BlockPos origin) {
        final Set<BlockPos> computerPositions = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = origin.relative(direction);
            if (level.getBlockEntity(neighborPos) instanceof ScreenBlockEntity neighborScreen && neighborScreen.hasLinkedComputer()) {
                computerPositions.add(neighborScreen.getLinkedComputerPos());
            }
        }
        computerPositions.addAll(XLNetworkResolver.resolveComputers(level, origin));

        for (final BlockPos computerPos : computerPositions) {
            if (level.isLoaded(computerPos) && level.getBlockEntity(computerPos) instanceof ComputerBlockEntity computer) {
                computer.refreshConnectedEndpoints();
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, JOIN_CCW, JOIN_CW, JOIN_UP, JOIN_DOWN);
    }

    public static BlockState withPanelJoins(final BlockState state, final boolean joinCounterClockWise, final boolean joinClockWise,
                                            final boolean joinUp, final boolean joinDown) {
        if (!state.hasProperty(JOIN_CCW) || !state.hasProperty(JOIN_CW) || !state.hasProperty(JOIN_UP) || !state.hasProperty(JOIN_DOWN)) {
            return state;
        }
        return state.setValue(JOIN_CCW, joinCounterClockWise)
                .setValue(JOIN_CW, joinClockWise)
                .setValue(JOIN_UP, joinUp)
                .setValue(JOIN_DOWN, joinDown);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected boolean isCollisionShapeFullBlock(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ScreenBlockEntity screen)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        final InteractionResult namingResult = this.tryOpenNamingScreen(state, player, hitResult, screen);
        if (namingResult != null) {
            return namingResult;
        }

        final ScreenBlockEntity interactionScreen = screen.resolveLoadedControllerScreen();

        if (interactionScreen == null) {
            if (player.isShiftKeyDown()) {
                this.sendDiscoveryHint(player);
            }
            player.sendSystemMessage(Component.literal("This screen is part of " + screen.multiblockSummary() + " and waits for controller " + screen.getControllerPos().toShortString() + ". Move closer or load that chunk first."));
            return InteractionResult.SUCCESS;
        }

        if (!interactionScreen.hasLinkedComputer()) {
            if (player.isShiftKeyDown()) {
                this.sendDiscoveryHint(player);
            }
            player.sendSystemMessage(Component.literal(screen.describeState()));
            return InteractionResult.SUCCESS;
        }

        final ComputerBlockEntity linkedComputer = interactionScreen.resolveLinkedComputer();
        if (linkedComputer == null) {
            if (player.isShiftKeyDown()) {
                this.sendDiscoveryHint(player);
            }
            player.sendSystemMessage(Component.literal(screen.describeState()));
            return InteractionResult.SUCCESS;
        }

        if (hitResult.getDirection() == state.getValue(FACING)) {
            return this.handleFrontFaceUse(state, pos, hitResult, screen, interactionScreen, linkedComputer, player);
        }

        if (player.isShiftKeyDown()) {
            this.sendDiscoveryHint(player);
        }
        player.sendSystemMessage(Component.literal(screen.describeState()));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult tryOpenNamingScreen(final BlockState state, final Player player, final BlockHitResult hitResult, final ScreenBlockEntity screen) {
        if (!player.isShiftKeyDown() || hitResult.getDirection() == state.getValue(FACING)) {
            return null;
        }

        final ScreenBlockEntity namingTarget = screen.resolveControllerScreen();
        if (player instanceof ServerPlayer serverPlayer) {
            XLNetworking.openEndpointNamingScreen(serverPlayer, namingTarget);
        }
        return InteractionResult.SUCCESS;
    }

    private void sendDiscoveryHint(final Player player) {
        player.sendSystemMessage(Component.literal("Screen assignments come from discovery mode over connected network cables. Each cable segment may only contain one computer unless segments are separated by XLAPI blocks."));
    }

    private InteractionResult handleFrontFaceUse(final BlockState state, final BlockPos pos, final BlockHitResult hitResult,
                                                 final ScreenBlockEntity clickedScreen, final ScreenBlockEntity controllerScreen,
                                                 final ComputerBlockEntity linkedComputer, final Player player) {
        final FrontFaceZone zone = frontFaceZone(state, pos, hitResult, clickedScreen, controllerScreen);
        final InteractionResult focusedViewportResult = this.handleFocusedViewportUse(controllerScreen, linkedComputer, player, zone);
        if (focusedViewportResult != null) {
            return focusedViewportResult;
        }
        return switch (zone.band()) {
            case TOP -> this.handlePageBand(controllerScreen, linkedComputer, player, zone);
            case MIDDLE -> this.handleFocusBand(controllerScreen, linkedComputer, player, zone);
            case BOTTOM -> this.handleScrollBand(controllerScreen, linkedComputer, player, zone);
        };
    }

    private InteractionResult handlePageBand(final ScreenBlockEntity screen, final ComputerBlockEntity linkedComputer, final Player player,
                                             final FrontFaceZone zone) {
        final List<ComputerOutputEntry> focusableEntries = focusableEntries(screen, linkedComputer);
        if (screen.hasFocusedOutput() && !focusableEntries.isEmpty()) {
            return this.handleFocusedJumpBand(screen, focusableEntries, player, zone.section(), player.isShiftKeyDown());
        }

        if (screen.hasFocusedOutput()) {
            screen.clearFocusedOutput();
        }

        if (player.isShiftKeyDown()) {
            if (screen.retreatPageCursor()) {
                player.sendSystemMessage(Component.literal("Screen moved toward newer output. The active page is shown in the panel footer."));
            } else {
                player.sendSystemMessage(Component.literal("Screen is already on its newest page."));
            }
            return InteractionResult.SUCCESS;
        }

        screen.advancePageCursor();
        player.sendSystemMessage(Component.literal("Screen moved toward older output. The active page is shown in the panel footer."));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleFocusBand(final ScreenBlockEntity screen, final ComputerBlockEntity linkedComputer, final Player player, final FrontFaceZone zone) {
        final List<ComputerOutputEntry> focusableEntries = focusableEntries(screen, linkedComputer);
        if (screen.hasFocusedOutput()) {
            if (focusableEntries.isEmpty()) {
                screen.clearFocusedOutput();
                player.sendSystemMessage(Component.literal("No detailed table or card output remains available to keep focused."));
                return InteractionResult.SUCCESS;
            }

            if (player.isShiftKeyDown()) {
                screen.clearFocusedOutput();
                player.sendSystemMessage(Component.literal("Detailed focus cleared. Click a visible table or card to focus it again."));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.literal("Focused output uses directional clicks inside the visible detail area. Shift-right-click here to leave focus."));
            return InteractionResult.SUCCESS;
        }

        final List<ComputerOutputEntry> outputEntries = screen.resolveDisplayOutputEntries(linkedComputer.getRuntimeState());
        if (focusableEntries.isEmpty()) {
            player.sendSystemMessage(Component.literal("No detailed table or card output is available to focus."));
            return InteractionResult.SUCCESS;
        }

        final FocusHit focusHit = resolveVisibleFocusHit(screen, outputEntries, zone);
        if (focusHit == null) {
            player.sendSystemMessage(Component.literal("No detailed table or card is under this part of the visible screen."));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusHit.focusCursor(), focusHit.fieldOffset(), focusHit.rowOffset(), focusHit.columnOffset());
        this.sendFocusMessage(player, focusableEntries, focusHit.focusCursor());
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleScrollBand(final ScreenBlockEntity screen, final ComputerBlockEntity linkedComputer, final Player player, final FrontFaceZone zone) {
        final List<ComputerOutputEntry> focusableEntries = focusableEntries(screen, linkedComputer);
        if (!screen.hasFocusedOutput() || focusableEntries.isEmpty()) {
            player.sendSystemMessage(Component.literal("No detailed output is focused. Use the middle screen zone to focus a table or card first."));
            return InteractionResult.SUCCESS;
        }

        final int focusedIndex = Math.min(screen.getFocusEntryCursor(), focusableEntries.size() - 1);
        final ComputerOutputEntry focusedEntry = focusableEntries.get(focusedIndex);
        final FocusViewport viewport = focusViewport(screen, focusedEntry, focusedIndex);
        final int delta = player.isShiftKeyDown() ? -1 : 1;

        if (focusedEntry.tableKind() && zone.section().isOuter()) {
            final int before = viewport.columnOffset();
            final int target = clamp(before + delta, 0, viewport.maxColumnOffset());
            if (target == before) {
                player.sendSystemMessage(Component.literal(TABLE_COLUMN_LIMIT_MESSAGE));
                return InteractionResult.SUCCESS;
            }
            screen.focusOutput(focusedIndex, viewport.fieldOffset(), viewport.rowOffset(), target);
            this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
            return InteractionResult.SUCCESS;
        }

        if (focusedEntry.tableKind()) {
            final int before = viewport.rowOffset();
            final int target = clamp(before + delta, 0, viewport.maxRowOffset());
            if (target == before) {
                player.sendSystemMessage(Component.literal(TABLE_ROW_LIMIT_MESSAGE));
                return InteractionResult.SUCCESS;
            }
            screen.focusOutput(focusedIndex, viewport.fieldOffset(), target, viewport.columnOffset());
            this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
            return InteractionResult.SUCCESS;
        }

        final int before = viewport.fieldOffset();
        final int target = clamp(before + delta, 0, viewport.maxFieldOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(CARD_FIELD_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, target, viewport.rowOffset(), viewport.columnOffset());
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleFocusedJumpBand(final ScreenBlockEntity screen, final List<ComputerOutputEntry> focusableEntries,
                                                    final Player player, final HorizontalSection section, final boolean reverseCenter) {
        final int focusedIndex = Math.min(screen.getFocusEntryCursor(), focusableEntries.size() - 1);
        final ComputerOutputEntry focusedEntry = focusableEntries.get(focusedIndex);
        final FocusViewport viewport = focusViewport(screen, focusedEntry, focusedIndex);

        if (focusedEntry.tableKind()) {
            if (section.isOuter()) {
                final int horizontalDirection = section == HorizontalSection.LEFT ? -1 : 1;
                return this.handleFocusedTableColumnJump(screen, player, focusedEntry, focusedIndex, viewport, horizontalDirection);
            }

            final int verticalDirection = reverseCenter ? -1 : 1;
            return this.handleFocusedTableRowJump(screen, player, focusedEntry, focusedIndex, viewport, verticalDirection);
        }

        final int direction = focusBandDirection(section, reverseCenter);
        final int step = Math.max(1, viewport.fieldVisibleCount());
        final int before = viewport.fieldOffset();
        final int target = clamp(before + direction * step, 0, viewport.maxFieldOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(CARD_FIELD_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, target, viewport.rowOffset(), viewport.columnOffset());
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleFocusedTableColumnJump(final ScreenBlockEntity screen, final Player player, final ComputerOutputEntry focusedEntry,
                                                           final int focusedIndex, final FocusViewport viewport, final int direction) {
        final int step = Math.max(1, viewport.columnVisibleCount());
        final int before = viewport.columnOffset();
        final int target = clamp(before + direction * step, 0, viewport.maxColumnOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(TABLE_COLUMN_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, viewport.fieldOffset(), viewport.rowOffset(), target);
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleFocusedTableRowJump(final ScreenBlockEntity screen, final Player player, final ComputerOutputEntry focusedEntry,
                                                        final int focusedIndex, final FocusViewport viewport, final int direction) {
        final int step = Math.max(1, viewport.rowVisibleCount());
        final int before = viewport.rowOffset();
        final int target = clamp(before + direction * step, 0, viewport.maxRowOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(TABLE_ROW_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, viewport.fieldOffset(), target, viewport.columnOffset());
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleFocusedViewportUse(final ScreenBlockEntity screen, final ComputerBlockEntity linkedComputer, final Player player,
                                                       final FrontFaceZone zone) {
        if (!screen.hasFocusedOutput()) {
            return null;
        }

        final List<ComputerOutputEntry> focusableEntries = focusableEntries(screen, linkedComputer);
        if (focusableEntries.isEmpty()) {
            screen.clearFocusedOutput();
            player.sendSystemMessage(Component.literal("No detailed table or card output remains available to keep focused."));
            return InteractionResult.SUCCESS;
        }

        final int focusedIndex = Math.min(screen.getFocusEntryCursor(), focusableEntries.size() - 1);
        final ComputerOutputEntry focusedEntry = focusableEntries.get(focusedIndex);
        final FocusViewport viewport = focusViewport(screen, focusedEntry, focusedIndex);
        final FocusedViewportIntent intent = resolveFocusedViewportIntent(screen, viewport, zone, player.isShiftKeyDown());
        if (intent == null) {
            return null;
        }

        return this.applyFocusedViewportIntent(screen, player, viewport, focusedEntry, focusedIndex, intent);
    }

    private InteractionResult applyFocusedViewportIntent(final ScreenBlockEntity screen, final Player player, final FocusViewport viewport,
                                                         final ComputerOutputEntry focusedEntry, final int focusedIndex, final FocusedViewportIntent intent) {
        if (intent.clearFocus()) {
            screen.clearFocusedOutput();
            player.sendSystemMessage(Component.literal("Detailed focus cleared. Click a visible table or card to focus it again."));
            return InteractionResult.SUCCESS;
        }

        if (intent.retargetHit() != null) {
            final FocusHit hit = intent.retargetHit();
            if (!screen.focusOutput(hit.focusCursor(), hit.fieldOffset(), hit.rowOffset(), hit.columnOffset())) {
                player.sendSystemMessage(Component.literal("Focused output already targets this visible area."));
                return InteractionResult.SUCCESS;
            }

            this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
            return InteractionResult.SUCCESS;
        }

        return switch (intent.axis()) {
            case FIELD -> this.applyFocusedFieldStep(screen, player, focusedEntry, focusedIndex, viewport, intent.delta());
            case ROW -> this.applyFocusedRowStep(screen, player, focusedEntry, focusedIndex, viewport, intent.delta());
            case COLUMN -> this.applyFocusedColumnStep(screen, player, focusedEntry, focusedIndex, viewport, intent.delta());
        };
    }

    private InteractionResult applyFocusedFieldStep(final ScreenBlockEntity screen, final Player player, final ComputerOutputEntry focusedEntry,
                                                    final int focusedIndex, final FocusViewport viewport, final int delta) {
        final int before = viewport.fieldOffset();
        final int target = clamp(before + delta, 0, viewport.maxFieldOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(CARD_FIELD_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, target, viewport.rowOffset(), viewport.columnOffset());
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult applyFocusedRowStep(final ScreenBlockEntity screen, final Player player, final ComputerOutputEntry focusedEntry,
                                                  final int focusedIndex, final FocusViewport viewport, final int delta) {
        final int before = viewport.rowOffset();
        final int target = clamp(before + delta, 0, viewport.maxRowOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(TABLE_ROW_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, viewport.fieldOffset(), target, viewport.columnOffset());
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult applyFocusedColumnStep(final ScreenBlockEntity screen, final Player player, final ComputerOutputEntry focusedEntry,
                                                     final int focusedIndex, final FocusViewport viewport, final int delta) {
        final int before = viewport.columnOffset();
        final int target = clamp(before + delta, 0, viewport.maxColumnOffset());
        if (target == before) {
            player.sendSystemMessage(Component.literal(TABLE_COLUMN_LIMIT_MESSAGE));
            return InteractionResult.SUCCESS;
        }

        screen.focusOutput(focusedIndex, viewport.fieldOffset(), viewport.rowOffset(), target);
        this.sendFocusedViewportMessage(player, focusViewport(screen, focusedEntry, focusedIndex));
        return InteractionResult.SUCCESS;
    }

    private void sendFocusMessage(final Player player, final List<ComputerOutputEntry> focusableEntries, final int focusCursor) {
        if (focusableEntries.isEmpty()) {
            return;
        }
        final int clampedCursor = clamp(focusCursor, 0, focusableEntries.size() - 1);
        final ComputerOutputEntry entry = focusableEntries.get(clampedCursor);
        final String summary = entry.summaryLine();
        final String suffix = summary.isBlank() ? entry.displayLabel() : summary;
        player.sendSystemMessage(Component.literal("Focused " + entry.displayLabel() + " " + (clampedCursor + 1) + "/" + focusableEntries.size() + ": " + suffix));
    }

    private void sendFocusedViewportMessage(final Player player, final FocusViewport viewport) {
        if (viewport.entry().tableKind()) {
            if (viewport.rowCount() <= 0) {
                player.sendSystemMessage(Component.literal("Focused table columns now start at " + (viewport.columnOffset() + 1) + "."));
            } else {
                player.sendSystemMessage(Component.literal("Focused table now starts at row " + (viewport.rowOffset() + 1) + ", column " + (viewport.columnOffset() + 1) + "."));
            }
            return;
        }

        player.sendSystemMessage(Component.literal("Focused card now starts at field " + (viewport.fieldOffset() + 1) + "."));
    }

    private static List<ComputerOutputEntry> focusableEntries(final ScreenBlockEntity screen, final ComputerBlockEntity linkedComputer) {
        return focusableEntries(screen.resolveDisplayOutputEntries(linkedComputer.getRuntimeState()));
    }

    private static List<ComputerOutputEntry> focusableEntries(final List<ComputerOutputEntry> outputEntries) {
        final ArrayList<ComputerOutputEntry> focusableEntries = new ArrayList<>();
        for (int index = outputEntries.size() - 1; index >= 0; index--) {
            final ComputerOutputEntry entry = outputEntries.get(index);
            if (isFocusableEntry(entry)) {
                focusableEntries.add(entry);
            }
        }
        return List.copyOf(focusableEntries);
    }

    private static FocusHit resolveVisibleFocusHit(final ScreenBlockEntity screen, final List<ComputerOutputEntry> outputEntries, final FrontFaceZone zone) {
        final InteractionLayout layout = interactionLayout(screen);
        final Float panelX = layout.panelX(zone.horizontalRatio(), screen);
        final Float panelY = layout.panelY(zone.verticalRatio(), screen);
        if (panelX == null || panelY == null) {
            return null;
        }

        final PlannedPage page = selectPlannedPage(outputEntries, screen, layout.availableHeight());
        if (page.slices().isEmpty()) {
            return null;
        }

        float y = layout.outputTop();
        for (final PlannedSlice slice : page.slices()) {
            final float bottom = y + slice.height();
            if (panelY >= y && panelY <= bottom) {
                return slice.focusHit(panelX, panelY, y, layout.contentLeft(), layout.contentWidth());
            }
            y = bottom + HIT_TEST_ENTRY_GAP;
        }
        return null;
    }

    private static FocusedViewportIntent resolveFocusedViewportIntent(final ScreenBlockEntity screen, final FocusViewport viewport,
                                                                     final FrontFaceZone zone, final boolean clearRequested) {
        final FocusedViewportPoint point = resolveFocusedViewportPoint(screen, viewport, zone);
        if (point == null) {
            return null;
        }

        if (clearRequested) {
            return FocusedViewportIntent.clear();
        }

        final float absX = Math.abs(point.normalizedX());
        final float absY = Math.abs(point.normalizedY());
        final float magnitude = Math.max(absX, absY);
        if (magnitude <= 0.2F) {
            return retargetViewportIntent(viewport, point);
        }

        final boolean horizontalDominant = absX > absY;
        final boolean coarseStep = magnitude >= 0.72F;
        if (viewport.entry().tableKind()) {
            return tableViewportIntent(viewport, point, horizontalDominant, coarseStep);
        }

        return fieldViewportIntent(viewport, point, horizontalDominant, coarseStep);
    }

    private static FocusedViewportIntent retargetViewportIntent(final FocusViewport viewport, final FocusedViewportPoint point) {
        final FocusHit focusHit = viewport.slice().focusHit(point.panelX(), point.panelY(), point.sliceTop(), point.contentLeft(), point.contentWidth());
        return focusHit == null ? null : FocusedViewportIntent.retarget(focusHit);
    }

    private static FocusedViewportIntent tableViewportIntent(final FocusViewport viewport, final FocusedViewportPoint point,
                                                             final boolean horizontalDominant, final boolean coarseStep) {
        if (horizontalDominant) {
            return FocusedViewportIntent.delta(FocusedViewportAxis.COLUMN, signedViewportStep(point.normalizedX(), coarseStep ? viewport.columnVisibleCount() : 1));
        }
        return FocusedViewportIntent.delta(FocusedViewportAxis.ROW, signedViewportStep(point.normalizedY(), coarseStep ? viewport.rowVisibleCount() : 1));
    }

    private static FocusedViewportIntent fieldViewportIntent(final FocusViewport viewport, final FocusedViewportPoint point,
                                                             final boolean horizontalDominant, final boolean coarseStep) {
        final float signedPrimary = horizontalDominant ? point.normalizedX() : point.normalizedY();
        return FocusedViewportIntent.delta(FocusedViewportAxis.FIELD, signedViewportStep(signedPrimary, coarseStep ? viewport.fieldVisibleCount() : 1));
    }

    private static FocusedViewportPoint resolveFocusedViewportPoint(final ScreenBlockEntity screen, final FocusViewport viewport, final FrontFaceZone zone) {
        final InteractionLayout layout = interactionLayout(screen);
        final Float panelX = layout.panelX(zone.horizontalRatio(), screen);
        final Float panelY = layout.panelY(zone.verticalRatio(), screen);
        if (panelX == null || panelY == null) {
            return null;
        }

        final float top = layout.outputTop();
        final float bottom = top + viewport.height();
        final float left = layout.contentLeft();
        final float right = left + layout.contentWidth();
        if (panelX < left || panelX > right || panelY < top || panelY > bottom) {
            return null;
        }

        final float normalizedX = (float) ((((panelX - left) / Math.max(1.0F, layout.contentWidth())) * 2.0D) - 1.0D);
        final float normalizedY = (float) ((((panelY - top) / Math.max(1.0F, viewport.height())) * 2.0D) - 1.0D);
        return new FocusedViewportPoint(panelX, panelY, left, layout.contentWidth(), top, normalizedX, normalizedY);
    }

    private static FocusViewport focusViewport(final ScreenBlockEntity screen, final ComputerOutputEntry entry, final int focusCursor) {
        final float availableHeight = focusedAvailableHeight(screen);
        if (entry.tableKind()) {
            return focusedTableViewport(screen, entry, focusCursor, availableHeight);
        }
        if (entry.keyValueKind() || entry.planCardKind()) {
            return focusedFieldViewport(screen, entry, focusCursor, availableHeight);
        }
        return new FocusViewport(entry, focusCursor, 0, 0, 0, 0, 0, 0, 0, 0, 0, HIT_TEST_LINE_HEIGHT + 6.0F);
    }

    private static FocusViewport focusedFieldViewport(final ScreenBlockEntity screen, final ComputerOutputEntry entry, final int focusCursor,
                                                     final float availableHeight) {
        final List<ComputerOutputEntry.OutputField> fields = entry.fields();
        if (fields.isEmpty()) {
            return new FocusViewport(entry, focusCursor, 0, 0, 0, 0, 0, 0, 0, 0, 0, measureFieldSliceHeight(entry, 0, false));
        }

        int visibleCount = 1;
        for (int candidateCount = 1; candidateCount <= fields.size(); candidateCount++) {
            final int candidateStart = clamp(screen.getFocusFieldOffset(), 0, Math.max(0, fields.size() - candidateCount));
            final boolean candidateContinued = candidateStart > 0 || candidateStart + candidateCount < fields.size();
            if (measureFieldSliceHeight(entry, candidateCount, candidateContinued) <= availableHeight) {
                visibleCount = candidateCount;
            } else {
                break;
            }
        }

        final int start = clamp(screen.getFocusFieldOffset(), 0, Math.max(0, fields.size() - visibleCount));
        final boolean continued = start > 0 || start + visibleCount < fields.size();
        return new FocusViewport(entry, focusCursor, start, visibleCount, fields.size(), 0, 0, 0, 0, 0, 0, measureFieldSliceHeight(entry, visibleCount, continued));
    }

    private static FocusViewport focusedTableViewport(final ScreenBlockEntity screen, final ComputerOutputEntry entry, final int focusCursor,
                                                     final float availableHeight) {
        final ComputerOutputEntry.TableData tableData = entry.tableData();
        final List<String> columns = tableColumns(tableData);
        final int rowCount = tableData.rows().size();
        final int visibleColumns = Math.max(1, Math.min(columns.size(), focusedTableColumnLimit(screen)));
        final int columnStart = clamp(screen.getFocusColumnOffset(), 0, Math.max(0, columns.size() - visibleColumns));

        if (rowCount == 0) {
            final boolean continued = columnStart > 0 || columnStart + visibleColumns < columns.size();
            return new FocusViewport(entry, focusCursor, 0, 0, 0, 0, 0, 0, columnStart, visibleColumns, columns.size(),
                    measureTableSliceHeight(entry, 0, continued));
        }

        int visibleRows = 1;
        for (int candidateRows = 1; candidateRows <= rowCount; candidateRows++) {
            final int candidateStart = clamp(screen.getFocusRowOffset(), 0, Math.max(0, rowCount - candidateRows));
            final boolean candidateContinued = columnStart > 0 || columnStart + visibleColumns < columns.size()
                    || candidateStart > 0 || candidateStart + candidateRows < rowCount;
            if (measureTableSliceHeight(entry, candidateRows, candidateContinued) <= availableHeight) {
                visibleRows = candidateRows;
            } else {
                break;
            }
        }

        final int rowStart = clamp(screen.getFocusRowOffset(), 0, Math.max(0, rowCount - visibleRows));
        final boolean continued = columnStart > 0 || columnStart + visibleColumns < columns.size()
                || rowStart > 0 || rowStart + visibleRows < rowCount;
        return new FocusViewport(entry, focusCursor, 0, 0, 0, rowStart, visibleRows, rowCount, columnStart, visibleColumns, columns.size(),
                measureTableSliceHeight(entry, visibleRows, continued));
    }

    private static float focusedAvailableHeight(final ScreenBlockEntity screen) {
        return Math.max(1.0F, interactionLayout(screen).availableHeight() - HIT_TEST_PAGE_FOOTER_HEIGHT);
    }

    private static PlannedPage selectPlannedPage(final List<ComputerOutputEntry> outputEntries, final ScreenBlockEntity screen, final float availableHeight) {
        final PlannedPage initialPage = buildPlannedPage(outputEntries, screen, availableHeight);
        if (!initialPage.multiPage()) {
            return initialPage;
        }

        return buildPlannedPage(outputEntries, screen, Math.max(1.0F, availableHeight - HIT_TEST_PAGE_FOOTER_HEIGHT));
    }

    private static PlannedPage buildPlannedPage(final List<ComputerOutputEntry> outputEntries, final ScreenBlockEntity screen, final float availableHeight) {
        final List<PlannedSlice> slices = expandPlannedSlices(outputEntries, screen);
        if (slices.isEmpty() || availableHeight <= 0.0F) {
            return PlannedPage.empty();
        }

        final ArrayList<List<PlannedSlice>> pages = new ArrayList<>();
        final ArrayList<PlannedSlice> currentPage = new ArrayList<>();
        float usedHeight = 0.0F;
        for (final PlannedSlice slice : slices) {
            final float nextHeight = currentPage.isEmpty() ? slice.height() : usedHeight + HIT_TEST_ENTRY_GAP + slice.height();
            if (!currentPage.isEmpty() && nextHeight > availableHeight) {
                pages.add(reversePlanned(currentPage));
                currentPage.clear();
                usedHeight = 0.0F;
            }

            currentPage.add(slice);
            usedHeight = currentPage.size() == 1 ? slice.height() : usedHeight + HIT_TEST_ENTRY_GAP + slice.height();
        }

        if (!currentPage.isEmpty()) {
            pages.add(reversePlanned(currentPage));
        }

        if (pages.isEmpty()) {
            return PlannedPage.empty();
        }

        final int effectivePageIndex = Math.min(screen.getPageCursor(), pages.size() - 1);
        return new PlannedPage(pages.get(effectivePageIndex), effectivePageIndex, pages.size());
    }

    private static List<PlannedSlice> expandPlannedSlices(final List<ComputerOutputEntry> outputEntries, final ScreenBlockEntity screen) {
        if (outputEntries.isEmpty()) {
            return List.of();
        }

        final ArrayList<PlannedSlice> slices = new ArrayList<>();
        int focusCursor = 0;
        for (int index = outputEntries.size() - 1; index >= 0; index--) {
            final ComputerOutputEntry entry = outputEntries.get(index);
            final int entryFocusCursor = isFocusableEntry(entry) ? focusCursor++ : -1;
            slices.addAll(expandPlannedEntry(entry, screen, entryFocusCursor));
        }
        return List.copyOf(slices);
    }

    private static List<PlannedSlice> expandPlannedEntry(final ComputerOutputEntry entry, final ScreenBlockEntity screen, final int focusCursor) {
        if (entry.tableKind()) {
            return expandPlannedTableSlices(entry, screen, focusCursor);
        }
        if (entry.keyValueKind() || entry.planCardKind()) {
            return expandPlannedFieldSlices(entry, screen, focusCursor);
        }
        return List.of(PlannedSlice.line(entry));
    }

    private static List<PlannedSlice> expandPlannedFieldSlices(final ComputerOutputEntry entry, final ScreenBlockEntity screen, final int focusCursor) {
        final List<ComputerOutputEntry.OutputField> fields = entry.fields();
        final int pageSize = Math.max(1, fieldLimit(screen));
        if (fields.isEmpty()) {
            return List.of(PlannedSlice.field(entry, focusCursor, 0, 0, measureFieldSliceHeight(entry, 0, false)));
        }

        final ArrayList<PlannedSlice> slices = new ArrayList<>();
        for (int start = 0; start < fields.size(); start += pageSize) {
            final int end = Math.min(fields.size(), start + pageSize);
            final boolean continued = start > 0 || end < fields.size();
            slices.add(PlannedSlice.field(entry, focusCursor, start, end - start, measureFieldSliceHeight(entry, end - start, continued)));
        }
        return List.copyOf(slices);
    }

    private static List<PlannedSlice> expandPlannedTableSlices(final ComputerOutputEntry entry, final ScreenBlockEntity screen, final int focusCursor) {
        final ComputerOutputEntry.TableData tableData = entry.tableData();
        final List<String> columns = tableColumns(tableData);
        final int rowCount = tableData.rows().size();
        final int columnPageSize = Math.max(1, tableColumnLimit(screen));
        final int rowPageSize = Math.max(1, tableRowLimit(screen));
        final ArrayList<PlannedSlice> slices = new ArrayList<>();

        for (int columnStart = 0; columnStart < columns.size(); columnStart += columnPageSize) {
            final int columnEnd = Math.min(columns.size(), columnStart + columnPageSize);
            if (rowCount == 0) {
                final boolean continued = columnStart > 0 || columnEnd < columns.size();
                slices.add(PlannedSlice.table(entry, focusCursor, 0, 0, columnStart, columnEnd - columnStart, measureTableSliceHeight(entry, 0, continued)));
                continue;
            }

            for (int rowStart = 0; rowStart < rowCount; rowStart += rowPageSize) {
                final int rowEnd = Math.min(rowCount, rowStart + rowPageSize);
                final boolean continued = columnStart > 0 || columnEnd < columns.size() || rowStart > 0 || rowEnd < rowCount;
                slices.add(PlannedSlice.table(entry, focusCursor, rowStart, rowEnd - rowStart, columnStart, columnEnd - columnStart, measureTableSliceHeight(entry, rowEnd - rowStart, continued)));
            }
        }

        return List.copyOf(slices);
    }

    private static List<String> tableColumns(final ComputerOutputEntry.TableData tableData) {
        if (!tableData.columns().isEmpty()) {
            return tableData.columns();
        }

        int width = 0;
        for (final List<String> row : tableData.rows()) {
            width = Math.max(width, row.size());
        }

        final int normalizedWidth = Math.max(1, width);
        final ArrayList<String> columns = new ArrayList<>(normalizedWidth);
        for (int index = 0; index < normalizedWidth; index++) {
            columns.add(normalizedWidth == 1 ? "Value" : "Value " + (index + 1));
        }
        return List.copyOf(columns);
    }

    private static float measureFieldSliceHeight(final ComputerOutputEntry entry, final int visibleCount, final boolean continued) {
        float height = HIT_TEST_LINE_HEIGHT + 8.0F;
        if (!entry.text().isBlank()) {
            height += HIT_TEST_LINE_HEIGHT + 2.0F;
        }
        height += Math.max(0, visibleCount) * (HIT_TEST_LINE_HEIGHT + 1.0F);
        if (continued) {
            height += HIT_TEST_LINE_HEIGHT + 1.0F;
        }
        return Math.max(height + 4.0F, HIT_TEST_LINE_HEIGHT + 10.0F);
    }

    private static float measureTableSliceHeight(final ComputerOutputEntry entry, final int visibleRows, final boolean continued) {
        float height = HIT_TEST_LINE_HEIGHT + 8.0F;
        if (!entry.text().isBlank()) {
            height += HIT_TEST_LINE_HEIGHT + 2.0F;
        }
        height += HIT_TEST_LINE_HEIGHT + 3.0F;
        height += Math.max(0, visibleRows) * (HIT_TEST_LINE_HEIGHT + 1.0F);
        if (continued) {
            height += HIT_TEST_LINE_HEIGHT + 1.0F;
        }
        return Math.max(height + 4.0F, HIT_TEST_LINE_HEIGHT + 12.0F);
    }

    private static List<PlannedSlice> reversePlanned(final List<PlannedSlice> slices) {
        final ArrayList<PlannedSlice> reversed = new ArrayList<>(slices);
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static InteractionLayout interactionLayout(final ScreenBlockEntity screen) {
        final int lineBudget = lineBudget(screen);
        final float surfaceWidth = ScreenLayoutMetrics.surfaceWidthUnits(screen.getSpanX());
        final float surfaceHeight = lineBudget * (HIT_TEST_LINE_HEIGHT + 2.0F) + 8.0F;
        final float contentHeight = lineBudget * (HIT_TEST_LINE_HEIGHT + 2.0F) - 2.0F;
        final float surfaceLeft = -surfaceWidth / 2.0F;
        final float surfaceTop = -contentHeight / 2.0F - 4.0F;
        final float surfaceBottom = surfaceTop + surfaceHeight;
        final int headerLines = screen.isSoloScreen() ? 2 : 3;
        final float outputTop = surfaceTop + HIT_TEST_CONTENT_MARGIN + headerLines * HIT_TEST_CENTERED_TEXT_ADVANCE + 2.0F + HIT_TEST_STATUS_SUMMARY_HEIGHT + HIT_TEST_ENTRY_GAP;
        final float availableHeight = surfaceBottom - outputTop - HIT_TEST_CONTENT_MARGIN;
        return new InteractionLayout(surfaceLeft, surfaceTop, surfaceWidth, surfaceHeight, outputTop, availableHeight);
    }

    private static int lineBudget(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.lineBudget(screen.getSpanY());
    }

    private static int fieldLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.fieldLimit(screen.getSpanY());
    }

    private static int tableRowLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.tableRowLimit(screen.getSpanY());
    }

    private static int tableColumnLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.tableColumnLimit(screen.getSpanX());
    }

    private static int focusedTableColumnLimit(final ScreenBlockEntity screen) {
        return ScreenLayoutMetrics.focusedTableColumnLimit(screen.getSpanX());
    }

    private static boolean isFocusableEntry(final ComputerOutputEntry entry) {
        return entry != null && (entry.tableKind() || entry.keyValueKind() || entry.planCardKind());
    }

    private static FrontFaceZone frontFaceZone(final BlockState state, final BlockPos pos, final BlockHitResult hitResult,
                                               final ScreenBlockEntity clickedScreen, final ScreenBlockEntity controllerScreen) {
        final Vec3 localHit = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        final Direction facing = state.getValue(FACING);
        final double localHorizontal = switch (facing) {
            case NORTH -> 1.0D - localHit.x;
            case SOUTH -> localHit.x;
            case WEST -> localHit.z;
            case EAST -> 1.0D - localHit.z;
            default -> 0.5D;
        };
        final Direction horizontalDirection = facing.getCounterClockWise();
        final BlockPos controllerPos = controllerScreen.getControllerPos();
        final BlockPos clickedPos = clickedScreen.getBlockPos();
        final int horizontalBlockOffset = distanceAlong(controllerPos, clickedPos, horizontalDirection);
        final int verticalBlockOffset = clickedPos.getY() - controllerPos.getY();
        final double horizontal = clamp01((horizontalBlockOffset + localHorizontal) / Math.max(1.0D, controllerScreen.getSpanX()));
        final double vertical = clamp01((verticalBlockOffset + localHit.y) / Math.max(1.0D, controllerScreen.getSpanY()));
        return new FrontFaceZone(verticalBand(vertical), horizontalSection(horizontal), horizontal, vertical);
    }

    private static int distanceAlong(final BlockPos origin, final BlockPos target, final Direction direction) {
        final BlockPos delta = target.subtract(origin);
        return delta.getX() * direction.getStepX() + delta.getY() * direction.getStepY() + delta.getZ() * direction.getStepZ();
    }

    private static double clamp01(final double value) {
        return Math.max(0.0D, Math.min(0.999999D, value));
    }

    private static VerticalBand verticalBand(final double vertical) {
        if (vertical >= 0.72D) {
            return VerticalBand.TOP;
        }
        if (vertical >= 0.38D) {
            return VerticalBand.MIDDLE;
        }
        return VerticalBand.BOTTOM;
    }

    private static HorizontalSection horizontalSection(final double horizontal) {
        if (horizontal <= 0.33D) {
            return HorizontalSection.LEFT;
        }
        if (horizontal >= 0.67D) {
            return HorizontalSection.RIGHT;
        }
        return HorizontalSection.CENTER;
    }

    private static int clamp(final int value, final int minValue, final int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private static int signedViewportStep(final float signedDirection, final int step) {
        return (signedDirection < 0.0F ? -1 : 1) * Math.max(1, step);
    }

    private static int focusBandDirection(final HorizontalSection section, final boolean reverseCenter) {
        if (section == HorizontalSection.LEFT) {
            return -1;
        }
        if (section == HorizontalSection.RIGHT) {
            return 1;
        }
        return reverseCenter ? -1 : 1;
    }

    private static BooleanProperty joinPropertyForSide(final BlockState state, final Direction side) {
        final Direction facing = state.getValue(FACING);
        if (side == Direction.UP) {
            return JOIN_UP;
        }
        if (side == Direction.DOWN) {
            return JOIN_DOWN;
        }
        if (side == facing.getCounterClockWise()) {
            return JOIN_CCW;
        }
        if (side == facing.getClockWise()) {
            return JOIN_CW;
        }
        return null;
    }

    private static VoxelShape shapeFor(final BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    private enum VerticalBand {
        TOP,
        MIDDLE,
        BOTTOM
    }

    private enum HorizontalSection {
        LEFT,
        CENTER,
        RIGHT;

        private boolean isOuter() {
            return this != CENTER;
        }
    }

    private record FrontFaceZone(VerticalBand band, HorizontalSection section, double horizontalRatio, double verticalRatio) {
    }

    private record FocusHit(int focusCursor, int fieldOffset, int rowOffset, int columnOffset) {
    }

    private enum FocusedViewportAxis {
        FIELD,
        ROW,
        COLUMN
    }

    private record FocusedViewportIntent(FocusedViewportAxis axis, int delta, boolean clearFocus, FocusHit retargetHit) {
        private static FocusedViewportIntent clear() {
            return new FocusedViewportIntent(FocusedViewportAxis.FIELD, 0, true, null);
        }

        private static FocusedViewportIntent retarget(final FocusHit focusHit) {
            return new FocusedViewportIntent(FocusedViewportAxis.FIELD, 0, false, focusHit);
        }

        private static FocusedViewportIntent delta(final FocusedViewportAxis axis, final int delta) {
            return new FocusedViewportIntent(axis, delta, false, null);
        }
    }

    private record FocusedViewportPoint(float panelX, float panelY, float contentLeft, float contentWidth, float sliceTop,
                                        float normalizedX, float normalizedY) {
    }

    private record FocusViewport(ComputerOutputEntry entry, int focusCursor, int fieldOffset, int fieldVisibleCount, int fieldCount,
                                 int rowOffset, int rowVisibleCount, int rowCount, int columnOffset, int columnVisibleCount,
                                 int columnCount, float height) {
        private PlannedSlice slice() {
            if (this.entry.tableKind()) {
                return PlannedSlice.table(this.entry, this.focusCursor, this.rowOffset, this.rowVisibleCount, this.columnOffset, this.columnVisibleCount, this.height);
            }
            if (this.entry.keyValueKind() || this.entry.planCardKind()) {
                return PlannedSlice.field(this.entry, this.focusCursor, this.fieldOffset, this.fieldVisibleCount, this.height);
            }
            return PlannedSlice.line(this.entry);
        }

        private int maxFieldOffset() {
            return Math.max(0, this.fieldCount - Math.max(1, this.fieldVisibleCount));
        }

        private int maxRowOffset() {
            return Math.max(0, this.rowCount - Math.max(1, this.rowVisibleCount));
        }

        private int maxColumnOffset() {
            return Math.max(0, this.columnCount - Math.max(1, this.columnVisibleCount));
        }
    }

    private record PlannedPage(List<PlannedSlice> slices, int pageIndex, int totalPages) {
        private static PlannedPage empty() {
            return new PlannedPage(List.of(), 0, 0);
        }

        private boolean multiPage() {
            return this.totalPages > 1;
        }
    }

    private record PlannedSlice(ComputerOutputEntry entry, int focusCursor, int fieldOffset, int fieldVisibleCount, int rowOffset, int rowVisibleCount,
                                int columnOffset, int columnVisibleCount, float height) {
        private static PlannedSlice line(final ComputerOutputEntry entry) {
            return new PlannedSlice(entry, -1, 0, 0, 0, 0, 0, 0, HIT_TEST_LINE_HEIGHT + 6.0F);
        }

        private static PlannedSlice field(final ComputerOutputEntry entry, final int focusCursor, final int fieldOffset, final int fieldVisibleCount, final float height) {
            return new PlannedSlice(entry, focusCursor, fieldOffset, fieldVisibleCount, 0, 0, 0, 0, height);
        }

        private static PlannedSlice table(final ComputerOutputEntry entry, final int focusCursor, final int rowOffset, final int rowVisibleCount,
                                          final int columnOffset, final int columnVisibleCount, final float height) {
            return new PlannedSlice(entry, focusCursor, 0, 0, rowOffset, rowVisibleCount, columnOffset, columnVisibleCount, height);
        }

        private FocusHit focusHit(final float panelX, final float panelY, final float sliceTop, final float contentLeft, final float contentWidth) {
            if (this.focusCursor < 0) {
                return null;
            }
            if (this.entry.tableKind()) {
                return this.tableFocusHit(panelX, panelY, sliceTop, contentLeft, contentWidth);
            }
            if (this.entry.keyValueKind() || this.entry.planCardKind()) {
                return this.fieldFocusHit(panelY, sliceTop);
            }
            return new FocusHit(this.focusCursor, this.fieldOffset, this.rowOffset, this.columnOffset);
        }

        private FocusHit fieldFocusHit(final float panelY, final float sliceTop) {
            int targetFieldOffset = this.fieldOffset;
            if (this.fieldVisibleCount > 0) {
                final float fieldStart = sliceTop + 4.0F + HIT_TEST_LINE_HEIGHT + 3.0F + (this.entry.text().isBlank() ? 0.0F : HIT_TEST_LINE_HEIGHT + 2.0F);
                final float fieldBottom = fieldStart + this.fieldVisibleCount * (HIT_TEST_LINE_HEIGHT + 1.0F);
                if (panelY >= fieldStart && panelY < fieldBottom) {
                    final int fieldIndex = clamp((int) ((panelY - fieldStart) / (HIT_TEST_LINE_HEIGHT + 1.0F)), 0, this.fieldVisibleCount - 1);
                    targetFieldOffset = this.fieldOffset + fieldIndex;
                }
            }
            return new FocusHit(this.focusCursor, targetFieldOffset, 0, 0);
        }

        private FocusHit tableFocusHit(final float panelX, final float panelY, final float sliceTop, final float contentLeft, final float contentWidth) {
            int targetRowOffset = this.rowOffset;
            int targetColumnOffset = this.columnOffset;
            final float tableHeaderTop = sliceTop + 4.0F + HIT_TEST_LINE_HEIGHT + 3.0F + (this.entry.text().isBlank() ? 0.0F : HIT_TEST_LINE_HEIGHT + 2.0F);
            final float tableHeaderBottom = tableHeaderTop + HIT_TEST_LINE_HEIGHT + 2.0F;
            final float rowTop = tableHeaderTop + HIT_TEST_LINE_HEIGHT + 3.0F;
            final float rowBottom = rowTop + this.rowVisibleCount * (HIT_TEST_LINE_HEIGHT + 1.0F);
            final float columnWidth = Math.max(ScreenLayoutMetrics.TABLE_MIN_COLUMN_WIDTH_UNITS,
                    (contentWidth - ScreenLayoutMetrics.TABLE_COLUMN_PADDING_UNITS) / Math.max(1, this.columnVisibleCount));
            final float columnLeft = contentLeft + 4.0F;
            final float columnRight = columnLeft + this.columnVisibleCount * columnWidth;

            if (this.columnVisibleCount > 0 && panelX >= columnLeft && panelX < columnRight && panelY >= tableHeaderTop - 1.0F && panelY < Math.max(tableHeaderBottom, rowBottom)) {
                final int columnIndex = clamp((int) ((panelX - columnLeft) / columnWidth), 0, this.columnVisibleCount - 1);
                targetColumnOffset = this.columnOffset + columnIndex;
            }

            if (this.rowVisibleCount > 0 && panelY >= rowTop && panelY < rowBottom) {
                final int rowIndex = clamp((int) ((panelY - rowTop) / (HIT_TEST_LINE_HEIGHT + 1.0F)), 0, this.rowVisibleCount - 1);
                targetRowOffset = this.rowOffset + rowIndex;
            }

            return new FocusHit(this.focusCursor, 0, targetRowOffset, targetColumnOffset);
        }
    }

    private record InteractionLayout(float surfaceLeft, float surfaceTop, float surfaceWidth, float surfaceHeight, float outputTop, float availableHeight) {
        private float contentLeft() {
            return this.surfaceLeft + HIT_TEST_CONTENT_MARGIN;
        }

        private float contentWidth() {
            return this.surfaceWidth - HIT_TEST_CONTENT_MARGIN * 2.0F;
        }

        private Float panelX(final double horizontalRatio, final ScreenBlockEntity screen) {
            final double center = 0.5D;
            final double panelHalfWidth = this.surfaceWidth * HIT_TEST_SCREEN_SCALE / (2.0D * Math.max(1, screen.getSpanX()));
            final double leftEdge = center - panelHalfWidth;
            final double rightEdge = center + panelHalfWidth;
            if (horizontalRatio < leftEdge || horizontalRatio > rightEdge) {
                return null;
            }

            final double panelRelative = (horizontalRatio - leftEdge) / Math.max(0.0001D, rightEdge - leftEdge);
            return this.surfaceLeft + (float) panelRelative * this.surfaceWidth;
        }

        private Float panelY(final double verticalRatio, final ScreenBlockEntity screen) {
            final double center = 0.5D + HIT_TEST_PANEL_LIFT_PER_EXTRA_ROW * Math.max(0, screen.getSpanY() - 1) / Math.max(1.0D, screen.getSpanY());
            final double panelHalfHeight = this.surfaceHeight * HIT_TEST_SCREEN_SCALE / (2.0D * Math.max(1, screen.getSpanY()));
            final double lowerEdge = center - panelHalfHeight;
            final double upperEdge = center + panelHalfHeight;
            if (verticalRatio < lowerEdge || verticalRatio > upperEdge) {
                return null;
            }

            final double panelRelative = (upperEdge - verticalRatio) / Math.max(0.0001D, upperEdge - lowerEdge);
            return this.surfaceTop + (float) panelRelative * this.surfaceHeight;
        }
    }
}
