package de.xllogic.common.network;

import de.xllogic.common.block.ColoredRedstoneCableBlock;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.device.RedstoneIOMode;
import de.xllogic.common.registry.XLBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class XLRedstoneBusResolver {
    private static final int MAX_ROUTE_HOPS = 8;
    private static final int MAX_BLOCKER_HINTS = 6;
    private static final int MAX_COMPACT_ROLE_MARKERS = 2;
    private static final String BLOCKER_TYPE_FILTER = "filter";
    private static final String BLOCKER_TYPE_DEVICE_CHANNEL = "device_channel";
    private static final String BLOCKER_TYPE_UNLOADED_FRONTIER = "unloaded_frontier";
    private static final String CABLE_TYPE_COLORED = "colored";
    private static final String CABLE_TYPE_BUS = "bus";
    private static final String REASON_ADJACENT_COLORED_CHANNEL_MISMATCH = "adjacent coloured cables use different channels";
    private static final String REASON_TARGET_COLORED_FILTERS_OTHER_CHANNEL = "target coloured cable filters another channel";
    private static final String REASON_UNLOADED_FRONTIER = "bus continues into an unloaded frontier";

    private XLRedstoneBusResolver() {
    }

    public static boolean isBusCable(final BlockState state) {
        return state.getBlock() == XLBlocks.REDSTONE_BUS_CABLE.get() || state.getBlock() instanceof ColoredRedstoneCableBlock;
    }

    public static boolean canCableConnectTo(final BlockState cableState, final BlockState neighborState) {
        if (neighborState.getBlock() == XLBlocks.REDSTONE_IO.get()) {
            return true;
        }
        if (!isBusCable(cableState) || !isBusCable(neighborState)) {
            return false;
        }
        if (cableState.getBlock() == XLBlocks.REDSTONE_BUS_CABLE.get() || neighborState.getBlock() == XLBlocks.REDSTONE_BUS_CABLE.get()) {
            return true;
        }
        return channelValue(cableState) == channelValue(neighborState);
    }

    public static int resolveChannelSignal(final Level level, final BlockPos startCablePos, final int channel) {
        if (!canResolve(level, startCablePos)) {
            return 0;
        }

        final int resolvedChannel = Mth.clamp(channel, 0, 15);
        final BlockState startState = level.getBlockState(startCablePos);
        if (!carriesChannel(startState, resolvedChannel)) {
            return 0;
        }

        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();
        queue.add(startCablePos.immutable());
        visited.add(startCablePos.immutable());
        final ChannelResolutionContext resolutionContext = new ChannelResolutionContext(level, resolvedChannel, queue, visited);

        int strongestSignal = 0;
        while (!queue.isEmpty() && strongestSignal < 15) {
            strongestSignal = resolveAtCable(resolutionContext, queue.removeFirst(), strongestSignal);
        }

        return strongestSignal;
    }

    public static void notifyAdjacentNetworks(final Level level, final BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        final Set<BlockPos> affectedDevices = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> visitedCables = new HashSet<>();
        final boolean sourceIsCable = level.isLoaded(pos) && isBusCable(level.getBlockState(pos));

        for (final Direction direction : Direction.values()) {
            seedAdjacentState(level, pos.relative(direction), sourceIsCable, queue, visitedCables, affectedDevices);
        }

        while (!queue.isEmpty()) {
            final BlockPos cablePos = queue.removeFirst();
            collectAdjacentDevices(level, cablePos, queue, visitedCables, affectedDevices);
        }

        for (final BlockPos devicePos : affectedDevices) {
            if (level.getBlockEntity(devicePos) instanceof RedstoneIOBlockEntity redstoneIo && redstoneIo.getMode() == RedstoneIOMode.INPUT) {
                redstoneIo.captureInputs();
            }
        }
    }

    public static BusNetworkDebugSnapshot inspectBus(final Level level, final BlockPos startPos) {
        if (!canResolve(level, startPos)) {
            return new BusNetworkDebugSnapshot(startPos == null ? BlockPos.ZERO : startPos.immutable(), 0, 0, List.of(), List.of());
        }

        final BlockState startState = level.getBlockState(startPos);
        if (!isBusCable(startState)) {
            return new BusNetworkDebugSnapshot(startPos.immutable(), 0, 0, List.of(), List.of());
        }

        final PhysicalBusNetworkSnapshot physical = inspectPhysicalBusNetwork(level, startPos);
        final ArrayList<ChannelFlowDebugSnapshot> channelFlows = new ArrayList<>();
        for (int channel = 0; channel < 16; channel++) {
            if (!carriesChannel(startState, channel)) {
                continue;
            }

            final ChannelFlowDebugSnapshot flow = inspectChannelFlow(level, startPos, channel);
            if (flow.hasAttachedDevices()) {
                channelFlows.add(flow);
            }
        }
        channelFlows.sort(Comparator.comparingInt(ChannelFlowDebugSnapshot::channel));
        return new BusNetworkDebugSnapshot(startPos.immutable(), physical.cableCount(), physical.coloredCableCount(), sortPositions(physical.redstoneIoPositions()), List.copyOf(channelFlows));
    }

    public static ChannelFlowDebugSnapshot inspectChannelFlow(final Level level, final BlockPos startPos, final int channel) {
        if (!canResolve(level, startPos)) {
            return new ChannelFlowDebugSnapshot(Mth.clamp(channel, 0, 15), 0, 0, 0, List.of(), List.of(), List.of(), false, List.of(), false);
        }

        final int resolvedChannel = Mth.clamp(channel, 0, 15);
        final BlockState startState = level.getBlockState(startPos);
        if (!isBusCable(startState) || !carriesChannel(startState, resolvedChannel)) {
            return new ChannelFlowDebugSnapshot(resolvedChannel, 0, 0, 0, List.of(), List.of(), List.of(), false, List.of(), false);
        }

        final ChannelFlowContext flowContext = new ChannelFlowContext(level, resolvedChannel, startPos);

        int strongestSignal = 0;
        int coloredCableCount = 0;
        while (flowContext.hasPendingTraversal()) {
            final ChannelFlowTraversalNode traversalNode = flowContext.pollNext();
            final BlockPos currentPos = traversalNode.pos();
            final BlockState currentState = level.getBlockState(currentPos);
            if (currentState.getBlock() instanceof ColoredRedstoneCableBlock) {
                coloredCableCount++;
            }

            final LinkedHashSet<HopEndpointMarker> producersAtHop = new LinkedHashSet<>();
            final LinkedHashSet<HopEndpointMarker> consumersAtHop = new LinkedHashSet<>();

            for (final Direction direction : Direction.values()) {
                strongestSignal = inspectChannelNeighbor(flowContext, traversalNode, currentState, direction, strongestSignal, producersAtHop, consumersAtHop);
            }

            flowContext.recordRouteHop(currentPos, currentState, traversalNode.distance(), producersAtHop, consumersAtHop);
        }

        return new ChannelFlowDebugSnapshot(
                resolvedChannel,
                strongestSignal,
                flowContext.cableCount(),
                coloredCableCount,
                sortPositions(flowContext.producerPositions()),
                sortPositions(flowContext.consumerPositions()),
                flowContext.routeHops(),
                flowContext.routeHopsTruncated(),
                flowContext.blockers(),
                flowContext.blockersTruncated()
        );
    }

    private static void collectAdjacentDevices(final Level level, final BlockPos cablePos, final ArrayDeque<BlockPos> queue, final Set<BlockPos> visitedCables, final Set<BlockPos> affectedDevices) {
        for (final Direction direction : Direction.values()) {
            collectAdjacentState(level, cablePos.relative(direction), queue, visitedCables, affectedDevices);
        }
    }

    private static boolean canResolve(final Level level, final BlockPos startCablePos) {
        return level != null && level.isLoaded(startCablePos);
    }

    private static int resolveAtCable(final ChannelResolutionContext context, final BlockPos currentPos, final int currentStrongestSignal) {
        final BlockState currentState = context.level().getBlockState(currentPos);
        int strongestSignal = currentStrongestSignal;
        for (final Direction direction : Direction.values()) {
            strongestSignal = resolveNeighbor(context, currentState, currentPos.relative(direction), direction, strongestSignal);
            if (strongestSignal >= 15) {
                return 15;
            }
        }
        return strongestSignal;
    }

    private static int resolveNeighbor(final ChannelResolutionContext context, final BlockState currentState, final BlockPos neighborPos, final Direction direction, final int currentStrongestSignal) {
        if (!context.level().isLoaded(neighborPos)) {
            return currentStrongestSignal;
        }

        final BlockState neighborState = context.level().getBlockState(neighborPos);
        if (isBusCable(neighborState) && canCableConnectTo(currentState, neighborState) && carriesChannel(neighborState, context.channel())) {
            enqueueCable(neighborPos, context.queue(), context.visitedCables());
            return currentStrongestSignal;
        }

        if (isMatchingOutput(context.level(), neighborPos, direction, context.channel())) {
            return Math.max(currentStrongestSignal, resolveOutputLevel(context.level(), neighborPos, direction));
        }

        return currentStrongestSignal;
    }

    private static void seedAdjacentState(final Level level, final BlockPos neighborPos, final boolean sourceIsCable, final ArrayDeque<BlockPos> queue, final Set<BlockPos> visitedCables, final Set<BlockPos> affectedDevices) {
        if (!level.isLoaded(neighborPos)) {
            return;
        }

        final BlockState neighborState = level.getBlockState(neighborPos);
        if (isBusCable(neighborState)) {
            enqueueCable(neighborPos, queue, visitedCables);
            return;
        }

        if (sourceIsCable && neighborState.getBlock() == XLBlocks.REDSTONE_IO.get()) {
            affectedDevices.add(neighborPos.immutable());
        }
    }

    private static void collectAdjacentState(final Level level, final BlockPos neighborPos, final ArrayDeque<BlockPos> queue, final Set<BlockPos> visitedCables, final Set<BlockPos> affectedDevices) {
        if (!level.isLoaded(neighborPos)) {
            return;
        }

        final BlockState neighborState = level.getBlockState(neighborPos);
        if (isBusCable(neighborState)) {
            enqueueCable(neighborPos, queue, visitedCables);
            return;
        }

        if (neighborState.getBlock() == XLBlocks.REDSTONE_IO.get()) {
            affectedDevices.add(neighborPos.immutable());
        }
    }

    private static void enqueueCable(final BlockPos cablePos, final ArrayDeque<BlockPos> queue, final Set<BlockPos> visitedCables) {
        final BlockPos immutablePos = cablePos.immutable();
        if (visitedCables.add(immutablePos)) {
            queue.addLast(immutablePos);
        }
    }

    private static boolean isMatchingOutput(final Level level, final BlockPos neighborPos, final Direction direction, final int channel) {
        return level.getBlockState(neighborPos).getBlock() == XLBlocks.REDSTONE_IO.get()
                && level.getBlockEntity(neighborPos) instanceof RedstoneIOBlockEntity redstoneIo
                && redstoneIo.getMode() == RedstoneIOMode.OUTPUT
                && redstoneIo.getBusChannel(direction.getOpposite()) == channel;
    }

    private static int resolveOutputLevel(final Level level, final BlockPos neighborPos, final Direction direction) {
        if (level.getBlockEntity(neighborPos) instanceof RedstoneIOBlockEntity redstoneIo) {
            return redstoneIo.getSideLevel(direction.getOpposite());
        }
        return 0;
    }

    private static PhysicalBusNetworkSnapshot inspectPhysicalBusNetwork(final Level level, final BlockPos startPos) {
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> visitedCables = new HashSet<>();
        final Set<BlockPos> redstoneIoPositions = new HashSet<>();
        int coloredCableCount = 0;

        queue.add(startPos.immutable());
        visitedCables.add(startPos.immutable());
        while (!queue.isEmpty()) {
            final BlockPos currentPos = queue.removeFirst();
            final BlockState currentState = level.getBlockState(currentPos);
            if (currentState.getBlock() instanceof ColoredRedstoneCableBlock) {
                coloredCableCount++;
            }

            for (final Direction direction : Direction.values()) {
                inspectPhysicalNeighbor(level, currentState, currentPos.relative(direction), queue, visitedCables, redstoneIoPositions);
            }
        }

        return new PhysicalBusNetworkSnapshot(visitedCables.size(), coloredCableCount, redstoneIoPositions);
    }

    private static void inspectPhysicalNeighbor(final Level level, final BlockState currentState, final BlockPos neighborPos, final ArrayDeque<BlockPos> queue, final Set<BlockPos> visitedCables, final Set<BlockPos> redstoneIoPositions) {
        if (!level.isLoaded(neighborPos)) {
            return;
        }

        final BlockState neighborState = level.getBlockState(neighborPos);
        if (isBusCable(neighborState) && canCableConnectTo(currentState, neighborState)) {
            enqueueCable(neighborPos, queue, visitedCables);
            return;
        }

        if (neighborState.getBlock() == XLBlocks.REDSTONE_IO.get()) {
            redstoneIoPositions.add(neighborPos.immutable());
        }
    }

    private static int inspectChannelNeighbor(final ChannelFlowContext context, final ChannelFlowTraversalNode traversalNode, final BlockState currentState, final Direction direction, final int currentStrongestSignal, final Set<HopEndpointMarker> producersAtHop, final Set<HopEndpointMarker> consumersAtHop) {
        final BlockPos neighborPos = traversalNode.pos().relative(direction);
        if (!context.level().isLoaded(neighborPos)) {
            context.recordBlocker(unloadedFrontierBlocker(traversalNode.pos(), neighborPos, context.channel()));
            return currentStrongestSignal;
        }

        final BlockState neighborState = context.level().getBlockState(neighborPos);
        context.recordBlocker(filteredTransitionBlocker(currentState, traversalNode.pos(), neighborState, neighborPos, context.channel()));
        if (isBusCable(neighborState) && canCableConnectTo(currentState, neighborState) && carriesChannel(neighborState, context.channel())) {
            context.enqueueCable(neighborPos, traversalNode.distance() + 1);
            return currentStrongestSignal;
        }

        if (neighborState.getBlock() != XLBlocks.REDSTONE_IO.get() || !(context.level().getBlockEntity(neighborPos) instanceof RedstoneIOBlockEntity redstoneIo)) {
            return currentStrongestSignal;
        }

        final Direction deviceSide = direction.getOpposite();
        if (redstoneIo.getBusChannel(deviceSide) != context.channel()) {
            context.recordBlocker(deviceChannelBlocker(traversalNode.pos(), neighborPos, context.channel(), redstoneIo.getBusChannel(deviceSide), redstoneIo.getEndpointName(), deviceSide));
            return currentStrongestSignal;
        }

        if (redstoneIo.getMode() == RedstoneIOMode.OUTPUT) {
            context.producerPositions().add(neighborPos.immutable());
            producersAtHop.add(new HopEndpointMarker(direction, neighborPos, redstoneIo.getEndpointName()));
            return Math.max(currentStrongestSignal, redstoneIo.getSideLevel(deviceSide));
        }

        if (redstoneIo.getMode() == RedstoneIOMode.INPUT) {
            context.consumerPositions().add(neighborPos.immutable());
            consumersAtHop.add(new HopEndpointMarker(direction, neighborPos, redstoneIo.getEndpointName()));
        }
        return currentStrongestSignal;
    }

    private static RouteBlockerDebugSnapshot filteredTransitionBlocker(final BlockState currentState, final BlockPos currentPos, final BlockState neighborState, final BlockPos neighborPos, final int channel) {
        if (!isBusCable(neighborState) || (canCableConnectTo(currentState, neighborState) && carriesChannel(neighborState, channel))) {
            return null;
        }

        if (!(neighborState.getBlock() instanceof ColoredRedstoneCableBlock) && !(currentState.getBlock() instanceof ColoredRedstoneCableBlock)) {
            return null;
        }

        final int targetChannel = neighborState.getBlock() instanceof ColoredRedstoneCableBlock ? channelValue(neighborState) : -1;
        final String reason = currentState.getBlock() instanceof ColoredRedstoneCableBlock && neighborState.getBlock() instanceof ColoredRedstoneCableBlock && !canCableConnectTo(currentState, neighborState)
                ? REASON_ADJACENT_COLORED_CHANNEL_MISMATCH
                : REASON_TARGET_COLORED_FILTERS_OTHER_CHANNEL;
        return new RouteBlockerDebugSnapshot(BLOCKER_TYPE_FILTER, currentPos.immutable(), neighborPos.immutable(), channel, targetChannel, reason);
    }

    private static RouteBlockerDebugSnapshot unloadedFrontierBlocker(final BlockPos currentPos, final BlockPos neighborPos, final int channel) {
        return new RouteBlockerDebugSnapshot(BLOCKER_TYPE_UNLOADED_FRONTIER, currentPos.immutable(), neighborPos.immutable(), channel, -1, REASON_UNLOADED_FRONTIER);
    }

    private static RouteBlockerDebugSnapshot deviceChannelBlocker(final BlockPos currentPos, final BlockPos devicePos, final int channel, final int targetChannel, final String endpointName, final Direction deviceSide) {
        final String detail = "device " + (endpointName == null || endpointName.isBlank() ? "redstone_io" : endpointName)
                + " on side " + deviceSide.getSerializedName()
                + " listens on channel " + Mth.clamp(targetChannel, 0, 15);
        return new RouteBlockerDebugSnapshot(BLOCKER_TYPE_DEVICE_CHANNEL, currentPos.immutable(), devicePos.immutable(), channel, targetChannel, detail);
    }

    private static int channelValue(final BlockState state) {
        if (state.hasProperty(ColoredRedstoneCableBlock.CHANNEL)) {
            return state.getValue(ColoredRedstoneCableBlock.CHANNEL);
        }
        return state.getBlock() instanceof ColoredRedstoneCableBlock coloredCable
                ? coloredCable.fixedChannel()
                : ColoredRedstoneCableBlock.DEFAULT_CHANNEL;
    }

    private static List<BlockPos> sortPositions(final Set<BlockPos> positions) {
        final ArrayList<BlockPos> resolved = new ArrayList<>(positions.size());
        for (final BlockPos position : positions) {
            resolved.add(position.immutable());
        }
        resolved.sort(Comparator.comparingLong(BlockPos::asLong));
        return List.copyOf(resolved);
    }

    private static boolean carriesChannel(final BlockState state, final int channel) {
        if (state.getBlock() == XLBlocks.REDSTONE_BUS_CABLE.get()) {
            return true;
        }
        return state.getBlock() instanceof ColoredRedstoneCableBlock
                && channelValue(state) == channel;
    }

    public record BusNetworkDebugSnapshot(BlockPos originPos, int cableCount, int coloredCableCount, List<BlockPos> redstoneIoPositions, List<ChannelFlowDebugSnapshot> channelFlows) {
        public BusNetworkDebugSnapshot {
            originPos = originPos == null ? BlockPos.ZERO : originPos.immutable();
            cableCount = Math.max(0, cableCount);
            coloredCableCount = Math.max(0, coloredCableCount);
            redstoneIoPositions = redstoneIoPositions == null ? List.of() : List.copyOf(redstoneIoPositions);
            channelFlows = channelFlows == null ? List.of() : List.copyOf(channelFlows);
        }

        public String summaryLine() {
            final String channels = this.channelFlows.isEmpty()
                    ? "none"
                    : this.channelFlows.stream().map(ChannelFlowDebugSnapshot::compactLabel).reduce((left, right) -> left + ", " + right).orElse("none");
            return "Bus @ " + this.originPos.toShortString() + " | cables: " + this.cableCount + " | colored: " + this.coloredCableCount + " | redstone I/O: " + this.redstoneIoPositions.size() + " | channels: " + channels;
        }
    }

    public record ChannelFlowDebugSnapshot(
            int channel,
            int strongestSignal,
            int cableCount,
            int coloredCableCount,
            List<BlockPos> producerPositions,
            List<BlockPos> consumerPositions,
            List<RouteHopDebugSnapshot> routeHops,
            boolean routeHopsTruncated,
            List<RouteBlockerDebugSnapshot> blockers,
            boolean blockersTruncated
    ) {
        public ChannelFlowDebugSnapshot {
            channel = Mth.clamp(channel, 0, 15);
            strongestSignal = Mth.clamp(strongestSignal, 0, 15);
            cableCount = Math.max(0, cableCount);
            coloredCableCount = Math.max(0, coloredCableCount);
            producerPositions = producerPositions == null ? List.of() : List.copyOf(producerPositions);
            consumerPositions = consumerPositions == null ? List.of() : List.copyOf(consumerPositions);
            routeHops = routeHops == null ? List.of() : List.copyOf(routeHops);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }

        public boolean hasAttachedDevices() {
            return !this.producerPositions.isEmpty() || !this.consumerPositions.isEmpty();
        }

        public String compactLabel() {
            final String blockerLabel = this.blockers.size() + (this.blockersTruncated ? "+" : "");
            return this.channel + "(" + this.strongestSignal + ",h" + this.cableCount + ",b" + blockerLabel + ")";
        }

        public String summaryLine() {
            return "channel " + this.channel
                    + " | strongest: " + this.strongestSignal
                    + " | route cables: " + this.cableCount
                    + " | colored: " + this.coloredCableCount
                    + " | outputs: " + this.producerPositions.size()
                    + " | inputs: " + this.consumerPositions.size()
                    + compactMarkerSummary()
                    + " | hop preview: " + this.routeHops.size() + (this.routeHopsTruncated ? "+" : "")
                    + " | blockers: " + this.blockers.size() + (this.blockersTruncated ? "+" : "")
                    + " | filtered: " + this.countBlockers(BLOCKER_TYPE_FILTER)
                    + " | device: " + this.countBlockers(BLOCKER_TYPE_DEVICE_CHANNEL)
                    + " | frontiers: " + this.countBlockers(BLOCKER_TYPE_UNLOADED_FRONTIER);
        }

        private long countBlockers(final String blockerType) {
            return this.blockers.stream().filter(blocker -> blockerType.equals(blocker.blockerType())).count();
        }

        private String compactMarkerSummary() {
            final String producerSummary = summarizeCompactRole("P", collectMarkers(true));
            final String consumerSummary = summarizeCompactRole("C", collectMarkers(false));
            if (producerSummary.isEmpty() && consumerSummary.isEmpty()) {
                return "";
            }
            if (producerSummary.isEmpty()) {
                return " | " + consumerSummary;
            }
            if (consumerSummary.isEmpty()) {
                return " | " + producerSummary;
            }
            return " | " + producerSummary + " | " + consumerSummary;
        }

        private List<HopEndpointMarker> collectMarkers(final boolean producers) {
            final LinkedHashSet<HopEndpointMarker> markers = new LinkedHashSet<>();
            for (final RouteHopDebugSnapshot hop : this.routeHops) {
                markers.addAll(producers ? hop.producers() : hop.consumers());
            }
            return List.copyOf(markers);
        }

        private static String summarizeCompactRole(final String prefix, final List<HopEndpointMarker> markers) {
            if (markers.isEmpty()) {
                return "";
            }

            final int limit = Math.min(markers.size(), MAX_COMPACT_ROLE_MARKERS);
            final StringBuilder builder = new StringBuilder(prefix).append('[');
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(markers.get(index).summaryLabel());
            }
            if (markers.size() > limit) {
                builder.append(", +").append(markers.size() - limit);
            }
            return builder.append(']').toString();
        }
    }

    public record RouteHopDebugSnapshot(int distance, BlockPos pos, String cableType, int cableChannel, List<HopEndpointMarker> producers, List<HopEndpointMarker> consumers) {
        public RouteHopDebugSnapshot {
            distance = Math.max(0, distance);
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            cableType = cableType == null || cableType.isBlank() ? CABLE_TYPE_BUS : cableType;
            cableChannel = cableType.equals(CABLE_TYPE_COLORED) ? Mth.clamp(cableChannel, 0, 15) : -1;
            producers = producers == null ? List.of() : List.copyOf(producers);
            consumers = consumers == null ? List.of() : List.copyOf(consumers);
        }

        public String summaryLine() {
            final String detail = this.cableChannel >= 0 ? this.cableType + "[" + this.cableChannel + "]" : this.cableType;
            return "hop " + this.distance + ": " + detail + " @ " + this.pos.toShortString() + summarizeMarkers(this.producers, this.consumers);
        }

        private static String summarizeMarkers(final List<HopEndpointMarker> producers, final List<HopEndpointMarker> consumers) {
            final String producerSummary = summarizeRole("P", producers);
            final String consumerSummary = summarizeRole("C", consumers);
            if (producerSummary.isEmpty() && consumerSummary.isEmpty()) {
                return "";
            }
            if (producerSummary.isEmpty()) {
                return " | " + consumerSummary;
            }
            if (consumerSummary.isEmpty()) {
                return " | " + producerSummary;
            }
            return " | " + producerSummary + " | " + consumerSummary;
        }

        private static String summarizeRole(final String prefix, final List<HopEndpointMarker> markers) {
            if (markers.isEmpty()) {
                return "";
            }

            final StringBuilder builder = new StringBuilder(prefix).append('[');
            for (int index = 0; index < markers.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(markers.get(index).summaryLabel());
            }
            return builder.append(']').toString();
        }
    }

    public record HopEndpointMarker(Direction side, BlockPos devicePos, String endpointName) {
        public HopEndpointMarker {
            side = side == null ? Direction.NORTH : side;
            devicePos = devicePos == null ? BlockPos.ZERO : devicePos.immutable();
            endpointName = endpointName == null || endpointName.isBlank() ? "redstone_io" : endpointName;
        }

        public String summaryLabel() {
            return this.side.getSerializedName() + ':' + this.endpointName;
        }
    }

    public record RouteBlockerDebugSnapshot(String blockerType, BlockPos fromPos, BlockPos blockedPos, int channel, int targetChannel, String reason) {
        public RouteBlockerDebugSnapshot {
            blockerType = blockerType == null || blockerType.isBlank() ? BLOCKER_TYPE_FILTER : blockerType;
            fromPos = fromPos == null ? BlockPos.ZERO : fromPos.immutable();
            blockedPos = blockedPos == null ? BlockPos.ZERO : blockedPos.immutable();
            channel = Mth.clamp(channel, 0, 15);
            targetChannel = targetChannel < 0 ? -1 : Mth.clamp(targetChannel, 0, 15);
            reason = reason == null || reason.isBlank() ? "filtered transition blocked" : reason;
        }

        public String summaryLine() {
            final String target = this.targetChannel >= 0 ? " | target channel: " + this.targetChannel : "";
            return "blocker: " + this.blockerType + " | channel " + this.channel + " stops at " + this.fromPos.toShortString() + " -> " + this.blockedPos.toShortString() + " | " + this.reason + target;
        }
    }

    private record PhysicalBusNetworkSnapshot(int cableCount, int coloredCableCount, Set<BlockPos> redstoneIoPositions) {
    }

    private record ChannelResolutionContext(Level level, int channel, ArrayDeque<BlockPos> queue, Set<BlockPos> visitedCables) {
    }

    private static final class ChannelFlowContext {
        private final Level level;
        private final int channel;
        private final ArrayDeque<ChannelFlowTraversalNode> queue = new ArrayDeque<>();
        private final Set<BlockPos> visitedCables = new HashSet<>();
        private final Set<BlockPos> producerPositions = new HashSet<>();
        private final Set<BlockPos> consumerPositions = new HashSet<>();
        private final ArrayList<RouteHopDebugSnapshot> routeHops = new ArrayList<>();
        private final LinkedHashSet<RouteBlockerDebugSnapshot> blockers = new LinkedHashSet<>();
        private boolean routeHopsTruncated;
        private boolean blockersTruncated;

        private ChannelFlowContext(final Level level, final int channel, final BlockPos startPos) {
            this.level = level;
            this.channel = channel;
            this.enqueueCable(startPos, 0);
        }

        private Level level() {
            return this.level;
        }

        private int channel() {
            return this.channel;
        }

        private boolean hasPendingTraversal() {
            return !this.queue.isEmpty();
        }

        private ChannelFlowTraversalNode pollNext() {
            return this.queue.removeFirst();
        }

        private void enqueueCable(final BlockPos cablePos, final int distance) {
            final BlockPos immutablePos = cablePos.immutable();
            if (this.visitedCables.add(immutablePos)) {
                this.queue.addLast(new ChannelFlowTraversalNode(immutablePos, distance));
            }
        }

        private void recordRouteHop(final BlockPos pos, final BlockState state, final int distance, final Set<HopEndpointMarker> producers, final Set<HopEndpointMarker> consumers) {
            if (this.routeHops.size() >= MAX_ROUTE_HOPS) {
                this.routeHopsTruncated = true;
                return;
            }

            final String cableType = state.getBlock() instanceof ColoredRedstoneCableBlock ? CABLE_TYPE_COLORED : CABLE_TYPE_BUS;
            final int cableChannel = state.getBlock() instanceof ColoredRedstoneCableBlock ? channelValue(state) : -1;
            this.routeHops.add(new RouteHopDebugSnapshot(distance, pos, cableType, cableChannel, List.copyOf(producers), List.copyOf(consumers)));
        }

        private void recordBlocker(final RouteBlockerDebugSnapshot blocker) {
            if (blocker == null || this.blockers.contains(blocker)) {
                return;
            }
            if (this.blockers.size() >= MAX_BLOCKER_HINTS) {
                this.blockersTruncated = true;
                return;
            }
            this.blockers.add(blocker);
        }

        private int cableCount() {
            return this.visitedCables.size();
        }

        private Set<BlockPos> producerPositions() {
            return this.producerPositions;
        }

        private Set<BlockPos> consumerPositions() {
            return this.consumerPositions;
        }

        private List<RouteHopDebugSnapshot> routeHops() {
            return List.copyOf(this.routeHops);
        }

        private boolean routeHopsTruncated() {
            return this.routeHopsTruncated;
        }

        private List<RouteBlockerDebugSnapshot> blockers() {
            return List.copyOf(this.blockers);
        }

        private boolean blockersTruncated() {
            return this.blockersTruncated;
        }
    }

    private record ChannelFlowTraversalNode(BlockPos pos, int distance) {
    }
}