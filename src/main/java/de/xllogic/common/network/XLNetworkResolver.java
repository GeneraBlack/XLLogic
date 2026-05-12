package de.xllogic.common.network;

import com.google.gson.JsonObject;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import de.xllogic.common.registry.XLBlocks;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class XLNetworkResolver {
    private static final int MAX_BOUNDARY_HINTS = 8;
    private static final String ENDPOINT_TYPE_XLAPI_BLOCK = "xlapi_block";
    private static final String XLAPI_LABEL = "xlapi";
    private static final String BOUNDARY_TYPE_UNLOADED = "unloaded";
    private static final Map<String, ResolveComputersCache> RESOLVED_COMPUTERS_CACHE = new ConcurrentHashMap<>();

    private XLNetworkResolver() {
    }

    public static List<XLNetworkEndpointSnapshot> resolveEndpoints(final Level level, final BlockPos computerPos) {
        final Map<BlockPos, XLNetworkEndpointSnapshot> endpoints = new LinkedHashMap<>();

        traverseLocalSegment(level, computerPos, new TraversalVisitor() {
            @Override
            public void visitEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos endpointPos, final int distance) {
                endpoints.putIfAbsent(endpointPos, XLNetworkEndpointSnapshot.local(endpoint, endpointPos, distance));
            }
        });

        return sortEndpoints(endpoints.values());
    }

    public static List<XLNetworkEndpointSnapshot> resolveReachableEndpoints(final Level level, final BlockPos computerPos) {
        final List<XLNetworkEndpointSnapshot> localEndpoints = resolveEndpoints(level, computerPos);
        final Map<BlockPos, XLNetworkEndpointSnapshot> reachable = new LinkedHashMap<>();
        for (final XLNetworkEndpointSnapshot endpoint : localEndpoints) {
            reachable.put(endpoint.pos(), endpoint);
        }

        for (final XLNetworkEndpointSnapshot endpoint : resolveBridgedEndpoints(level, localEndpoints)) {
            reachable.putIfAbsent(endpoint.pos(), endpoint);
        }

        return sortEndpoints(reachable.values());
    }

    public static List<XLNetworkEndpointSnapshot> resolveBridgedEndpoints(final Level level, final BlockPos computerPos) {
        return resolveBridgedEndpoints(level, resolveEndpoints(level, computerPos));
    }

    public static List<BridgeRemoteComputerSnapshot> resolveBridgedComputersForBridge(final Level level, final BlockPos localComputerPos, final BlockPos bridgePos) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.network.resolveBridgedComputers");
        try {
            if (!level.isLoaded(bridgePos) || !(level.getBlockEntity(bridgePos) instanceof XLApiBlockEntity bridge) || !bridge.isRelayEnabled()) {
                return List.of();
            }

            final Set<BlockPos> localComputerPositions = new HashSet<>(resolveComputers(level, localComputerPos));
            final Map<BlockPos, BridgeRemoteComputerSnapshot> bridgedComputers = new LinkedHashMap<>();
            for (final BlockPos peerPos : XLApiBlockEntity.findActiveRelayPeers(level, bridge.getUplinkGroup(), bridgePos)) {
                mergePeerRemoteComputers(level, localComputerPositions, bridge, peerPos, bridgedComputers);
            }
            return sortRemoteComputers(bridgedComputers.values());
        } finally {
            XLRuntimeDebugger.endSection("server.network.resolveBridgedComputers", debugStartedAt);
        }
    }

    public static int countBridgedComputersForBridge(final Level level, final BlockPos localComputerPos, final BlockPos bridgePos) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.network.countBridgedComputers");
        try {
            if (!level.isLoaded(bridgePos) || !(level.getBlockEntity(bridgePos) instanceof XLApiBlockEntity bridge) || !bridge.isRelayEnabled()) {
                return 0;
            }

            final Set<BlockPos> localComputerPositions = new HashSet<>(resolveComputers(level, localComputerPos));
            final Set<BlockPos> bridgedComputerPositions = new HashSet<>();
            for (final BlockPos peerPos : XLApiBlockEntity.findActiveRelayPeers(level, bridge.getUplinkGroup(), bridgePos)) {
                countPeerRemoteComputers(level, localComputerPositions, peerPos, bridgedComputerPositions);
            }
            return bridgedComputerPositions.size();
        } finally {
            XLRuntimeDebugger.endSection("server.network.countBridgedComputers", debugStartedAt);
        }
    }

    public static List<BlockPos> resolveComputers(final Level level, final BlockPos startPos) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.network.resolveComputers");
        try {
            if (level == null || startPos == null || !level.isLoaded(startPos)) {
                return List.of();
            }

            final String levelKey = level.dimension().location().toString();
            final long gameTime = level.getGameTime();
            final ResolveComputersCache cache = RESOLVED_COMPUTERS_CACHE.computeIfAbsent(levelKey, ignored -> new ResolveComputersCache());
            final List<BlockPos> cached = cache.get(startPos, gameTime);
            if (cached != null) {
                return cached;
            }

            final Map<BlockPos, Integer> computers = new LinkedHashMap<>();
            final Set<BlockPos> segmentPositions = new HashSet<>();
            segmentPositions.add(startPos.immutable());

            traverseLocalSegment(level, startPos, new TraversalVisitor() {
                @Override
                public void visitConductor(final BlockPos conductorPos, final Block block, final int distance) {
                    segmentPositions.add(conductorPos.immutable());
                }

                @Override
                public void visitEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos endpointPos, final int distance) {
                    segmentPositions.add(endpointPos.immutable());
                }

                @Override
                public void visitComputer(final BlockPos computerPos, final int distance) {
                    segmentPositions.add(computerPos.immutable());
                    computers.putIfAbsent(computerPos, distance);
                }
            });

            final List<Map.Entry<BlockPos, Integer>> resolved = new ArrayList<>(computers.entrySet());
            resolved.sort(Comparator.comparingInt(Map.Entry<BlockPos, Integer>::getValue).thenComparing(entry -> entry.getKey().asLong()));

            final ArrayList<BlockPos> positions = new ArrayList<>(resolved.size());
            for (final Map.Entry<BlockPos, Integer> entry : resolved) {
                positions.add(entry.getKey());
            }
            final List<BlockPos> result = List.copyOf(positions);
            cache.put(segmentPositions, result, gameTime);
            return result;
        } finally {
            XLRuntimeDebugger.endSection("server.network.resolveComputers", debugStartedAt);
        }
    }

    public static boolean hasValidAnimationNetwork(final Level level, final BlockPos startPos) {
        if (level == null || startPos == null || !level.isLoaded(startPos)) {
            return false;
        }

        if (level.getBlockEntity(startPos) instanceof XLApiBlockEntity) {
            return hasValidXlApiAnimationNetwork(level, startPos);
        }
        return resolveComputers(level, startPos).size() == 1;
    }

    public static LocalSegmentDebugSnapshot inspectLocalSegment(final Level level, final BlockPos startPos) {
        final Set<BlockPos> cablePositions = new HashSet<>();
        final Set<BlockPos> conductorPositions = new HashSet<>();
        final Map<BlockPos, XLNetworkEndpointSnapshot> endpoints = new LinkedHashMap<>();
        final Map<BlockPos, Integer> computers = new LinkedHashMap<>();
        final LinkedHashSet<SegmentBoundaryDebugSnapshot> boundaries = new LinkedHashSet<>();
        final boolean[] boundariesTruncated = new boolean[1];

        traverseLocalSegment(level, startPos, new TraversalVisitor() {
            @Override
            public void visitConductor(final BlockPos conductorPos, final Block block, final int distance) {
                conductorPositions.add(conductorPos.immutable());
                if (block == XLBlocks.NETWORK_CABLE.get()) {
                    cablePositions.add(conductorPos.immutable());
                }
            }

            @Override
            public void visitEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos endpointPos, final int distance) {
                endpoints.putIfAbsent(endpointPos, XLNetworkEndpointSnapshot.local(endpoint, endpointPos, distance));
            }

            @Override
            public void visitComputer(final BlockPos computerPos, final int distance) {
                computers.putIfAbsent(computerPos.immutable(), distance);
            }

            @Override
            public void visitBoundaryEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos fromPos, final BlockPos endpointPos, final int distance) {
                recordBoundary(boundaries, segmentBoundaryForEndpoint(endpoint, fromPos, endpointPos), boundariesTruncated);
            }

            @Override
            public void visitUnloadedFrontier(final BlockPos fromPos, final BlockPos frontierPos, final Direction direction, final int distance) {
                recordBoundary(boundaries, new SegmentBoundaryDebugSnapshot(fromPos, frontierPos, BOUNDARY_TYPE_UNLOADED, "unloaded edge towards " + direction.getSerializedName()), boundariesTruncated);
            }
        });

        return new LocalSegmentDebugSnapshot(
                cablePositions.size(),
                conductorPositions.size(),
                sortBlockPositions(computers.keySet()),
                sortEndpoints(endpoints.values()),
                sortSegmentBoundaries(boundaries),
                boundariesTruncated[0]
        );
    }

    private static boolean isHardConductor(final Block block) {
        return block == XLBlocks.COMPUTER.get() || block == XLBlocks.NETWORK_CABLE.get();
    }

    private static boolean isComputer(final Block block) {
        return block == XLBlocks.COMPUTER.get();
    }

    private static boolean hasValidXlApiAnimationNetwork(final Level level, final BlockPos bridgePos) {
        boolean hasValidBranch = false;
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = bridgePos.relative(direction);
            if (level.isLoaded(neighborPos)) {
                final Block neighborBlock = level.getBlockState(neighborPos).getBlock();
                if (isHardConductor(neighborBlock)) {
                    final int computerCount = resolveComputers(level, neighborPos).size();
                    if (computerCount > 1) {
                        return false;
                    }
                    if (computerCount == 1) {
                        hasValidBranch = true;
                    }
                }
            }
        }
        return hasValidBranch;
    }

    private static boolean allowsLocalPassthrough(final NamedNetworkEndpointBlockEntity endpoint) {
        return endpoint.allowsNetworkPassthrough() && !(endpoint instanceof XLApiBlockEntity);
    }

    private static List<XLNetworkEndpointSnapshot> resolveBridgedEndpoints(final Level level, final List<XLNetworkEndpointSnapshot> localEndpoints) {
        final Map<BlockPos, XLNetworkEndpointSnapshot> bridgedEndpoints = new LinkedHashMap<>();
        final Set<BlockPos> localEndpointPositions = new HashSet<>();
        for (final XLNetworkEndpointSnapshot endpoint : localEndpoints) {
            localEndpointPositions.add(endpoint.pos());
        }

        for (final XLNetworkEndpointSnapshot localEndpoint : localEndpoints) {
            final XLApiBlockEntity bridge = resolveBridge(level, localEndpoint);
            if (bridge == null) {
                continue;
            }

            for (final BlockPos peerPos : XLApiBlockEntity.findActiveRelayPeers(level, bridge.getUplinkGroup(), bridge.getBlockPos())) {
                mergePeerSegment(level, peerPos, bridge.getEndpointName(), bridge.getUplinkGroup(), localEndpoint.distance(), localEndpointPositions, bridgedEndpoints);
            }
        }

        return sortEndpoints(bridgedEndpoints.values());
    }

    private static XLApiBlockEntity resolveBridge(final Level level, final XLNetworkEndpointSnapshot endpoint) {
        if (!ENDPOINT_TYPE_XLAPI_BLOCK.equals(endpoint.endpointType()) || !level.isLoaded(endpoint.pos())) {
            return null;
        }
        if (level.getBlockEntity(endpoint.pos()) instanceof XLApiBlockEntity xlApi && xlApi.isRelayEnabled()) {
            return xlApi;
        }
        return null;
    }

    private static void mergePeerSegment(final Level level, final BlockPos peerPos, final String bridgeName, final int bridgeUplinkGroup, final int bridgeDistance, final Set<BlockPos> localEndpointPositions, final Map<BlockPos, XLNetworkEndpointSnapshot> bridgedEndpoints) {
        for (final XLNetworkEndpointSnapshot endpoint : resolveEndpoints(level, peerPos)) {
            if (localEndpointPositions.contains(endpoint.pos()) || isBridgeEndpoint(level, endpoint.pos())) {
                continue;
            }

            final int reachableDistance = bridgeDistance + endpoint.distance() + 1;
            bridgedEndpoints.putIfAbsent(endpoint.pos(), XLNetworkEndpointSnapshot.bridged(endpoint, reachableDistance, bridgeName, bridgeUplinkGroup));
        }
    }

    private static boolean isBridgeEndpoint(final Level level, final BlockPos endpointPos) {
        return level.isLoaded(endpointPos) && level.getBlockEntity(endpointPos) instanceof XLApiBlockEntity;
    }

    private static void mergePeerRemoteComputers(final Level level, final Set<BlockPos> localComputerPositions, final XLApiBlockEntity bridge, final BlockPos peerPos, final Map<BlockPos, BridgeRemoteComputerSnapshot> bridgedComputers) {
        final List<BlockPos> remoteComputers = resolveComputers(level, peerPos);
        if (remoteComputers.size() != 1) {
            return;
        }

        final BlockPos remoteComputerPos = remoteComputers.get(0);
        if (localComputerPositions.contains(remoteComputerPos) || !level.isLoaded(remoteComputerPos)) {
            return;
        }

        if (level.getBlockEntity(remoteComputerPos) instanceof ComputerBlockEntity remoteComputer) {
            bridgedComputers.putIfAbsent(remoteComputerPos, new BridgeRemoteComputerSnapshot(
                    remoteComputer.computerId(),
                    remoteComputerPos,
                    bridge.getEndpointName(),
                    bridge.getUplinkGroup(),
                    remoteComputer.runtimeStatus(),
                    remoteComputer.getRuntimeState().summary(),
                    remoteComputer.countBridgeMessages(bridge.getUplinkGroup())
            ));
        }
    }

    private static void countPeerRemoteComputers(final Level level,
                                                 final Set<BlockPos> localComputerPositions,
                                                 final BlockPos peerPos,
                                                 final Set<BlockPos> bridgedComputerPositions) {
        final List<BlockPos> remoteComputers = resolveComputers(level, peerPos);
        if (remoteComputers.size() != 1) {
            return;
        }

        final BlockPos remoteComputerPos = remoteComputers.get(0);
        if (localComputerPositions.contains(remoteComputerPos) || !level.isLoaded(remoteComputerPos)) {
            return;
        }

        if (level.getBlockEntity(remoteComputerPos) instanceof ComputerBlockEntity) {
            bridgedComputerPositions.add(remoteComputerPos.immutable());
        }
    }

    private static List<XLNetworkEndpointSnapshot> sortEndpoints(final java.util.Collection<XLNetworkEndpointSnapshot> endpoints) {
        final List<XLNetworkEndpointSnapshot> resolved = new ArrayList<>(endpoints);
        resolved.sort(Comparator.comparing(XLNetworkEndpointSnapshot::networkScope).thenComparing(XLNetworkEndpointSnapshot::endpointType).thenComparing(XLNetworkEndpointSnapshot::endpointName).thenComparing(snapshot -> snapshot.pos().asLong()));
        return List.copyOf(resolved);
    }

    private static List<BridgeRemoteComputerSnapshot> sortRemoteComputers(final java.util.Collection<BridgeRemoteComputerSnapshot> remoteComputers) {
        final List<BridgeRemoteComputerSnapshot> resolved = new ArrayList<>(remoteComputers);
        resolved.sort(Comparator.comparing(BridgeRemoteComputerSnapshot::computerId).thenComparing(snapshot -> snapshot.computerPos().asLong()));
        return List.copyOf(resolved);
    }

    public record BridgeRemoteComputerSnapshot(String computerId, BlockPos computerPos, String bridgeEndpointName, int bridgeUplinkGroup, String runtimeStatus, String summary, int inboxCount) {
        private static final int MAX_ID_LENGTH = 64;
        private static final int MAX_BRIDGE_NAME_LENGTH = 64;
        private static final int MAX_RUNTIME_STATUS_LENGTH = 16;
        private static final int MAX_SUMMARY_LENGTH = 512;

        public BridgeRemoteComputerSnapshot {
            computerId = limit(computerId, MAX_ID_LENGTH, "computer");
            computerPos = computerPos == null ? BlockPos.ZERO : computerPos.immutable();
            bridgeEndpointName = limit(bridgeEndpointName, MAX_BRIDGE_NAME_LENGTH, XLAPI_LABEL);
            bridgeUplinkGroup = Math.max(0, bridgeUplinkGroup);
            runtimeStatus = limit(runtimeStatus, MAX_RUNTIME_STATUS_LENGTH, "offline");
            summary = limit(summary, MAX_SUMMARY_LENGTH, "");
            inboxCount = Math.max(0, inboxCount);
        }

        public JsonObject toJson() {
            final JsonObject object = new JsonObject();
            object.addProperty("id", this.computerId);
            object.addProperty("position", this.computerPos.toShortString());
            object.addProperty("bridge_name", this.bridgeEndpointName);
            object.addProperty("bridge_group", this.bridgeUplinkGroup);
            object.addProperty("runtime_status", this.runtimeStatus);
            object.addProperty("summary", this.summary);
            object.addProperty("inbox_count", this.inboxCount);
            return object;
        }

        private static String limit(final String value, final int maxLength, final String fallback) {
            final String safeValue = value == null ? fallback : value;
            return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
        }
    }

    private static void traverseLocalSegment(final Level level, final BlockPos startPos, final TraversalVisitor visitor) {
        final ArrayDeque<TraversalNode> queue = new ArrayDeque<>();
        final Set<BlockPos> visitedConductors = new HashSet<>();

        queue.add(new TraversalNode(startPos, 0));
        visitedConductors.add(startPos);
        final Block startBlock = level.getBlockState(startPos).getBlock();
        if (isHardConductor(startBlock)) {
            visitor.visitConductor(startPos.immutable(), startBlock, 0);
        }
        if (startBlock == XLBlocks.COMPUTER.get()) {
            visitor.visitComputer(startPos.immutable(), 0);
        }

        while (!queue.isEmpty()) {
            traverseNeighbors(level, queue.removeFirst(), visitedConductors, queue, visitor);
        }
    }

    private static void traverseNeighbors(final Level level, final TraversalNode current, final Set<BlockPos> visitedConductors, final ArrayDeque<TraversalNode> queue, final TraversalVisitor visitor) {
        for (final Direction direction : Direction.values()) {
            processNeighbor(level, current, direction, visitedConductors, queue, visitor);
        }
    }

    private static void processNeighbor(final Level level, final TraversalNode current, final Direction direction, final Set<BlockPos> visitedConductors, final ArrayDeque<TraversalNode> queue, final TraversalVisitor visitor) {
        final BlockPos neighborPos = current.pos().relative(direction);
        if (!level.isLoaded(neighborPos)) {
            visitor.visitUnloadedFrontier(current.pos(), neighborPos, direction, current.distance() + 1);
            return;
        }

        final BlockState neighborState = level.getBlockState(neighborPos);
        final NamedNetworkEndpointBlockEntity endpoint = level.getBlockEntity(neighborPos) instanceof NamedNetworkEndpointBlockEntity endpointBlockEntity ? endpointBlockEntity : null;
        final NeighborNode neighbor = new NeighborNode(neighborPos, current.distance() + 1, neighborState.getBlock(), endpoint);
        if (neighbor.endpoint() != null) {
            visitor.visitEndpoint(neighbor.endpoint(), neighbor.pos(), neighbor.distance());
            visitor.visitBoundaryEndpoint(neighbor.endpoint(), current.pos(), neighbor.pos(), neighbor.distance());
            if (allowsLocalPassthrough(neighbor.endpoint()) && visitedConductors.add(neighbor.pos())) {
                queue.addLast(new TraversalNode(neighbor.pos(), neighbor.distance()));
            }
            return;
        }

        if (isComputer(neighbor.block())) {
            visitor.visitComputer(neighbor.pos(), neighbor.distance());
        }
        if (isHardConductor(neighbor.block()) && visitedConductors.add(neighbor.pos())) {
            visitor.visitConductor(neighbor.pos(), neighbor.block(), neighbor.distance());
            queue.addLast(new TraversalNode(neighbor.pos(), neighbor.distance()));
        }
    }

    private static List<BlockPos> sortBlockPositions(final java.util.Collection<BlockPos> positions) {
        final ArrayList<BlockPos> resolved = new ArrayList<>(positions.size());
        for (final BlockPos pos : positions) {
            resolved.add(pos.immutable());
        }
        resolved.sort(Comparator.comparingLong(BlockPos::asLong));
        return List.copyOf(resolved);
    }

    private static void recordBoundary(final Set<SegmentBoundaryDebugSnapshot> boundaries, final SegmentBoundaryDebugSnapshot boundary, final boolean[] boundariesTruncated) {
        if (boundary == null || boundaries.contains(boundary)) {
            return;
        }
        if (boundaries.size() >= MAX_BOUNDARY_HINTS) {
            boundariesTruncated[0] = true;
            return;
        }
        boundaries.add(boundary);
    }

    private static SegmentBoundaryDebugSnapshot segmentBoundaryForEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos fromPos, final BlockPos endpointPos) {
        if (endpoint instanceof XLApiBlockEntity xlApi) {
            return new SegmentBoundaryDebugSnapshot(
                    fromPos,
                    endpointPos,
                    XLAPI_LABEL,
                    xlApi.getEndpointName() + " | relay: " + xlApi.isRelayEnabled() + " | group: " + xlApi.getUplinkGroup()
            );
        }
        return null;
    }

    private static List<SegmentBoundaryDebugSnapshot> sortSegmentBoundaries(final java.util.Collection<SegmentBoundaryDebugSnapshot> boundaries) {
        final ArrayList<SegmentBoundaryDebugSnapshot> resolved = new ArrayList<>(boundaries.size());
        resolved.addAll(boundaries);
        resolved.sort(Comparator.comparing(SegmentBoundaryDebugSnapshot::boundaryType)
                .thenComparing(snapshot -> snapshot.boundaryPos().asLong())
                .thenComparing(snapshot -> snapshot.fromPos().asLong()));
        return List.copyOf(resolved);
    }

    private interface TraversalVisitor {
        default void visitConductor(final BlockPos conductorPos, final Block block, final int distance) {
        }

        default void visitEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos endpointPos, final int distance) {
        }

        default void visitComputer(final BlockPos computerPos, final int distance) {
        }

        default void visitBoundaryEndpoint(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos fromPos, final BlockPos endpointPos, final int distance) {
        }

        default void visitUnloadedFrontier(final BlockPos fromPos, final BlockPos frontierPos, final Direction direction, final int distance) {
        }
    }

    private record TraversalNode(BlockPos pos, int distance) {
    }

    private record NeighborNode(BlockPos pos, int distance, Block block, NamedNetworkEndpointBlockEntity endpoint) {
    }

    private static final class ResolveComputersCache {
        private long gameTime = Long.MIN_VALUE;
        private final Map<BlockPos, List<BlockPos>> resultsByPosition = new LinkedHashMap<>();

        private synchronized List<BlockPos> get(final BlockPos position, final long currentGameTime) {
            if (this.gameTime != currentGameTime) {
                this.gameTime = currentGameTime;
                this.resultsByPosition.clear();
                return null;
            }
            return this.resultsByPosition.get(position);
        }

        private synchronized void put(final Set<BlockPos> positions, final List<BlockPos> result, final long currentGameTime) {
            if (this.gameTime != currentGameTime) {
                this.gameTime = currentGameTime;
                this.resultsByPosition.clear();
            }

            for (final BlockPos position : positions) {
                this.resultsByPosition.put(position, result);
            }
        }
    }

    public record LocalSegmentDebugSnapshot(
            int cableCount,
            int conductorCount,
            List<BlockPos> computerPositions,
            List<XLNetworkEndpointSnapshot> endpoints,
            List<SegmentBoundaryDebugSnapshot> boundaries,
            boolean boundariesTruncated
    ) {
        public LocalSegmentDebugSnapshot {
            cableCount = Math.max(0, cableCount);
            conductorCount = Math.max(0, conductorCount);
            computerPositions = computerPositions == null ? List.of() : List.copyOf(computerPositions);
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
        }

        public boolean hasComputerConflict() {
            return this.computerPositions.size() > 1;
        }

        public int xlapiBoundaryCount() {
            return countBoundaries(XLAPI_LABEL);
        }

        public int unloadedBoundaryCount() {
            return countBoundaries(BOUNDARY_TYPE_UNLOADED);
        }

        private int countBoundaries(final String type) {
            int count = 0;
            for (final SegmentBoundaryDebugSnapshot boundary : this.boundaries) {
                if (type.equals(boundary.boundaryType())) {
                    count++;
                }
            }
            return count;
        }

        public String endpointTypeSummary() {
            if (this.endpoints.isEmpty()) {
                return "none";
            }

            final LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            for (final XLNetworkEndpointSnapshot endpoint : this.endpoints) {
                counts.merge(endpoint.endpointType(), 1, Integer::sum);
            }

            final StringBuilder builder = new StringBuilder();
            for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return builder.toString();
        }
    }

    public record SegmentBoundaryDebugSnapshot(BlockPos fromPos, BlockPos boundaryPos, String boundaryType, String detail) {
        public SegmentBoundaryDebugSnapshot {
            fromPos = fromPos == null ? BlockPos.ZERO : fromPos.immutable();
            boundaryPos = boundaryPos == null ? BlockPos.ZERO : boundaryPos.immutable();
            boundaryType = boundaryType == null || boundaryType.isBlank() ? "boundary" : boundaryType;
            detail = detail == null || detail.isBlank() ? boundaryType : detail;
        }

        public String summaryLine() {
            if (XLAPI_LABEL.equals(this.boundaryType)) {
                return "boundary: XLAPI @ " + this.boundaryPos.toShortString() + " stops local discovery from " + this.fromPos.toShortString() + " | " + this.detail;
            }
            if (BOUNDARY_TYPE_UNLOADED.equals(this.boundaryType)) {
                return "boundary: unloaded frontier from " + this.fromPos.toShortString() + " towards " + this.boundaryPos.toShortString() + " | " + this.detail;
            }
            return "boundary: " + this.boundaryType + " @ " + this.boundaryPos.toShortString() + " from " + this.fromPos.toShortString() + " | " + this.detail;
        }
    }
}