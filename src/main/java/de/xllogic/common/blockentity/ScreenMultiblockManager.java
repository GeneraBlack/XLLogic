package de.xllogic.common.blockentity;

import de.xllogic.common.block.ScreenBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class ScreenMultiblockManager {
    private static final Comparator<GridPoint> GRID_ORDER = Comparator.comparingInt(GridPoint::y).thenComparingInt(GridPoint::x);

    private ScreenMultiblockManager() {
    }

    public static void rebuildAround(final Level level, final BlockPos origin) {
        if (level == null || level.isClientSide()) {
            return;
        }

        final Set<BlockPos> processed = new HashSet<>();
        for (final BlockPos candidatePos : candidatePositions(origin)) {
            processCandidate(level, candidatePos.immutable(), processed);
        }
    }

    private static void processCandidate(final Level level, final BlockPos candidatePos, final Set<BlockPos> processed) {
        if (processed.contains(candidatePos)) {
            return;
        }

        final ScreenBlockEntity candidate = screenAt(level, candidatePos);
        if (candidate == null) {
            return;
        }

        if (!candidate.hasLinkedComputer()) {
            candidate.setMultiblockState(candidate.getBlockPos(), 1, 1);
            updatePanelJoins(level, candidate, false, false, false, false);
            processed.add(candidatePos);
            return;
        }

        final List<ScreenBlockEntity> component = collectLinkedComponent(level, candidate, processed);
        if (!component.isEmpty()) {
            applyLayouts(level, component);
        }
    }

    private static List<BlockPos> candidatePositions(final BlockPos origin) {
        final ArrayList<BlockPos> positions = new ArrayList<>(Direction.values().length + 1);
        positions.add(origin.immutable());
        for (final Direction direction : Direction.values()) {
            positions.add(origin.relative(direction));
        }
        return List.copyOf(positions);
    }

    private static List<ScreenBlockEntity> collectLinkedComponent(final Level level, final ScreenBlockEntity start, final Set<BlockPos> processed) {
        final Direction facing = start.getBlockState().getValue(ScreenBlock.FACING);
        final BlockPos linkedComputerPos = start.getLinkedComputerPos();
        if (linkedComputerPos == null) {
            processed.add(start.getBlockPos().immutable());
            return List.of(start);
        }

        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        final Set<BlockPos> componentPositions = new HashSet<>();
        final BlockPos startPos = start.getBlockPos().immutable();
        queue.add(startPos);
        seen.add(startPos);

        while (!queue.isEmpty()) {
            final BlockPos currentPos = queue.removeFirst();
            final ScreenBlockEntity current = screenAt(level, currentPos);
            if (matches(current, facing, linkedComputerPos)) {
                componentPositions.add(currentPos);
                enqueueMatchingNeighbors(level, currentPos, facing, linkedComputerPos, seen, queue);
            }
        }

        processed.addAll(componentPositions);
        final ArrayList<ScreenBlockEntity> component = new ArrayList<>(componentPositions.size());
        for (final BlockPos componentPos : componentPositions) {
            final ScreenBlockEntity screen = matchingScreen(level, componentPos, facing, linkedComputerPos);
            if (screen != null) {
                component.add(screen);
            }
        }
        return List.copyOf(component);
    }

    private static void enqueueMatchingNeighbors(final Level level, final BlockPos currentPos, final Direction facing,
                                                 final BlockPos linkedComputerPos, final Set<BlockPos> seen, final ArrayDeque<BlockPos> queue) {
        for (final Direction direction : adjacencyDirections(facing)) {
            final BlockPos neighborPos = currentPos.relative(direction).immutable();
            if (seen.add(neighborPos) && matchingScreen(level, neighborPos, facing, linkedComputerPos) != null) {
                queue.addLast(neighborPos);
            }
        }
    }

    private static void applyLayouts(final Level level, final List<ScreenBlockEntity> component) {
        if (component.isEmpty()) {
            return;
        }

        final Direction facing = component.get(0).getBlockState().getValue(ScreenBlock.FACING);
        final Direction horizontalDirection = facing.getCounterClockWise();
        final BlockPos referencePos = component.get(0).getBlockPos();

        final ArrayList<ProjectedScreen> projectedScreens = new ArrayList<>(component.size());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (final ScreenBlockEntity screen : component) {
            final int projectedX = distanceAlong(referencePos, screen.getBlockPos(), horizontalDirection);
            final int projectedY = screen.getBlockPos().getY() - referencePos.getY();
            projectedScreens.add(new ProjectedScreen(screen, projectedX, projectedY));
            minX = Math.min(minX, projectedX);
            minY = Math.min(minY, projectedY);
        }

        final Map<GridPoint, ScreenBlockEntity> screensByPoint = new HashMap<>();
        for (final ProjectedScreen projectedScreen : projectedScreens) {
            screensByPoint.put(new GridPoint(projectedScreen.x() - minX, projectedScreen.y() - minY), projectedScreen.screen());
        }

        final Set<GridPoint> unassigned = new HashSet<>(screensByPoint.keySet());
        while (!unassigned.isEmpty()) {
            final GridPoint start = unassigned.stream().min(GRID_ORDER).orElseThrow();
            final LayoutRect rect = chooseBestRectangle(start, unassigned);
            final ScreenBlockEntity controller = screensByPoint.get(start);
            if (controller == null) {
                unassigned.remove(start);
                continue;
            }

            final BlockPos controllerPos = controller.getBlockPos();
            for (int row = 0; row < rect.height(); row++) {
                for (int column = 0; column < rect.width(); column++) {
                    final GridPoint point = new GridPoint(start.x() + column, start.y() + row);
                    final ScreenBlockEntity member = screensByPoint.get(point);
                    if (member != null) {
                        member.setMultiblockState(controllerPos, rect.width(), rect.height());
                        updatePanelJoins(level, member, column + 1 < rect.width(), column > 0, row + 1 < rect.height(), row > 0);
                    }
                    unassigned.remove(point);
                }
            }
        }
    }

    private static LayoutRect chooseBestRectangle(final GridPoint start, final Set<GridPoint> available) {
        int maxWidth = 0;
        while (available.contains(new GridPoint(start.x() + maxWidth, start.y()))) {
            maxWidth++;
        }

        LayoutCandidate best = evaluateRectangle(start, new LayoutRect(1, 1), available);
        for (int width = 1; width <= maxWidth; width++) {
            int maxHeight = 0;
            while (isRowFilled(start, width, maxHeight, available)) {
                maxHeight++;
            }

            for (int height = 1; height <= maxHeight; height++) {
                final LayoutCandidate candidate = evaluateRectangle(start, new LayoutRect(width, height), available);
                if (candidate.betterThan(best)) {
                    best = candidate;
                }
            }
        }
        return best.rect();
    }

    private static boolean isRowFilled(final GridPoint start, final int width, final int rowOffset, final Set<GridPoint> available) {
        final int y = start.y() + rowOffset;
        for (int x = start.x(); x < start.x() + width; x++) {
            if (!available.contains(new GridPoint(x, y))) {
                return false;
            }
        }
        return true;
    }

    private static LayoutCandidate evaluateRectangle(final GridPoint start, final LayoutRect rect, final Set<GridPoint> available) {
        final Set<GridPoint> remaining = new HashSet<>(available.size());
        for (final GridPoint point : available) {
            if (!contains(start, rect, point)) {
                remaining.add(point);
            }
        }

        int remainderComponents = 0;
        int remainderSingletons = 0;
        final Set<GridPoint> seen = new HashSet<>();
        for (final GridPoint point : remaining) {
            if (!seen.add(point)) {
                continue;
            }

            remainderComponents++;
            final int componentSize = floodRemaining(point, remaining, seen);
            if (componentSize == 1) {
                remainderSingletons++;
            }
        }

        return new LayoutCandidate(rect, rect.width() * rect.height(), remainderComponents, remainderSingletons, seamEdges(start, rect, available));
    }

    private static int floodRemaining(final GridPoint start, final Set<GridPoint> remaining, final Set<GridPoint> seen) {
        final ArrayDeque<GridPoint> queue = new ArrayDeque<>();
        queue.add(start);
        int size = 0;
        while (!queue.isEmpty()) {
            final GridPoint current = queue.removeFirst();
            size++;
            for (final GridPoint neighbor : neighbors(current)) {
                if (remaining.contains(neighbor) && seen.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return size;
    }

    private static int seamEdges(final GridPoint start, final LayoutRect rect, final Set<GridPoint> available) {
        int seamEdges = 0;
        for (int row = 0; row < rect.height(); row++) {
            for (int column = 0; column < rect.width(); column++) {
                final GridPoint point = new GridPoint(start.x() + column, start.y() + row);
                for (final GridPoint neighbor : neighbors(point)) {
                    if (!contains(start, rect, neighbor) && available.contains(neighbor)) {
                        seamEdges++;
                    }
                }
            }
        }
        return seamEdges;
    }

    private static boolean contains(final GridPoint start, final LayoutRect rect, final GridPoint point) {
        return point.x() >= start.x() && point.x() < start.x() + rect.width()
                && point.y() >= start.y() && point.y() < start.y() + rect.height();
    }

    private static List<GridPoint> neighbors(final GridPoint point) {
        return List.of(
                new GridPoint(point.x() - 1, point.y()),
                new GridPoint(point.x() + 1, point.y()),
                new GridPoint(point.x(), point.y() - 1),
                new GridPoint(point.x(), point.y() + 1));
    }

    private static void updatePanelJoins(final Level level, final ScreenBlockEntity screen, final boolean joinCounterClockWise,
                                         final boolean joinClockWise, final boolean joinUp, final boolean joinDown) {
        final var currentState = screen.getBlockState();
        final var updatedState = ScreenBlock.withPanelJoins(currentState, joinCounterClockWise, joinClockWise, joinUp, joinDown);
        if (!currentState.equals(updatedState)) {
            level.setBlock(screen.getBlockPos(), updatedState, 3);
        }
    }

    private static boolean matches(final ScreenBlockEntity candidate, final Direction facing, final BlockPos linkedComputerPos) {
        return candidate != null
                && candidate.getBlockState().getValue(ScreenBlock.FACING) == facing
                && Objects.equals(linkedComputerPos, candidate.getLinkedComputerPos());
    }

    private static ScreenBlockEntity matchingScreen(final Level level, final BlockPos pos, final Direction facing, final BlockPos linkedComputerPos) {
        final ScreenBlockEntity candidate = screenAt(level, pos);
        return matches(candidate, facing, linkedComputerPos) ? candidate : null;
    }

    private static Direction[] adjacencyDirections(final Direction facing) {
        return new Direction[] {facing.getCounterClockWise(), facing.getClockWise(), Direction.UP, Direction.DOWN};
    }

    private static int distanceAlong(final BlockPos origin, final BlockPos target, final Direction direction) {
        final BlockPos delta = target.subtract(origin);
        return delta.getX() * direction.getStepX() + delta.getY() * direction.getStepY() + delta.getZ() * direction.getStepZ();
    }

    private static ScreenBlockEntity screenAt(final Level level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScreenBlockEntity screen ? screen : null;
    }

    private record GridPoint(int x, int y) {
    }

    private record LayoutRect(int width, int height) {
    }

    private record LayoutCandidate(LayoutRect rect, int area, int remainderComponents, int remainderSingletons, int seamEdges) {
        private boolean betterThan(final LayoutCandidate other) {
            if (this.area != other.area) {
                return this.area > other.area;
            }
            if (this.remainderComponents != other.remainderComponents) {
                return this.remainderComponents < other.remainderComponents;
            }
            if (this.remainderSingletons != other.remainderSingletons) {
                return this.remainderSingletons < other.remainderSingletons;
            }
            if (this.minSide() != other.minSide()) {
                return this.minSide() > other.minSide();
            }
            if (this.seamEdges != other.seamEdges) {
                return this.seamEdges < other.seamEdges;
            }
            if (this.rect.width() != other.rect.width()) {
                return this.rect.width() > other.rect.width();
            }
            return this.rect.height() > other.rect.height();
        }

        private int minSide() {
            return Math.min(this.rect.width(), this.rect.height());
        }
    }

    private record ProjectedScreen(ScreenBlockEntity screen, int x, int y) {
    }
}