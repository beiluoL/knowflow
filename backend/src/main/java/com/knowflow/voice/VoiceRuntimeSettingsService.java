package com.knowflow.voice;

import com.knowflow.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 实例级语音设置：进程内缓存 + 单行表持久化。
 * <p>不做逻辑删除（设置行只更新不删除），因此不存在「唯一键被软删行占死」的问题；
 * 表主键固定为 1，插入冲突按幂等处理。
 */
@Service
public class VoiceRuntimeSettingsService {

    private static final long ROW_ID = 1L;

    private final VoiceRuntimeProperties properties;
    private final JdbcTemplate jdbc;
    private volatile VoiceRuntimeSetting current;

    public VoiceRuntimeSettingsService(VoiceRuntimeProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void load() {
        VoiceMode fallback = VoiceMode.parse(properties.getMode());
        if (fallback == null) throw new IllegalStateException("knowflow.voice.mode 必须是 LOCAL/HYBRID/CLOUD");
        current = VoiceRuntimeSetting.defaults(fallback);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT mode,allow_remote_fallback,update_by,update_time FROM voice_runtime_setting WHERE id=?", ROW_ID);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                VoiceMode stored = VoiceMode.parse(String.valueOf(row.get("mode")));
                Object updated = row.get("update_time");
                current = new VoiceRuntimeSetting(
                        stored == null ? fallback : stored,
                        toBoolean(row.get("allow_remote_fallback")),
                        row.get("update_by") == null ? null : ((Number) row.get("update_by")).longValue(),
                        updated instanceof Timestamp stamp ? stamp.toInstant() : null);
            }
        } catch (RuntimeException unsupported) {
            // 迁移未执行时不让整个应用启动失败，但保持配置默认值并记录原因。
            current = VoiceRuntimeSetting.defaults(fallback);
        }
    }

    public VoiceRuntimeSetting current() {
        return current;
    }

    public VoiceMode mode() {
        return current.mode();
    }

    /** 只有 ADMIN 能改，避免单个用户替所有会话决定数据出境。 */
    public VoiceRuntimeSetting update(long user, String mode, Boolean allowRemoteFallback) {
        VoiceMode parsed = VoiceMode.parse(mode);
        if (parsed == null) throw new BusinessException(400, "运行模式必须是 LOCAL/HYBRID/CLOUD");
        VoiceRuntimeSetting previous = current;
        boolean fallback = allowRemoteFallback == null ? previous.allowRemoteFallback() : allowRemoteFallback;
        if (parsed == VoiceMode.LOCAL && fallback) {
            throw new BusinessException(400, "本地模式不允许开启远程降级");
        }
        persist(parsed, fallback, user);
        current = new VoiceRuntimeSetting(parsed, fallback, user, Instant.now());
        return current;
    }

    private void persist(VoiceMode mode, boolean fallback, long user) {
        int updated = jdbc.update(
                "UPDATE voice_runtime_setting SET mode=?,allow_remote_fallback=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                mode.name(), fallback ? 1 : 0, user, ROW_ID);
        if (updated > 0) return;
        try {
            jdbc.update(
                    "INSERT INTO voice_runtime_setting(id,mode,allow_remote_fallback,update_by,create_time,update_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    ROW_ID, mode.name(), fallback ? 1 : 0, user);
        } catch (DuplicateKeyException concurrent) {
            jdbc.update(
                    "UPDATE voice_runtime_setting SET mode=?,allow_remote_fallback=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    mode.name(), fallback ? 1 : 0, user, ROW_ID);
        }
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return false;
    }
}
