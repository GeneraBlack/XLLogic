package de.xllogic.client.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ScriptDiff {
    private ScriptDiff() {
    }

    public static DiffResult compare(final String localText, final String serverText) {
        final List<String> localLines = splitLines(localText);
        final List<String> serverLines = splitLines(serverText);
        final int[][] lcs = lcsLengths(localLines, serverLines);
        final ArrayList<DiffHunk> hunks = new ArrayList<>();
        int localIndex = 0;
        int serverIndex = 0;
        int hunkLocalStart = -1;
        int hunkServerStart = -1;

        while (localIndex < localLines.size() || serverIndex < serverLines.size()) {
            if (linesEqual(localLines, serverLines, localIndex, serverIndex)) {
                if (hunkLocalStart >= 0) {
                    hunks.add(createHunk(localLines, serverLines, hunkLocalStart, localIndex, hunkServerStart, serverIndex));
                    hunkLocalStart = -1;
                    hunkServerStart = -1;
                }
                localIndex++;
                serverIndex++;
            } else {
                final HunkStart hunkStart = ensureHunkStart(hunkLocalStart, hunkServerStart, localIndex, serverIndex);
                hunkLocalStart = hunkStart.localStart();
                hunkServerStart = hunkStart.serverStart();
                final CompareCursor compareCursor = advanceCompareCursor(localLines, serverLines, lcs, localIndex, serverIndex);
                localIndex = compareCursor.localIndex();
                serverIndex = compareCursor.serverIndex();
            }
        }

        if (hunkLocalStart >= 0) {
            hunks.add(createHunk(localLines, serverLines, hunkLocalStart, localIndex, hunkServerStart, serverIndex));
        }

        return new DiffResult(List.copyOf(hunks), List.copyOf(localLines), List.copyOf(serverLines));
    }

    public static String applyServerHunk(final String localText, final DiffHunk hunk) {
        if (hunk == null) {
            return normalize(localText);
        }

        final List<String> localLines = splitLines(localText);
        final ArrayList<String> mergedLines = new ArrayList<>(Math.max(1,
                localLines.size() - hunk.localLineCount() + hunk.serverLineCount()));
        mergedLines.addAll(localLines.subList(0, Math.min(hunk.localStartLine(), localLines.size())));
        mergedLines.addAll(hunk.serverLines());
        mergedLines.addAll(localLines.subList(Math.min(hunk.localEndLineExclusive(), localLines.size()), localLines.size()));
        return joinLines(mergedLines);
    }

    public static MergeResult merge(final String baseText, final String localText, final String serverText) {
        final MergeContext mergeContext = createMergeContext(baseText, localText, serverText);
        final ArrayList<String> mergedLines = new ArrayList<>();
        final ArrayList<MergeConflict> conflicts = new ArrayList<>();

        int autoMergedServerChangeCount = 0;
        int baseCursor = 0;
        int localIndex = 0;
        int serverIndex = 0;

        while (localIndex < mergeContext.localChanges().size() || serverIndex < mergeContext.serverChanges().size()) {
            final Change localChange = changeAt(mergeContext.localChanges(), localIndex);
            final Change serverChange = changeAt(mergeContext.serverChanges(), serverIndex);
            final int nextStart = Math.min(changeStart(localChange), changeStart(serverChange));
            if (baseCursor < nextStart) {
                mergedLines.addAll(mergeContext.baseLines().subList(baseCursor, nextStart));
            }

            switch (selectMergeBranch(localChange, serverChange)) {
                case SERVER_ONLY -> {
                    final Change nonNullServerChange = requireChange(serverChange, "server");
                    mergedLines.addAll(nonNullServerChange.variantLines());
                    baseCursor = nonNullServerChange.baseEndLineExclusive();
                    autoMergedServerChangeCount++;
                    serverIndex++;
                }
                case LOCAL_ONLY -> {
                    final Change nonNullLocalChange = requireChange(localChange, "local");
                    mergedLines.addAll(nonNullLocalChange.variantLines());
                    baseCursor = nonNullLocalChange.baseEndLineExclusive();
                    localIndex++;
                }
                case IDENTICAL -> {
                    final Change identicalLocalChange = requireChange(localChange, "local");
                    mergedLines.addAll(identicalLocalChange.variantLines());
                    baseCursor = identicalLocalChange.baseEndLineExclusive();
                    localIndex++;
                    serverIndex++;
                }
                case CONFLICT -> {
                    final ConflictCluster cluster = expandConflictCluster(localIndex, serverIndex, mergeContext);
                    final ResolvedCluster resolvedCluster = resolveConflictCluster(cluster);
                    final int mergedOffset = mergedLines.size();
                    mergedLines.addAll(resolvedCluster.mergedSegment());
                    autoMergedServerChangeCount += resolvedCluster.autoMergedServerChangeCount();
                    for (final RelativeConflict relativeConflict : resolvedCluster.conflicts()) {
                        conflicts.add(new MergeConflict(
                                mergedOffset + relativeConflict.mergedStartLine(),
                                mergedOffset + relativeConflict.mergedEndLineExclusive(),
                                cluster.serverStartLine() + relativeConflict.serverStartLine(),
                                cluster.serverStartLine() + relativeConflict.serverEndLineExclusive(),
                                cluster.baseStartLine() + relativeConflict.baseStartLine(),
                                cluster.baseStartLine() + relativeConflict.baseEndLineExclusive(),
                                relativeConflict.localLines(),
                                relativeConflict.serverLines(),
                                relativeConflict.baseLines()
                        ));
                    }
                    baseCursor = cluster.baseEndLineExclusive();
                    localIndex = cluster.nextLocalIndex();
                    serverIndex = cluster.nextServerIndex();
                }
            }
        }

        if (baseCursor < mergeContext.baseLines().size()) {
            mergedLines.addAll(mergeContext.baseLines().subList(baseCursor, mergeContext.baseLines().size()));
        }

        return new MergeResult(joinLines(mergedLines), List.copyOf(conflicts), autoMergedServerChangeCount);
    }

    public static String applyServerConflict(final String mergedText, final MergeConflict conflict) {
        if (conflict == null) {
            return normalize(mergedText);
        }

        final List<String> mergedLines = splitLines(mergedText);
        final ArrayList<String> resolvedLines = new ArrayList<>(Math.max(1,
                mergedLines.size() - conflict.localLineCount() + conflict.serverLineCount()));
        resolvedLines.addAll(mergedLines.subList(0, Math.min(conflict.mergedStartLine(), mergedLines.size())));
        resolvedLines.addAll(conflict.serverLines());
        resolvedLines.addAll(mergedLines.subList(Math.min(conflict.mergedEndLineExclusive(), mergedLines.size()), mergedLines.size()));
        return joinLines(resolvedLines);
    }

    private static MergeContext createMergeContext(final String baseText, final String localText, final String serverText) {
        final List<String> baseLines = splitLines(baseText);
        final List<String> localLines = splitLines(localText);
        final List<String> serverLines = splitLines(serverText);
        final List<Change> localChanges = changesFromBaseToVariant(baseText, localText);
        final List<Change> serverChanges = changesFromBaseToVariant(baseText, serverText);
        return new MergeContext(
                baseLines,
                localLines,
                serverLines,
                localChanges,
                serverChanges,
                buildBoundaryMaps(baseLines.size(), localChanges),
                buildBoundaryMaps(baseLines.size(), serverChanges)
        );
    }

    private static HunkStart ensureHunkStart(final int hunkLocalStart, final int hunkServerStart,
                                             final int localIndex, final int serverIndex) {
        if (hunkLocalStart >= 0) {
            return new HunkStart(hunkLocalStart, hunkServerStart);
        }
        return new HunkStart(localIndex, serverIndex);
    }

    private static CompareCursor advanceCompareCursor(final List<String> localLines, final List<String> serverLines,
                                                      final int[][] lcs, final int localIndex, final int serverIndex) {
        if (shouldAdvanceLocal(localLines, serverLines, lcs, localIndex, serverIndex)) {
            return new CompareCursor(localIndex + 1, serverIndex);
        }
        return new CompareCursor(localIndex, serverIndex + 1);
    }

    private static List<Change> changesFromBaseToVariant(final String baseText, final String variantText) {
        final ArrayList<Change> changes = new ArrayList<>();
        for (final DiffHunk hunk : compare(baseText, variantText).hunks()) {
            changes.add(Change.fromBaseDiff(hunk));
        }
        return List.copyOf(changes);
    }

    private static boolean linesEqual(final List<String> localLines, final List<String> serverLines,
                                      final int localIndex, final int serverIndex) {
        return localIndex < localLines.size()
                && serverIndex < serverLines.size()
                && localLines.get(localIndex).equals(serverLines.get(serverIndex));
    }

    private static boolean shouldAdvanceLocal(final List<String> localLines, final List<String> serverLines, final int[][] lcs,
                                              final int localIndex, final int serverIndex) {
        if (localIndex >= localLines.size()) {
            return false;
        }
        if (serverIndex >= serverLines.size()) {
            return true;
        }
        return lcs[localIndex + 1][serverIndex] >= lcs[localIndex][serverIndex + 1];
    }

    private static Change changeAt(final List<Change> changes, final int index) {
        return index < changes.size() ? changes.get(index) : null;
    }

    private static Change requireChange(final Change change, final String label) {
        if (change == null) {
            throw new IllegalStateException("Missing " + label + " change for merge branch.");
        }
        return change;
    }

    private static MergeBranch selectMergeBranch(final Change localChange, final Change serverChange) {
        if (localChange == null) {
            return MergeBranch.SERVER_ONLY;
        }
        if (serverChange == null) {
            return MergeBranch.LOCAL_ONLY;
        }
        if (localChange.identicalTo(serverChange)) {
            return MergeBranch.IDENTICAL;
        }
        if (isStrictlyBefore(localChange, serverChange)) {
            return MergeBranch.LOCAL_ONLY;
        }
        if (isStrictlyBefore(serverChange, localChange)) {
            return MergeBranch.SERVER_ONLY;
        }
        return MergeBranch.CONFLICT;
    }

    private static BoundaryMaps buildBoundaryMaps(final int baseLineCount, final List<Change> changes) {
        final int[] startPositions = new int[baseLineCount + 1];
        final int[] endPositions = new int[baseLineCount + 1];
        Arrays.fill(startPositions, -1);
        Arrays.fill(endPositions, -1);

        int baseCursor = 0;
        int variantCursor = 0;
        for (final Change change : changes) {
            final BoundaryCursor cursor = recordUnchangedBoundaries(startPositions, endPositions, baseCursor, variantCursor, change.baseStartLine());
            variantCursor = cursor.variantCursor();
            recordChangeStart(startPositions, endPositions, change.baseStartLine(), variantCursor);
            variantCursor = change.variantEndLineExclusive();
            baseCursor = change.baseEndLineExclusive();
            recordChangeEnd(startPositions, endPositions, change, baseCursor, variantCursor);
        }

        final BoundaryCursor finalCursor = recordUnchangedBoundaries(startPositions, endPositions, baseCursor, variantCursor, baseLineCount);
        variantCursor = finalCursor.variantCursor();

        if (startPositions[baseLineCount] < 0) {
            startPositions[baseLineCount] = variantCursor;
        }
        if (endPositions[baseLineCount] < 0) {
            endPositions[baseLineCount] = variantCursor;
        }
        return new BoundaryMaps(startPositions, endPositions);
    }

    private static BoundaryCursor recordUnchangedBoundaries(final int[] startPositions, final int[] endPositions,
                                                            final int baseCursor, final int variantCursor,
                                                            final int targetBoundary) {
        int currentBaseCursor = baseCursor;
        int currentVariantCursor = variantCursor;
        while (currentBaseCursor < targetBoundary) {
            startPositions[currentBaseCursor] = currentVariantCursor;
            endPositions[currentBaseCursor] = currentVariantCursor;
            currentBaseCursor++;
            currentVariantCursor++;
        }
        return new BoundaryCursor(currentBaseCursor, currentVariantCursor);
    }

    private static void recordChangeStart(final int[] startPositions, final int[] endPositions,
                                          final int baseStartLine, final int variantCursor) {
        if (startPositions[baseStartLine] < 0) {
            startPositions[baseStartLine] = variantCursor;
        }
        if (endPositions[baseStartLine] < 0) {
            endPositions[baseStartLine] = variantCursor;
        }
    }

    private static void recordChangeEnd(final int[] startPositions, final int[] endPositions, final Change change,
                                        final int baseCursor, final int variantCursor) {
        endPositions[baseCursor] = variantCursor;
        if (change.baseStartLine() != baseCursor && startPositions[baseCursor] < 0) {
            startPositions[baseCursor] = variantCursor;
        }
    }

    private static boolean isStrictlyBefore(final Change first, final Change second) {
        if (first == null) {
            return false;
        }
        if (second == null) {
            return true;
        }

        if (first.baseStartLine() < second.baseStartLine()) {
            if (first.insertionOnly()) {
                return !positionStrictlyInside(second, first.baseStartLine());
            }
            return first.baseEndLineExclusive() <= second.baseStartLine();
        }
        if (first.baseStartLine() > second.baseStartLine()) {
            return false;
        }
        if (first.insertionOnly() && second.insertionOnly()) {
            return false;
        }
        if (first.insertionOnly()) {
            return !positionStrictlyInside(second, first.baseStartLine());
        }
        return false;
    }

    private static boolean positionStrictlyInside(final Change change, final int boundary) {
        return !change.insertionOnly() && boundary > change.baseStartLine() && boundary < change.baseEndLineExclusive();
    }

    private static ConflictCluster expandConflictCluster(final int localIndex, final int serverIndex, final MergeContext mergeContext) {
        int clusterStart = Math.min(mergeContext.localChanges().get(localIndex).baseStartLine(), mergeContext.serverChanges().get(serverIndex).baseStartLine());
        int clusterEnd = Math.max(mergeContext.localChanges().get(localIndex).baseEndLineExclusive(), mergeContext.serverChanges().get(serverIndex).baseEndLineExclusive());
        int nextLocalIndex = localIndex;
        int nextServerIndex = serverIndex;

        boolean expanded;
        do {
            expanded = false;
            while (nextLocalIndex < mergeContext.localChanges().size()
                    && changeTouchesCluster(mergeContext.localChanges().get(nextLocalIndex), clusterStart, clusterEnd)) {
                final int newEnd = Math.max(clusterEnd, mergeContext.localChanges().get(nextLocalIndex).baseEndLineExclusive());
                expanded |= nextLocalIndex != localIndex || newEnd != clusterEnd;
                clusterEnd = newEnd;
                nextLocalIndex++;
            }
            while (nextServerIndex < mergeContext.serverChanges().size()
                    && changeTouchesCluster(mergeContext.serverChanges().get(nextServerIndex), clusterStart, clusterEnd)) {
                final int newEnd = Math.max(clusterEnd, mergeContext.serverChanges().get(nextServerIndex).baseEndLineExclusive());
                expanded |= nextServerIndex != serverIndex || newEnd != clusterEnd;
                clusterEnd = newEnd;
                nextServerIndex++;
            }
        } while (expanded);

        final int localStartLine = mergeContext.localBoundaries().start(clusterStart);
        final int localEndLineExclusive = mergeContext.localBoundaries().end(clusterEnd);
        final int serverStartLine = mergeContext.serverBoundaries().start(clusterStart);
        final int serverEndLineExclusive = mergeContext.serverBoundaries().end(clusterEnd);
        return new ConflictCluster(
                clusterStart,
                clusterEnd,
                localStartLine,
                localEndLineExclusive,
                serverStartLine,
                serverEndLineExclusive,
                List.copyOf(mergeContext.baseLines().subList(clusterStart, clusterEnd)),
                List.copyOf(mergeContext.localLines().subList(localStartLine, localEndLineExclusive)),
                List.copyOf(mergeContext.serverLines().subList(serverStartLine, serverEndLineExclusive)),
                nextLocalIndex,
                nextServerIndex
        );
    }

    private static boolean changeTouchesCluster(final Change change, final int clusterStart, final int clusterEnd) {
        if (change.insertionOnly()) {
            if (clusterStart == clusterEnd) {
                return change.baseStartLine() == clusterStart;
            }
            return change.baseStartLine() > clusterStart && change.baseStartLine() < clusterEnd;
        }
        return change.baseStartLine() < clusterEnd && change.baseEndLineExclusive() > clusterStart;
    }

    private static ResolvedCluster resolveConflictCluster(final ConflictCluster cluster) {
        if (cluster.localSegment().equals(cluster.serverSegment())) {
            return new ResolvedCluster(cluster.localSegment(), List.of(), 0);
        }
        if (!clusterHasLineWiseResolution(cluster)) {
            return new ResolvedCluster(
                    cluster.localSegment(),
                List.of(ConflictAccumulator.createConflict(
                    0,
                    0,
                    0,
                    cluster.localSegment(),
                    cluster.serverSegment(),
                    cluster.baseSegment())),
                    0
            );
        }

        final ArrayList<String> mergedSegment = new ArrayList<>();
        final ArrayList<RelativeConflict> conflicts = new ArrayList<>();
        final ConflictAccumulator conflictAccumulator = new ConflictAccumulator();
        int autoMergedServerChangeCount = 0;
        boolean previousServerOnlyLine = false;

        for (int index = 0; index < cluster.baseSegment().size(); index++) {
            final String baseLine = cluster.baseSegment().get(index);
            final String localLine = cluster.localSegment().get(index);
            final String serverLine = cluster.serverSegment().get(index);

            switch (classifyClusterLine(baseLine, localLine, serverLine)) {
                case SAME -> {
                    conflictAccumulator.flushInto(conflicts);
                    mergedSegment.add(localLine);
                    previousServerOnlyLine = false;
                }
                case SERVER_ONLY -> {
                    conflictAccumulator.flushInto(conflicts);
                    mergedSegment.add(serverLine);
                    if (!previousServerOnlyLine) {
                        autoMergedServerChangeCount++;
                    }
                    previousServerOnlyLine = true;
                }
                case LOCAL_ONLY -> {
                    conflictAccumulator.flushInto(conflicts);
                    mergedSegment.add(localLine);
                    previousServerOnlyLine = false;
                }
                case CONFLICT -> {
                    conflictAccumulator.beginIfNeeded(mergedSegment.size(), index);
                    mergedSegment.add(localLine);
                    conflictAccumulator.append(localLine, serverLine, baseLine);
                    previousServerOnlyLine = false;
                }
            }
        }

        conflictAccumulator.flushInto(conflicts);

        return new ResolvedCluster(mergedSegment, List.copyOf(conflicts), autoMergedServerChangeCount);
    }

    private static ClusterLineResolution classifyClusterLine(final String baseLine, final String localLine, final String serverLine) {
        if (localLine.equals(serverLine)) {
            return ClusterLineResolution.SAME;
        }
        if (localLine.equals(baseLine)) {
            return ClusterLineResolution.SERVER_ONLY;
        }
        if (serverLine.equals(baseLine)) {
            return ClusterLineResolution.LOCAL_ONLY;
        }
        return ClusterLineResolution.CONFLICT;
    }

    private static boolean clusterHasLineWiseResolution(final ConflictCluster cluster) {
        return cluster.baseSegment().size() == cluster.localSegment().size()
                && cluster.baseSegment().size() == cluster.serverSegment().size();
    }

    private static int changeStart(final Change change) {
        return change == null ? Integer.MAX_VALUE : change.baseStartLine();
    }

    private static DiffHunk createHunk(final List<String> localLines, final List<String> serverLines,
                                       final int localStart, final int localEndExclusive,
                                       final int serverStart, final int serverEndExclusive) {
        return new DiffHunk(
                localStart,
                localEndExclusive,
                serverStart,
                serverEndExclusive,
                List.copyOf(localLines.subList(localStart, localEndExclusive)),
                List.copyOf(serverLines.subList(serverStart, serverEndExclusive))
        );
    }

    private static int[][] lcsLengths(final List<String> localLines, final List<String> serverLines) {
        final int[][] lengths = new int[localLines.size() + 1][serverLines.size() + 1];
        for (int localIndex = localLines.size() - 1; localIndex >= 0; localIndex--) {
            for (int serverIndex = serverLines.size() - 1; serverIndex >= 0; serverIndex--) {
                lengths[localIndex][serverIndex] = localLines.get(localIndex).equals(serverLines.get(serverIndex))
                        ? lengths[localIndex + 1][serverIndex + 1] + 1
                        : Math.max(lengths[localIndex + 1][serverIndex], lengths[localIndex][serverIndex + 1]);
            }
        }
        return lengths;
    }

    private static List<String> splitLines(final String text) {
        final String normalized = normalize(text);
        return List.of(normalized.split("\\n", -1));
    }

    private static String joinLines(final List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    private static String normalize(final String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    public record DiffResult(List<DiffHunk> hunks, List<String> localLines, List<String> serverLines) {
        public DiffResult {
            hunks = hunks == null ? List.of() : List.copyOf(hunks);
            localLines = localLines == null || localLines.isEmpty() ? List.of("") : List.copyOf(localLines);
            serverLines = serverLines == null || serverLines.isEmpty() ? List.of("") : List.copyOf(serverLines);
        }

        public boolean identical() {
            return this.hunks.isEmpty();
        }

        public int clampSelection(final int index) {
            if (this.hunks.isEmpty()) {
                return 0;
            }
            return Math.max(0, Math.min(index, this.hunks.size() - 1));
        }

        public DiffHunk selectedHunk(final int index) {
            if (this.hunks.isEmpty()) {
                return null;
            }
            return this.hunks.get(this.clampSelection(index));
        }
    }

    public record DiffHunk(int localStartLine, int localEndLineExclusive, int serverStartLine, int serverEndLineExclusive,
                           List<String> localLines, List<String> serverLines) {
        public DiffHunk {
            localLines = localLines == null ? List.of() : List.copyOf(localLines);
            serverLines = serverLines == null ? List.of() : List.copyOf(serverLines);
        }

        public int localLineCount() {
            return this.localEndLineExclusive - this.localStartLine;
        }

        public int serverLineCount() {
            return this.serverEndLineExclusive - this.serverStartLine;
        }

        public boolean insertionOnly() {
            return this.localLineCount() == 0 && this.serverLineCount() > 0;
        }

        public boolean deletionOnly() {
            return this.localLineCount() > 0 && this.serverLineCount() == 0;
        }
    }

    public record MergeResult(String mergedText, List<MergeConflict> conflicts, int autoMergedServerChangeCount) {
        public MergeResult {
            mergedText = normalize(mergedText);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
            autoMergedServerChangeCount = Math.max(0, autoMergedServerChangeCount);
        }

        public boolean hasConflicts() {
            return !this.conflicts.isEmpty();
        }

        public int clampSelection(final int index) {
            if (this.conflicts.isEmpty()) {
                return 0;
            }
            return Math.max(0, Math.min(index, this.conflicts.size() - 1));
        }

        public MergeConflict selectedConflict(final int index) {
            if (this.conflicts.isEmpty()) {
                return null;
            }
            return this.conflicts.get(this.clampSelection(index));
        }
    }

    public record MergeConflict(int mergedStartLine, int mergedEndLineExclusive, int serverStartLine, int serverEndLineExclusive,
                                int baseStartLine, int baseEndLineExclusive, List<String> localLines,
                                List<String> serverLines, List<String> baseLines) {
        public MergeConflict {
            localLines = localLines == null ? List.of() : List.copyOf(localLines);
            serverLines = serverLines == null ? List.of() : List.copyOf(serverLines);
            baseLines = baseLines == null ? List.of() : List.copyOf(baseLines);
        }

        public int localLineCount() {
            return this.mergedEndLineExclusive - this.mergedStartLine;
        }

        public int serverLineCount() {
            return this.serverEndLineExclusive - this.serverStartLine;
        }

        public int baseLineCount() {
            return this.baseEndLineExclusive - this.baseStartLine;
        }

        public boolean localInsertionOnly() {
            return this.localLineCount() == 0 && this.serverLineCount() > 0;
        }

        public boolean serverInsertionOnly() {
            return this.localLineCount() > 0 && this.serverLineCount() == 0;
        }
    }

    private record Change(int baseStartLine, int baseEndLineExclusive, int variantStartLine, int variantEndLineExclusive,
                          List<String> baseLines, List<String> variantLines) {
        private static Change fromBaseDiff(final DiffHunk hunk) {
            return new Change(
                    hunk.localStartLine(),
                    hunk.localEndLineExclusive(),
                    hunk.serverStartLine(),
                    hunk.serverEndLineExclusive(),
                    hunk.localLines(),
                    hunk.serverLines()
            );
        }

        private boolean insertionOnly() {
            return this.baseStartLine == this.baseEndLineExclusive;
        }

        private boolean identicalTo(final Change other) {
            return other != null
                    && this.baseStartLine == other.baseStartLine
                    && this.baseEndLineExclusive == other.baseEndLineExclusive
                    && this.variantLines.equals(other.variantLines);
        }
    }

    private static final class BoundaryMaps {
        private final int[] startPositions;
        private final int[] endPositions;

        private BoundaryMaps(final int[] startPositions, final int[] endPositions) {
            this.startPositions = startPositions;
            this.endPositions = endPositions;
        }

        private int start(final int boundary) {
            return this.startPositions[boundary];
        }

        private int end(final int boundary) {
            return this.endPositions[boundary];
        }
    }

    private record BoundaryCursor(int baseCursor, int variantCursor) {
    }

    private record CompareCursor(int localIndex, int serverIndex) {
    }

    private record HunkStart(int localStart, int serverStart) {
    }

    private enum MergeBranch {
        LOCAL_ONLY,
        SERVER_ONLY,
        IDENTICAL,
        CONFLICT
    }

    private record MergeContext(List<String> baseLines, List<String> localLines, List<String> serverLines,
                                List<Change> localChanges, List<Change> serverChanges,
                                BoundaryMaps localBoundaries, BoundaryMaps serverBoundaries) {
    }

    private record RelativeConflict(int mergedStartLine, int mergedEndLineExclusive, int serverStartLine,
                                    int serverEndLineExclusive, int baseStartLine, int baseEndLineExclusive,
                                    List<String> localLines, List<String> serverLines, List<String> baseLines) {
    }

    private record ResolvedCluster(List<String> mergedSegment, List<RelativeConflict> conflicts,
                                   int autoMergedServerChangeCount) {
    }

    private enum ClusterLineResolution {
        SAME,
        SERVER_ONLY,
        LOCAL_ONLY,
        CONFLICT
    }

    private static final class ConflictAccumulator {
        private int mergedStartLine = -1;
        private int serverStartLine = -1;
        private int baseStartLine = -1;
        private final ArrayList<String> localLines = new ArrayList<>();
        private final ArrayList<String> serverLines = new ArrayList<>();
        private final ArrayList<String> baseLines = new ArrayList<>();

        private void beginIfNeeded(final int mergedStartLine, final int relativeLineIndex) {
            if (this.mergedStartLine >= 0) {
                return;
            }
            this.mergedStartLine = mergedStartLine;
            this.serverStartLine = relativeLineIndex;
            this.baseStartLine = relativeLineIndex;
        }

        private void append(final String localLine, final String serverLine, final String baseLine) {
            this.localLines.add(localLine);
            this.serverLines.add(serverLine);
            this.baseLines.add(baseLine);
        }

        private void flushInto(final List<RelativeConflict> conflicts) {
            if (this.mergedStartLine < 0) {
                return;
            }
            conflicts.add(createConflict(this.mergedStartLine, this.serverStartLine, this.baseStartLine,
                    this.localLines, this.serverLines, this.baseLines));
            this.mergedStartLine = -1;
            this.serverStartLine = -1;
            this.baseStartLine = -1;
            this.localLines.clear();
            this.serverLines.clear();
            this.baseLines.clear();
        }

        private static RelativeConflict createConflict(final int mergedStartLine, final int serverStartLine,
                                                       final int baseStartLine, final List<String> localLines,
                                                       final List<String> serverLines, final List<String> baseLines) {
            return new RelativeConflict(
                    mergedStartLine,
                    mergedStartLine + localLines.size(),
                    serverStartLine,
                    serverStartLine + serverLines.size(),
                    baseStartLine,
                    baseStartLine + baseLines.size(),
                    List.copyOf(localLines),
                    List.copyOf(serverLines),
                    List.copyOf(baseLines)
            );
        }
    }

    private record ConflictCluster(int baseStartLine, int baseEndLineExclusive, int localStartLine, int localEndLineExclusive,
                                   int serverStartLine, int serverEndLineExclusive, List<String> baseSegment,
                                   List<String> localSegment, List<String> serverSegment,
                                   int nextLocalIndex, int nextServerIndex) {
    }
}