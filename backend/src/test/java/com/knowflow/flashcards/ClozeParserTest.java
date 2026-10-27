package com.knowflow.flashcards;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClozeParserTest {
    @Test
    void multipleOrdinalsAndRepeatedHints() {
        var cards = ClozeParser.cards("volatile {{c1::可见性::提示}} 与 {{c2::有序性}}，{{c1::同步}}", "解释");
        assertEquals(2, cards.size());
        assertTrue(cards.get(1).front().contains("[提示]"));
        assertTrue(cards.get(1).front().contains("有序性"));
        assertFalse(cards.get(1).front().contains("可见性"));
        assertTrue(cards.get(1).back().contains("可见性"));
        assertEquals("volatile 可见性 与 [ … ]，同步", cards.get(2).front());
    }

    @Test
    void nestedBracesAndEscapes() {
        var c = ClozeParser.cards("{{c3::Map<String, {{nested}}>}} \\{{c4::literal}}", "");
        assertEquals(1, c.size());
        assertTrue(c.get(3).back().contains("{{nested}}"));
    }

    @Test
    void malformedInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClozeParser.cards("{{c1::missing", ""));
        assertThrows(
                IllegalArgumentException.class, () -> ClozeParser.cards("{{c0::invalid}}", ""));
        assertThrows(IllegalArgumentException.class, () -> ClozeParser.cards("no deletions", ""));
    }
}
