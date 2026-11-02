package com.knowflow.interview.speech;

import com.knowflow.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;

/** 使用实际准入与隔离 H2 验证轮次绑定、权限和幂等，不触发任何云端调用。 */
class InterviewStreamingTranscriptServiceTest {
    private static final String AUDIO_HASH = "a".repeat(64);
    private JdbcTemplate jdbc;
    private InterviewStreamingTranscriptService service;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:stream_final_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE mock_interview_session(id BIGINT PRIMARY KEY,user_id BIGINT,"
                + "deleted INT,status VARCHAR(30))");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/h2/migration/V20261016__interview_speech.sql")).execute(dataSource);
        jdbc.execute("CREATE TABLE mock_interview_v2_state(session_id BIGINT PRIMARY KEY,user_id BIGINT,"
                + "state VARCHAR(30),current_question_id VARCHAR(100),followup_depth INT,"
                + "version BIGINT,current_prompt VARCHAR(1000),deadline TIMESTAMP DEFAULT DATEADD('DAY',1,CURRENT_TIMESTAMP))");
        jdbc.execute("CREATE TABLE mock_interview_v2_turn(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "speech_id BIGINT)");
        jdbc.update("INSERT INTO mock_interview_session VALUES(1,7,0,'IN_PROGRESS')");
        jdbc.update("INSERT INTO mock_interview_v2_state(session_id,user_id,state,current_question_id,followup_depth,version,current_prompt) VALUES(1,7,'FOLLOW_UP','q1',1,4,'完整追问题干')");
        service = new InterviewStreamingTranscriptService(jdbc, new SpeechV2Admission(jdbc),
                new DataSourceTransactionManager(dataSource));
    }

    private InterviewSpeechService.SpeechTranscript save() {
        return service.saveFinal(7L, 1L, "q1", "request01", 4L, "真实最终转写", AUDIO_HASH,
                1000L, "test-provider");
    }

    @Test
    void persistsCandidateWithActualCurrentRoundBindingAndReplaysOnce() {
        var result = save();
        assertEquals("TRANSCRIBED", result.status());
        assertEquals("真实最终转写", result.rawText());
        assertEquals(result, save());
        var row = jdbc.queryForMap("SELECT v2_version,v2_depth,v2_prompt_hash,duration_ms,"
                + "provider FROM mock_interview_speech WHERE id=?", result.id());
        assertEquals(4L, ((Number) row.get("v2_version")).longValue());
        assertEquals(1, ((Number) row.get("v2_depth")).intValue());
        assertEquals(SpeechV2Admission.promptHash("完整追问题干"), row.get("v2_prompt_hash"));
        assertEquals(1000L, ((Number) row.get("duration_ms")).longValue());
        assertEquals("test-provider", row.get("provider"));
        assertEquals(1, count());
    }

    @Test
    void changedTextAudioOrRoundCannotReuseRequestKey() {
        save();
        assertCode(409, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L, "不同文本",
                AUDIO_HASH, 1000L, "test-provider"));
        assertCode(409, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L, "真实最终转写",
                "b".repeat(64), 1000L, "test-provider"));
        assertCode(409, () -> service.saveFinal(7L, 1L, "q1", "request01", 5L, "真实最终转写",
                AUDIO_HASH, 1000L, "test-provider"));
        assertEquals(1, count());
    }

    @Test
    void actualAdmissionRejectsStaleVersionDifferentQuestionPausedAndLegacySession() {
        jdbc.update("UPDATE mock_interview_v2_state SET version=5");
        assertCode(409, this::save);
        jdbc.update("UPDATE mock_interview_v2_state SET version=4,current_question_id='q2'");
        assertCode(409, this::save);
        jdbc.update("UPDATE mock_interview_v2_state SET current_question_id='q1',state='PAUSED'");
        assertCode(409, this::save);
        jdbc.update("DELETE FROM mock_interview_v2_state");
        assertCode(409, this::save);
        assertEquals(0, count());
    }

    @Test
    void anonymousOtherUserDeletedAndFinishedSessionCannotPersist() {
        assertCode(401, () -> service.saveFinal(null, 1L, "q1", "request01", 4L,
                "文本", AUDIO_HASH, 1000L, "test-provider"));
        assertCode(404, () -> service.saveFinal(8L, 1L, "q1", "request01", 4L,
                "文本", AUDIO_HASH, 1000L, "test-provider"));
        jdbc.update("UPDATE mock_interview_session SET deleted=1");
        assertCode(404, this::save);
        jdbc.update("UPDATE mock_interview_session SET deleted=0,status='FINISHED'");
        assertCode(409, this::save);
        assertEquals(0, count());
    }

    @Test
    void rejectsMalformedBoundariesWithoutWriting() {
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "short", 4L,
                "文本", AUDIO_HASH, 1000L, "test-provider"));
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L,
                " ", AUDIO_HASH, 1000L, "test-provider"));
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L,
                "x".repeat(8001), AUDIO_HASH, 1000L, "test-provider"));
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L,
                "文本", "invalid", 1000L, "test-provider"));
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L,
                "文本", AUDIO_HASH, 199L, "test-provider"));
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L,
                "文本", AUDIO_HASH, 120001L, "test-provider"));
        assertCode(400, () -> service.saveFinal(7L, 1L, "q1", "request01", 4L,
                "文本", AUDIO_HASH, 1000L, "Bearer secret"));
        assertEquals(0, count());
    }

    @Test
    void persistentReservationPreventsSecondOpenAndFinalizesSameRow() {
        var first = service.begin(7L, 1L, "q1", "request01", 4L, "test-provider");
        assertTrue(first.created());
        assertEquals("TRANSCRIBING", first.transcript().status());
        assertFalse(service.begin(7L, 1L, "q1", "request01", 4L, "test-provider").created());
        var result = save();
        assertEquals(first.transcript().id(), result.id());
        assertEquals(result, save());
        jdbc.update("UPDATE mock_interview_v2_state SET version=6,current_question_id='q2'");
        var replay = service.begin(7L, 1L, "q1", "request01", 4L, "test-provider");
        assertFalse(replay.created());
        assertEquals(result, replay.transcript());
        assertCode(409, () -> service.begin(7L, 1L, "q1", "request01", 6L, "test-provider"));
        assertEquals(1, count());
    }

    @Test
    void failSanitizesErrorAndCannotOverwriteCompletedTranscriptOrForeignReservation() {
        service.begin(7L, 1L, "q1", "request01", 4L, "test-provider");
        service.fail(8L, 1L, "request01", "Bearer secret");
        assertEquals("TRANSCRIBING", service.begin(7L, 1L, "q1", "request01", 4L,
                "test-provider").transcript().status());
        service.fail(7L, 1L, "request01", "Bearer secret");
        var failed = service.begin(7L, 1L, "q1", "request01", 4L, "test-provider");
        assertFalse(failed.created());
        assertEquals("FAILED", failed.transcript().status());
        assertFalse(failed.transcript().error().contains("secret"));
        assertCode(409, this::save);
        jdbc.update("DELETE FROM mock_interview_speech");
        var completed = save();
        service.fail(7L, 1L, "request01", "CANCELLED");
        assertEquals(completed, save());
    }

    @Test
    void reservationCannotFinalizeAfterRoundChangesAndLeavesNoFinalText() {
        service.begin(7L, 1L, "q1", "request01", 4L, "test-provider");
        jdbc.update("UPDATE mock_interview_v2_state SET version=5");
        assertCode(409, this::save);
        var row = jdbc.queryForMap("SELECT status,raw_text FROM mock_interview_speech WHERE request_id=?",
                "request01");
        assertEquals("TRANSCRIBING", row.get("status"));
        assertNull(row.get("raw_text"));
    }

    @Test
    void restoresOnlyLatestUnconsumedRealStreamingFinalInCurrentContext() {
        assertNull(service.findPendingFinal(7L, 1L, "q1", 4L));
        var first = service.saveFinal(7L, 1L, "q1", "restore01", 4L, "第一条最终文本",
                AUDIO_HASH, 1000L, "QWEN_REALTIME_ASR");
        var second = service.saveFinal(7L, 1L, "q1", "restore02", 4L, "第二条最终文本",
                AUDIO_HASH, 1000L, "QWEN_REALTIME_ASR");
        save(); // 更晚的上传/其他 provider 转写不能覆盖真实流式恢复。
        var restored = service.findPendingFinal(7L, 1L, "q1", 4L);
        assertEquals(second, restored.transcript());
        assertEquals("restore02", restored.requestId());
        assertEquals(4L, restored.version());
        jdbc.update("INSERT INTO mock_interview_v2_turn(speech_id) VALUES(?)", second.id());
        assertEquals(first, service.findPendingFinal(7L, 1L, "q1", 4L).transcript());
        jdbc.update("INSERT INTO mock_interview_v2_turn(speech_id) VALUES(?)", first.id());
        assertNull(service.findPendingFinal(7L, 1L, "q1", 4L));
    }

    @Test
    void restoreRejectsForeignActorAndOmitsStaleRoundOrPrompt() {
        service.saveFinal(7L, 1L, "q1", "restore01", 4L, "最终文本",
                AUDIO_HASH, 1000L, "QWEN_REALTIME_ASR");
        assertCode(404, () -> service.findPendingFinal(8L, 1L, "q1", 4L));
        jdbc.update("UPDATE mock_interview_v2_state SET version=5");
        assertNull(service.findPendingFinal(7L, 1L, "q1", 5L));
        assertNull(service.findPendingFinal(7L, 1L, "q1", 4L));
        jdbc.update("UPDATE mock_interview_v2_state SET version=4,current_prompt='另一个题干'");
        assertNull(service.findPendingFinal(7L, 1L, "q1", 4L));
    }

    @Test
    void databaseDeadlineRejectsReservationFinalizationAndRefreshRecovery() {
        service.begin(7L, 1L, "q1", "request01", 4L, "test-provider");
        jdbc.update("UPDATE mock_interview_v2_state SET deadline=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertCode(409, () -> service.begin(7L, 1L, "q1", "request02", 4L, "test-provider"));
        assertCode(409, this::save);
        assertCode(409, () -> service.findPendingFinal(7L, 1L, "q1", 4L));
        assertEquals(1, count());
        assertEquals("TRANSCRIBING", jdbc.queryForObject(
                "SELECT status FROM mock_interview_speech WHERE request_id=?", String.class, "request01"));
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_speech", Integer.class);
    }

    private void assertCode(int code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(BusinessException.class, operation).getCode());
    }
}
