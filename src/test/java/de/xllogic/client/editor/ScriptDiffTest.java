package de.xllogic.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptDiffTest {
    private static final String BASE_SCRIPT = "print('base')\nprint('shared')\nprint('tail')";
    private static final String LOCAL_CONFLICT_SCRIPT = "print('local')\nprint('local-shared')\nprint('tail')";

    @Test
    void identicalScriptsProduceNoHunks() {
        final ScriptDiff.DiffResult diff = ScriptDiff.compare("print('same')\n", "print('same')\n");

        assertTrue(diff.identical());
        assertEquals(0, diff.hunks().size());
    }

    @Test
    void compareGroupsReplacementAndInsertionIntoOneHunk() {
        final ScriptDiff.DiffResult diff = ScriptDiff.compare(
                "print('alpha')\nprint('local')\nprint('omega')",
                "print('alpha')\nprint('server-a')\nprint('server-b')\nprint('omega')");

        assertEquals(1, diff.hunks().size());
        final ScriptDiff.DiffHunk hunk = diff.hunks().getFirst();
        assertEquals(1, hunk.localStartLine());
        assertEquals(2, hunk.localEndLineExclusive());
        assertEquals(1, hunk.serverStartLine());
        assertEquals(3, hunk.serverEndLineExclusive());
        assertIterableEquals(List.of("print('local')"), hunk.localLines());
        assertIterableEquals(List.of("print('server-a')", "print('server-b')"), hunk.serverLines());
    }

    @Test
    void applyServerHunkSupportsSelectiveMerge() {
        final String localScript = "print('alpha')\nprint('local-one')\nprint('shared')\nprint('local-two')\nprint('omega')";
        final String serverScript = "print('alpha')\nprint('server-one')\nprint('shared')\nprint('server-two')\nprint('omega')";
        final ScriptDiff.DiffResult diff = ScriptDiff.compare(localScript, serverScript);

        assertEquals(2, diff.hunks().size());
        final String merged = ScriptDiff.applyServerHunk(localScript, diff.hunks().getFirst());

        assertEquals("print('alpha')\nprint('server-one')\nprint('shared')\nprint('local-two')\nprint('omega')", merged);
    }

    @Test
    void threeWayMergeAutoAppliesNonConflictingServerChanges() {
        final ScriptDiff.MergeResult merge = ScriptDiff.merge(
                BASE_SCRIPT,
                "print('local')\nprint('shared')\nprint('tail')",
                "print('base')\nprint('server')\nprint('tail')");

        assertEquals("print('local')\nprint('server')\nprint('tail')", merge.mergedText());
        assertEquals(1, merge.autoMergedServerChangeCount());
        assertTrue(merge.conflicts().isEmpty());
    }

    @Test
    void threeWayMergeKeepsOnlyTrueConflictsOpen() {
        final ScriptDiff.MergeResult merge = ScriptDiff.merge(
                BASE_SCRIPT,
                LOCAL_CONFLICT_SCRIPT,
                "print('base')\nprint('server-shared')\nprint('tail')");

        assertTrue(merge.hasConflicts());
        assertEquals(1, merge.conflicts().size());
        assertEquals(LOCAL_CONFLICT_SCRIPT, merge.mergedText());

        final ScriptDiff.MergeConflict conflict = merge.conflicts().getFirst();
        assertEquals(1, conflict.mergedStartLine());
        assertEquals(2, conflict.mergedEndLineExclusive());
        assertIterableEquals(List.of("print('shared')"), conflict.baseLines());
        assertIterableEquals(List.of("print('local-shared')"), conflict.localLines());
        assertIterableEquals(List.of("print('server-shared')"), conflict.serverLines());
    }

    @Test
    void applyServerConflictSupportsSelectiveThreeWayResolution() {
        final ScriptDiff.MergeResult merge = ScriptDiff.merge(
                BASE_SCRIPT,
                LOCAL_CONFLICT_SCRIPT,
                "print('base')\nprint('server-shared')\nprint('tail')");

        final String resolved = ScriptDiff.applyServerConflict(merge.mergedText(), merge.conflicts().getFirst());

        assertEquals("print('local')\nprint('server-shared')\nprint('tail')", resolved);
    }
}