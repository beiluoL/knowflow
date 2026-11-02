package com.knowflow.interview.voice;

import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import com.knowflow.service.impl.AiServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 显式启用的隔离浏览器夹具：真实登录/票据/WS/V2 持久化，ASR 为标明来源的合成替身。
 * 不验证真实麦克风、云识别、模型质量或真实 TTS；普通测试不启动该夹具。
 */
@EnabledIfEnvironmentVariable(named = "KNOWFLOW_V23_BROWSER_HARNESS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18083",
        "spring.profiles.active=test",
        "jwt.secret=SyntheticV23BrowserHarnessOnly32CharactersMinimum",
        "knowflow.datasource.type=h2",
        "knowflow.datasource.h2.url=jdbc:h2:mem:v23_browser_voice;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/v23-browser-no-overrides.json",
        "knowflow.datasource.allow-runtime-switch=false",
        "knowflow.seed.development-account.enabled=false",
        "knowflow.ai.capability.enabled=false",
        "knowflow.interview.voice.enabled=true",
        "knowflow.interview.voice.allowed-origins[0]=http://localhost:15173"
})
@ActiveProfiles("test")
class VoiceBrowserHarnessTest {
    @DynamicPropertySource
    static void isolatedDatasourceConfig(DynamicPropertyRegistry registry) {
        // 随机不存在路径确保不会读取本机保存的数据源选择，且运行时切换保持关闭。
        String noOverride = Path.of(System.getProperty("java.io.tmpdir"),
                "v23-browser-no-override-" + java.util.UUID.randomUUID() + ".json").toString();
        registry.add("knowflow.datasource.config-file", () -> noOverride);
    }

    @Autowired private JdbcTemplate jdbc;
    @MockBean private AiServiceImpl ai;
    @MockBean private InterviewStreamingAsrProvider asr;
    @MockBean private InterviewTtsProvider tts;

    @Test
    void browserUsesNormalAuthenticationTicketsSocketAndOriginalV2() throws Exception {
        String url = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                connection -> connection.getMetaData().getURL());
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:h2:mem:v23_browser_voice"), "必须使用专属内存数据库");
        String signalValue = System.getenv("KNOWFLOW_V23_FINISH_FILE");
        assertNotNull(signalValue, "必须指定夹具完成信号文件");
        Path signal = Path.of(signalValue);
        assertFalse(Files.exists(signal), "完成信号不能预先存在");
        jdbc.update("INSERT INTO sys_user(username,email,password,nickname,role) VALUES(?,?,?,?, 'user')",
                "v23_fixture_user", "v23_fixture@example.test",
                new BCryptPasswordEncoder().encode("SyntheticV23!2026"), "V23 Voice Test Double");
        long user = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, "v23_fixture_user");
        when(ai.isConfigured()).thenReturn(false);
        when(ai.isConfigured(anyLong())).thenReturn(false);
        when(ai.currentScope(anyLong())).thenReturn("V23_TEST_DOUBLE_NO_CLOUD");
        when(asr.capabilities()).thenReturn(
                new SpeechStreamCapabilities(true, "QWEN_REALTIME_ASR", "PCM16", 16000));
        when(tts.capabilities()).thenReturn(
                new SpeechStreamCapabilities(false, "V23_TTS_TEST_DOUBLE_DISABLED", "PCM16", 24000));
        when(asr.open(anyString(), any())).thenAnswer(invocation -> {
            InterviewStreamingAsrProvider.Listener callback = invocation.getArgument(1);
            return new InterviewStreamingAsrProvider.Stream() {
                private final AtomicBoolean firstFrame = new AtomicBoolean();
                private final AtomicBoolean closed = new AtomicBoolean();
                @Override public void audio(byte[] pcm) {
                    if (!closed.get() && firstFrame.compareAndSet(false, true)) {
                        callback.speechStarted();
                        callback.partial("合成 ASR Test Double：正在验证协议与录入流程");
                    }
                }
                @Override public void finish() {
                    if (closed.compareAndSet(false, true)) {
                        callback.finalText("合成 ASR Test Double：先明确问题边界，再通过实际测试验证工程行为。");
                        callback.speechEnded();
                        callback.completed();
                    }
                }
                @Override public void cancel() { closed.set(true); }
            };
        });
        Path ready = Path.of(signalValue + ".ready");
        Files.writeString(ready, "V23_SYNTHETIC_TEST_DOUBLE_READY_PORT_18083");
        try {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20);
            while (!Files.exists(signal) && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(500);
            }
            assertTrue(Files.exists(signal), "浏览器夹具未收到完成信号");
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM interview_voice_ticket "
                    + "WHERE user_id=? AND status IN ('CONNECTED','CLOSED')", Integer.class, user) >= 1,
                    "浏览器必须真实消费至少一张语音连接票据");
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_v2_state s "
                    + "JOIN mock_interview_session m ON m.id=s.session_id "
                    + "WHERE s.user_id=? AND m.user_id=? AND m.deleted=0", Integer.class, user, user) >= 1,
                    "浏览器必须通过原面试服务创建 V2 会话");
            verify(tts, never()).synthesizeStream(anyString(), anyString(), any());
        } finally {
            Files.deleteIfExists(ready);
        }
    }
}
