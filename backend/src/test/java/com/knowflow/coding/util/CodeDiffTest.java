package com.knowflow.coding.util;

import com.knowflow.coding.vo.CodingVos.SubmissionDiffVO.DiffLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3H 确定性行级 diff 纯算法测试（无 Spring、无 AI）。 */
class CodeDiffTest {

    private List<String> types(CodeDiff.Result r) {
        return r.lines().stream().map(DiffLine::getType).toList();
    }

    @Test
    void identicalSourcesAreAllEqual() {
        CodeDiff.Result r = CodeDiff.diff("a\nb\nc\n", "a\nb\nc\n");
        assertEquals(0, r.changedLines());
        assertFalse(r.truncated());
        assertEquals(List.of("EQUAL", "EQUAL", "EQUAL"), types(r));
    }

    @Test
    void addedLinesAreMarkedWithNewLineNumbers() {
        CodeDiff.Result r = CodeDiff.diff("a\nc\n", "a\nb\nc\n");
        assertEquals(1, r.changedLines());
        assertEquals(List.of("EQUAL", "ADDED", "EQUAL"), types(r));
        DiffLine added = r.lines().get(1);
        assertEquals("ADDED", added.getType());
        assertEquals("b", added.getText());
        assertNull(added.getOldLine());
        assertEquals(2, added.getNewLine());
    }

    @Test
    void changedBlockEmitsRemovedBeforeAdded() {
        CodeDiff.Result r = CodeDiff.diff("a\nx\nc\n", "a\ny\nc\n");
        assertEquals(2, r.changedLines());
        assertEquals(List.of("EQUAL", "REMOVED", "ADDED", "EQUAL"), types(r));
        DiffLine removed = r.lines().get(1);
        assertEquals(2, removed.getOldLine());
        assertNull(removed.getNewLine());
        DiffLine added = r.lines().get(2);
        assertNull(added.getOldLine());
        assertEquals(2, added.getNewLine());
    }

    @Test
    void emptySourcesProduceOnlyAddsOrRemovals() {
        CodeDiff.Result toEmpty = CodeDiff.diff("a\nb\n", "");
        assertEquals(2, toEmpty.changedLines());
        assertEquals(List.of("REMOVED", "REMOVED"), types(toEmpty));

        CodeDiff.Result fromEmpty = CodeDiff.diff("", "a\nb\n");
        assertEquals(2, fromEmpty.changedLines());
        assertEquals(List.of("ADDED", "ADDED"), types(fromEmpty));
    }

    @Test
    void crlfAndCrAreNormalizedToLf() {
        CodeDiff.Result r = CodeDiff.diff("a\r\nb\r\n", "a\nb\n");
        assertEquals(0, r.changedLines());
        assertEquals(List.of("EQUAL", "EQUAL"), types(r));
    }

    @Test
    void trailingNewlineDoesNotCreatePhantomBlankLine() {
        CodeDiff.Result r = CodeDiff.diff("a\n", "a");
        assertEquals(0, r.changedLines());
    }

    @Test
    void resultIsDeterministicAcrossRuns() {
        String oldCode = "class S {\n int v = 1;\n}\n";
        String newCode = "class S {\n int v = 2;\n int extra = 3;\n}\n";
        CodeDiff.Result first = CodeDiff.diff(oldCode, newCode);
        CodeDiff.Result second = CodeDiff.diff(oldCode, newCode);
        assertEquals(first.lines().size(), second.lines().size());
        for (int i = 0; i < first.lines().size(); i++) {
            DiffLine a = first.lines().get(i);
            DiffLine b = second.lines().get(i);
            assertEquals(a.getType(), b.getType());
            assertEquals(a.getText(), b.getText());
            assertEquals(a.getOldLine(), b.getOldLine());
            assertEquals(a.getNewLine(), b.getNewLine());
        }
    }

    @Test
    void oversizedSourcesAreTruncatedAndFlagged() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < CodeDiff.MAX_LINES + 50; i++) {
            big.append("line").append(i).append('\n');
        }
        CodeDiff.Result r = CodeDiff.diff(big.toString(), "x\n");
        assertTrue(r.truncated(), "超过行数上限必须标记 truncated");
    }
}
