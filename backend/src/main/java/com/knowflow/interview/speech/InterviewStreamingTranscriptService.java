package com.knowflow.interview.speech;

import com.knowflow.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 保存真实流式识别的最终候选文本；不提交答案，也不产生评分或学习证据。 */
@Service
public class InterviewStreamingTranscriptService {
    private static final int MAX_TEXT_LENGTH = 8000;
    private static final long MIN_DURATION_MS = 200L;
    private static final long MAX_DURATION_MS = 120000L;
    private static final String TRANSCRIPT_COLUMNS = "id,session_id,question_id,status,raw_text,error_message,request_hash,v2_version,provider";
    private final JdbcTemplate jdbc;
    private final SpeechV2Admission admission;
    private final TransactionTemplate transaction;

    public InterviewStreamingTranscriptService(JdbcTemplate jdbc, SpeechV2Admission admission,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.admission = admission;
        this.transaction = new TransactionTemplate(manager);
    }

    /**
     * actor 和 provider 必须由服务器认证上下文及已选择的 provider 提供。
     * 调用者须传入从收到的 PCM 计算的摘要/时长；本方法不接收音频，也不调用远端服务。
     */
    public InterviewSpeechService.SpeechTranscript saveFinal(Long user, long sid, String questionId,
            String requestId, long expectedVersion, String rawText, String pcmHash, long durationMs,
            String trustedProvider) {
        validate(user, sid, questionId, requestId, expectedVersion, rawText, pcmHash, durationMs,
                trustedProvider);
        String hash = requestHash(user, sid, questionId, expectedVersion, rawText,
                pcmHash.toLowerCase(java.util.Locale.ROOT), durationMs, trustedProvider);
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                var parents = jdbc.queryForList("SELECT status FROM mock_interview_session "
                        + "WHERE id=? AND user_id=? AND deleted=0 FOR UPDATE", sid, user);
                if (parents.isEmpty()) {
                    throw new BusinessException(404, "面试记录不存在");
                }
                if (!"IN_PROGRESS".equals(parents.get(0).get("status"))) {
                    throw new BusinessException(409, "面试已结束或不可用");
                }
                requireBeforeDeadline(user, sid);
                var previous = find(user, requestId);
                if (!previous.isEmpty() && !"TRANSCRIBING".equals(previous.get(0).get("status"))) {
                    return replay(previous.get(0), hash);
                }
                var binding = admission.requireAllowed(user, sid, questionId);
                if (binding.version() == null || binding.version() != expectedVersion) {
                    throw new BusinessException(409, "语音录入对应的面试轮次已失效");
                }
                if (!previous.isEmpty()) {
                    var reserved = previous.get(0);
                    String contextHash = requestHash(user, sid, questionId, expectedVersion, "", "", 0L,
                            trustedProvider);
                    if (!contextHash.equals(reserved.get("request_hash"))) {
                        throw new BusinessException(409, "请求标识已用于不同录音");
                    }
                    long id = ((Number) reserved.get("id")).longValue();
                    int changed = jdbc.update("UPDATE mock_interview_speech SET status='TRANSCRIBED',"
                            + "raw_text=?,request_hash=?,duration_ms=?,claimed_duration_ms=?,"
                            + "updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='TRANSCRIBING'",
                            rawText, hash, durationMs, durationMs, id);
                    if (changed != 1) {
                        throw new BusinessException(409, "语音转写状态已变化");
                    }
                    return new InterviewSpeechService.SpeechTranscript(id, questionId,
                            "TRANSCRIBED", rawText, null);
                }
                var key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("INSERT INTO mock_interview_speech "
                            + "(user_id,session_id,question_id,request_id,request_hash,status,raw_text,"
                            + "duration_ms,claimed_duration_ms,provider,v2_version,v2_depth,v2_prompt_hash,"
                            + "created_at,updated_at) VALUES(?,?,?,?,?,'TRANSCRIBED',?,?,?,?,?,?,?,"
                            + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", new String[]{"id"});
                    statement.setLong(1, user);
                    statement.setLong(2, sid);
                    statement.setString(3, questionId);
                    statement.setString(4, requestId);
                    statement.setString(5, hash);
                    statement.setString(6, rawText);
                    statement.setLong(7, durationMs);
                    statement.setLong(8, durationMs);
                    statement.setString(9, trustedProvider);
                    statement.setLong(10, binding.version());
                    statement.setObject(11, binding.depth());
                    statement.setString(12, binding.promptHash());
                    return statement;
                }, key);
                return new InterviewSpeechService.SpeechTranscript(
                        Objects.requireNonNull(key.getKey()).longValue(), questionId, "TRANSCRIBED", rawText, null);
            }));
        } catch (DuplicateKeyException exception) {
            // 不同父会话下使用同一用户幂等键时仍由唯一索引兜底；失败事务已回滚。
            var previous = find(user, requestId);
            if (previous.isEmpty()) {
                throw exception;
            }
            return replay(previous.get(0), hash);
        }
    }

    /** 新预留才允许开启付费 ASR；历史预留与完成记录只供调用方重放状态。 */
    public record Reservation(InterviewSpeechService.SpeechTranscript transcript, boolean created) { }

    public Reservation begin(Long user, long sid, String questionId, String requestId,
            long expectedVersion, String trustedProvider) {
        validate(user, sid, questionId, requestId, expectedVersion, "reservation", "a".repeat(64),
                MIN_DURATION_MS, trustedProvider);
        String hash = requestHash(user, sid, questionId, expectedVersion, "", "", 0L, trustedProvider);
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                var parents = jdbc.queryForList("SELECT status FROM mock_interview_session "
                        + "WHERE id=? AND user_id=? AND deleted=0 FOR UPDATE", sid, user);
                if (parents.isEmpty()) {
                    throw new BusinessException(404, "面试记录不存在");
                }
                if (!"IN_PROGRESS".equals(parents.get(0).get("status"))) {
                    throw new BusinessException(409, "面试已结束或不可用");
                }
                requireBeforeDeadline(user, sid);
                var previous = find(user, requestId);
                if (!previous.isEmpty()) {
                    return reservedReplay(previous.get(0), sid, questionId, expectedVersion, trustedProvider);
                }
                var binding = admission.requireAllowed(user, sid, questionId);
                if (binding.version() == null || binding.version() != expectedVersion) {
                    throw new BusinessException(409, "语音录入对应的面试轮次已失效");
                }
                var key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("INSERT INTO mock_interview_speech "
                            + "(user_id,session_id,question_id,request_id,request_hash,status,"
                            + "duration_ms,claimed_duration_ms,provider,v2_version,v2_depth,v2_prompt_hash,"
                            + "created_at,updated_at) VALUES(?,?,?,?,?,'TRANSCRIBING',0,0,?,?,?,?,"
                            + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", new String[]{"id"});
                    statement.setLong(1, user);
                    statement.setLong(2, sid);
                    statement.setString(3, questionId);
                    statement.setString(4, requestId);
                    statement.setString(5, hash);
                    statement.setString(6, trustedProvider);
                    statement.setLong(7, binding.version());
                    statement.setObject(8, binding.depth());
                    statement.setString(9, binding.promptHash());
                    return statement;
                }, key);
                return new Reservation(new InterviewSpeechService.SpeechTranscript(
                        Objects.requireNonNull(key.getKey()).longValue(), questionId, "TRANSCRIBING", null, null), true);
            }));
        } catch (DuplicateKeyException exception) {
            var previous = find(user, requestId);
            if (previous.isEmpty()) {
                throw exception;
            }
            return reservedReplay(previous.get(0), sid, questionId, expectedVersion, trustedProvider);
        }
    }

    /**
     * 刷新恢复仅取当前未被确认消费的真实流式最终文本，不触发识别或答案提交。
     * 当前仅支持唯一真实流式适配器 QWEN_REALTIME_ASR；新增适配器须显式扩展恢复过滤合同。
     */
    public record Restored(InterviewSpeechService.SpeechTranscript transcript, String requestId, long version) { }

    public Restored findPendingFinal(Long user, long sid, String questionId, long expectedVersion) {
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (user <= 0 || sid <= 0 || questionId == null || questionId.isBlank()
                || questionId.length() > 100 || expectedVersion < 0) {
            throw new BusinessException(400, "流式语音恢复参数无效");
        }
        return transaction.execute(status -> {
            var parents = jdbc.queryForList("SELECT status FROM mock_interview_session "
                    + "WHERE id=? AND user_id=? AND deleted=0 FOR UPDATE", sid, user);
            if (parents.isEmpty()) {
                throw new BusinessException(404, "面试记录不存在");
            }
            if (!"IN_PROGRESS".equals(parents.get(0).get("status"))) {
                throw new BusinessException(409, "面试已结束或不可用");
            }
            requireBeforeDeadline(user, sid);
            var binding = admission.requireAllowed(user, sid, questionId);
            if (binding.version() == null || binding.version() != expectedVersion) {
                return null;
            }
            var rows = jdbc.queryForList("SELECT s.id,s.question_id,s.status,s.raw_text,"
                    + "s.error_message,s.request_id,s.v2_version FROM mock_interview_speech s "
                    + "WHERE s.user_id=? AND s.session_id=? AND s.question_id=? AND s.v2_version=? "
                    + "AND s.v2_depth=? AND s.v2_prompt_hash=? AND s.status='TRANSCRIBED' "
                    + "AND s.provider='QWEN_REALTIME_ASR' "
                    + "AND NOT EXISTS (SELECT 1 FROM mock_interview_v2_turn t WHERE t.speech_id=s.id) "
                    + "ORDER BY s.id DESC LIMIT 1", user, sid, questionId, expectedVersion,
                    binding.depth(), binding.promptHash());
            if (rows.isEmpty()) {
                return null;
            }
            var row = rows.get(0);
            var transcript = new InterviewSpeechService.SpeechTranscript(
                    ((Number) row.get("id")).longValue(), (String) row.get("question_id"),
                    (String) row.get("status"), (String) row.get("raw_text"), (String) row.get("error_message"));
            return new Restored(transcript, (String) row.get("request_id"),
                    ((Number) row.get("v2_version")).longValue());
        });
    }

    /** 父会话锁内使用数据库时间复核截止，避免云识别完成与超时推进之间的窗口。 */
    private void requireBeforeDeadline(Long user, long sid) {
        var rows = jdbc.queryForList("SELECT session_id FROM mock_interview_v2_state "
                + "WHERE session_id=? AND user_id=? AND deadline>CURRENT_TIMESTAMP", sid, user);
        if (rows.isEmpty()) {
            throw new BusinessException(409, "面试轮次已超时或不可用");
        }
    }

    private Reservation reservedReplay(Map<String, Object> row, long sid, String questionId,
            long version, String provider) {
        if (((Number) row.get("session_id")).longValue() != sid
                || !questionId.equals(row.get("question_id")) || row.get("v2_version") == null
                || ((Number) row.get("v2_version")).longValue() != version
                || !provider.equals(row.get("provider"))) {
            throw new BusinessException(409, "请求标识已用于不同录音");
        }
        return new Reservation(replay(row, (String) row.get("request_hash")), false);
    }

    /** 只保存固定脱敏错误；code 由服务器传入且不进入数据库正文。 */
    public void fail(Long user, long sid, String requestId, String code) {
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        jdbc.update("UPDATE mock_interview_speech SET status='FAILED',"
                + "error_message='语音识别已中断，请使用文字输入或创建新录音',updated_at=CURRENT_TIMESTAMP "
                + "WHERE user_id=? AND session_id=? AND request_id=? AND status='TRANSCRIBING'",
                user, sid, requestId);
    }

    private java.util.List<Map<String, Object>> find(Long user, String requestId) {
        return jdbc.queryForList("SELECT " + TRANSCRIPT_COLUMNS
                + " FROM mock_interview_speech WHERE user_id=? AND request_id=?", user, requestId);
    }

    private InterviewSpeechService.SpeechTranscript replay(Map<String, Object> row, String hash) {
        if (!hash.equals(row.get("request_hash"))) {
            throw new BusinessException(409, "请求标识已用于不同录音");
        }
        return new InterviewSpeechService.SpeechTranscript(((Number) row.get("id")).longValue(),
                (String) row.get("question_id"), (String) row.get("status"),
                (String) row.get("raw_text"), (String) row.get("error_message"));
    }

    private void validate(Long user, long sid, String question, String request, long version,
            String text, String pcmHash, long duration, String provider) {
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (user <= 0 || sid <= 0 || question == null || question.isBlank() || question.length() > 100
                || request == null || !request.matches("[A-Za-z0-9_-]{8,80}") || version < 0
                || text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH
                || pcmHash == null || !pcmHash.matches("[A-Fa-f0-9]{64}")
                || duration < MIN_DURATION_MS || duration > MAX_DURATION_MS
                || provider == null || !provider.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")) {
            throw new BusinessException(400, "流式语音转写参数无效");
        }
    }

    private String requestHash(Long user, long sid, String question, long version, String text,
            String pcmHash, long duration, String provider) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // 长度前缀避免正文或上下文中的换行造成边界碰撞；隔离上传式语音的摘要域。
            for (String field : new String[]{"stream-final-v1", user.toString(), Long.toString(sid),
                    question, Long.toString(version), pcmHash, Long.toString(duration), provider, text}) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
