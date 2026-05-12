package de.xllogic.common.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractCableBlock extends Block {
    protected static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    protected static final BooleanProperty EAST = BlockStateProperties.EAST;
    protected static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    protected static final BooleanProperty WEST = BlockStateProperties.WEST;
    protected static final BooleanProperty UP = BlockStateProperties.UP;
    protected static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> CONNECTION_PROPERTIES = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN
    );

    private static final VoxelShape CORE_SHAPE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);
    private static final VoxelShape NORTH_SHAPE = Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 6.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(10.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 16.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D);
    private static final VoxelShape UP_SHAPE = Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape DOWN_SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 6.0D, 10.0D);

    protected AbstractCableBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.recalculateConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction, final BlockState neighborState, final LevelAccessor level, final BlockPos pos, final BlockPos neighborPos) {
        return state.setValue(propertyFor(direction), this.canConnectTo(state, level, neighborPos, neighborState, direction));
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            this.handleTopologyChange(level, pos, state);
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            this.handleTopologyRemoval(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        VoxelShape shape = CORE_SHAPE;
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, NORTH_SHAPE);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, EAST_SHAPE);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, SOUTH_SHAPE);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, WEST_SHAPE);
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, UP_SHAPE);
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, DOWN_SHAPE);
        }
        return shape;
    }

    protected void handleTopologyChange(final Level level, final BlockPos pos, final BlockState state) {
    }

    protected void handleTopologyRemoval(final Level level, final BlockPos pos, final BlockState state) {
        this.handleTopologyChange(level, pos, state);
    }

    protected abstract boolean canConnectTo(BlockState state, BlockGetter level, BlockPos neighborPos, BlockState neighborState, Direction direction);

    private BlockState recalculateConnections(final BlockState state, final BlockGetter level, final BlockPos pos) {
        BlockState resolved = state;
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = pos.relative(direction);
            resolved = resolved.setValue(propertyFor(direction), this.canConnectTo(resolved, level, neighborPos, level.getBlockState(neighborPos), direction));
        }
        return resolved;
    }

    private static BooleanProperty propertyFor(final Direction direction) {
        return CONNECTION_PROPERTIES.get(direction);
    }
}