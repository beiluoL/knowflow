package com.knowflow.support;

import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.ai.capability.AiFailureKind;
import com.knowflow.entity.DocDocument;
import com.knowflow.service.AiService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Architecture-4.3 §49：Fake AI Provider（测试替身）。
 *
 * <p>以 {@code @Primary} 覆盖 {@code AiService} 注入点（真实 {@code AiServiceImpl} 仍作为具体类 Bean 保留，
 * 供依赖具体类的 IntentService 等正常装配）。外部 LLM 不参与测试：脚本化返回由测试在 {@link #script} 设置。</p>
 *
 * <p>Phase 3H 新增：{@link #nextFailureKind} 可脚本化结构化失败分类（401/429/超时/未配置…），
 * 供 AI 降级故障注入测试使用；{@link #configured} 控制“未配置 Key”场景。</p>
 */
@TestConfiguration
public class FakeAiConfiguration {

    /** 脚本返回内容（FIFO）；耗尽后默认返回 {@code {}}。 */
    public static final Queue<String> QUEUE = new ConcurrentLinkedQueue<>();
    /** 4 参 complete 调用计数（断言自动重试用）。 */
    public static int completeCalls = 0;
    /** 最近一次 4 参 complete 收到的 userId（断言用户隔离/按用户路由用）。 */
    public static Long lastUserId = null;

    /** Phase 3H：非 null 时下一次 complete 抛该分类的 {@link AiCapabilityException}（一次性）。 */
    public static volatile AiFailureKind nextFailureKind = null;
    /** Phase 3H：模拟“是否已配置有效 Key”，默认已配置。 */
    public static volatile boolean configured = true;

    /** 超时/异常哨兵：压入后下一次调用抛 RuntimeException。 */
    public static final String THROW_SENTINEL = "\u0000__THROW__";

    public static void reset() {
        QUEUE.clear();
        completeCalls = 0;
        lastUserId = null;
        nextFailureKind = null;
        configured = true;
    }

    /** 依次压入多段返回；末位可用 {@link #THROW_SENTINEL} 触发异常。 */
    public static void script(String... responses) {
        for (String r : responses) {
            QUEUE.add(r);
        }
    }

    @Bean
    @Primary
    public AiService fakeAiService() {
        return new AiService() {
            @Override
            public String chat(String userMessage, List<DocDocument> contextDocs) {
                return "";
            }

            @Override
            public String chat(String userMessage, List<DocDocument> contextDocs, String model) {
                return "";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return complete(systemPrompt, userPrompt, null, null);
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, String model) {
                return complete(systemPrompt, userPrompt, model, null);
            }

            @Override
            public String chat(String userMessage, List<DocDocument> contextDocs, String model, Long userId) {
                return "";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, String model, Long userId) {
                completeCalls++;
                lastUserId = userId;
                AiFailureKind kind = nextFailureKind;
                if (kind != null) {
                    nextFailureKind = null;
                    throw new AiCapabilityException(kind, "fake: " + kind.name());
                }
                String next = QUEUE.poll();
                if (THROW_SENTINEL.equals(next)) {
                    throw new RuntimeException("fake ai timeout");
                }
                return next == null ? "{}" : next;
            }

            @Override
            public List<String> getAvailableModels() {
                return List.of("fake-model");
            }

            @Override
            public boolean isConfigured() {
                return configured;
            }

            @Override
            public boolean isConfigured(Long userId) {
                return configured;
            }

            @Override
            public String currentScope(Long userId) {
                return "fake:fake-model";
            }

            @Override
            public String gradeEssay(String question, String userAnswer, String correctAnswer) {
                return "";
            }

            @Override
            public String chatWithImages(String text, List<String> images, List<DocDocument> contextDocs,
                                          String model, Long userId) {
                return "";
            }

            @Override
            public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                                   SseEmitter emitter) {
                // no-op
            }

            @Override
            public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                                   SseEmitter emitter, StreamCompletionCallback callback) {
                // no-op
            }

            @Override
            public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                                   SseEmitter emitter, StreamCompletionCallback callback,
                                   Double temperature, Integer maxTokens, Double topP) {
                // no-op
            }

            @Override
            public String healthCheck(Long userId, Long configId) {
                return "{\"ok\":true}";
            }

            @Override
            public com.knowflow.ai.ModelAdapter.ChatResult chatMulti(
                    List<com.knowflow.ai.ModelAdapter.ChatMessage> messages,
                    List<com.knowflow.ai.ModelAdapter.ToolSpec> tools, Long userId, Long configId) {
                return null;
            }

            @Override
            public boolean supportsTools(Long userId, Long configId) {
                return false;
            }

            @Override
            public void streamMulti(List<com.knowflow.ai.ModelAdapter.ChatMessage> messages,
                                    List<com.knowflow.ai.ModelAdapter.ToolSpec> tools, Long userId, Long configId,
                                    java.util.function.Consumer<com.knowflow.ai.ModelAdapter.TokenDelta> onToken,
                                    java.util.function.Consumer<com.knowflow.ai.ModelAdapter.StreamDone> onDone,
                                    Double temperature, Integer maxTokens, Double topP) {
                // no-op
            }
        };
    }
}
