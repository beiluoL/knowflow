package com.knowflow.interview.service.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证追问 JSON 的增量提取和跨令牌转义。
 */
class FollowupQuestionDeltaTest {
    @Test void emitsIncrementallyBeforeObjectIsCompleteAndNeverStreamsEvaluation() {
        var emitted = new ArrayList<String>();
        var decoder = new FollowupQuestionDelta(new ObjectMapper(), emitted::add);
        decoder.accept("{\"evaluation\":{\"secret\":\"reference\"},\"followupQuestion\":\"Why");
        assertEquals("Why", String.join("", emitted));
        decoder.accept(" now?\",\"decision\":\"FOLLOW_UP\"}");
        assertEquals("Why now?", String.join("", emitted));
    }
    @Test void handlesEscapesAcrossTokensWithoutReplayingCharacters() {
        var emitted = new ArrayList<String>();
        var decoder = new FollowupQuestionDelta(new ObjectMapper(), emitted::add);
        decoder.accept("{\"followupQuestion\":\"a\\u4");
        assertEquals("a", String.join("", emitted));
        decoder.accept("e2d\\\"b?\"}");
        assertEquals("a中\"b?", String.join("", emitted));
    }
}
