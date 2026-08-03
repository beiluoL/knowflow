package com.knowflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiConfig;
import com.knowflow.config.AiProviderRegistry;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.UserAiConfig;
import com.knowflow.ai.AiException;
import com.knowflow.ai.ModelAdapter;
import com.knowflow.ai.ModelAdapterFactory;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.UserAiConfigMapper;
import com.knowflow.service.AiService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
/** AI 问答业务服务实现，基于 OpenAI 兼容接口调用大模型，未配置时降级。 */
public class AiServiceImpl implements AiService {

    private final AiConfig aiConfig;
    private final UserAiConfigMapper userAiConfigMapper;
    private final AiProviderRegistry providerRegistry;
    private final ModelAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean isConfigured() {
        String key = aiConfig.getApiKey();
        return key != null && !key.isEmpty() && !key.startsWith("sk-placeholder");
    }

    /** 对话入口：失败时降级为提示文案，不抛异常（保证聊天体验）。 */
    @Override
    public String chat(String userMessage, List<DocDocument> contextDocs) {
        return chat(userMessage, contextDocs, null);
    }

    @Override
    public String chat(String userMessage, List<DocDocument> contextDocs, String model) {
        return chat(userMessage, contextDocs, model, null);
    }

    @Override
    public String chat(String userMessage, List<DocDocument> contextDocs, String model, Long userId) {
        try {
            String systemPrompt = buildSystemPrompt(contextDocs);
            return callModel(systemPrompt, userMessage, 0.7, model, userId);
        } catch (Exception e) {
            log.warn("AI 对话降级: {}", e.getMessage());
            return generateFallbackReply(userMessage);
        }
    }

    /** 结构化补全入口：严格要求配置，未配置或调用失败时抛出异常由上层提示。 */
    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, null);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, String model) {
        return complete(systemPrompt, userPrompt, model, null);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, String model, Long userId) {
        return callModel(systemPrompt, userPrompt, 0.3, model, userId);
    }

    @Override
    public List<String> getAvailableModels() {
        List<String> models = aiConfig.getModels();
        if (models == null || models.isEmpty()) {
            models = Collections.singletonList(aiConfig.getModel());
        }
        return models;
    }

    /**
     * 统一的大模型调用：组装 OpenAI 兼容请求并解析首个 choice 的 content。
     * 未配置密钥时抛出 BusinessException；其余异常同样抛出以通知上层。
     */
    private String callModel(String systemPrompt, String userPrompt, double temperature, String model, Long userId) {
        EffectiveAiConfig effective = resolveEffectiveConfig(userId);
        if (effective.apiKey() == null || effective.apiKey().isEmpty() || effective.apiKey().startsWith("sk-placeholder")) {
            throw new BusinessException("AI 服务未配置，请先在 application.yml 中设置有效的 ai.api-key 或在个人配置中填写 API Key 以启用该功能");
        }
        try {
            String effectiveModel = (model != null && !model.isBlank()) ? model : effective.model();
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", effectiveModel);
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("max_tokens", aiConfig.getMaxTokens());
            requestBody.put("temperature", temperature);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(effective.baseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + effective.apiKey())
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    // 检查内容是否达到输出上限，若是则追加提示
                    Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
                    if (usage != null && Boolean.TRUE.equals(isFinishReasonLength(choices.get(0)))) {
                        int maxChars = aiConfig.getMaxContentChars();
                        if (content.length() > maxChars) {
                            content = content.substring(0, maxChars);
                        }
                        content += "\n\n---\n*注意：本次回复已达到模型最大输出长度，内容可能被截断。如需更完整的内容，请分多次询问或缩短问题范围。*";
                    }
                    return content;
                }
            }
            log.error("AI API 调用失败: status={} body={}", response.statusCode(), response.body());
            throw new BusinessException("AI 服务调用失败（状态码 " + response.statusCode() + "），请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务异常: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务异常：" + e.getMessage());
        }
    }

    /**
     * 对外暴露的大模型调用入口（供 IntentService 等内部服务复用的统一通道）。
     * model 传 null 时使用生效配置中的默认模型。
     */
    public String complete(String systemPrompt, String userPrompt, double temperature, Long userId) {
        return callModel(systemPrompt, userPrompt, temperature, null, userId);
    }

    /** 解析用户级 AI 配置，优先使用用户自带的 Key，否则回退到全局配置。 */
    private EffectiveAiConfig resolveEffectiveConfig(Long userId) {
        if (userId != null) {
            LambdaQueryWrapper<UserAiConfig> wrapper = new LambdaQueryWrapper<UserAiConfig>()
                    .eq(UserAiConfig::getUserId, userId)
                    .eq(UserAiConfig::getIsActive, 1)
                    .last("LIMIT 1");
            UserAiConfig userConfig = userAiConfigMapper.selectOne(wrapper);
            if (userConfig != null && userConfig.getApiKey() != null && !userConfig.getApiKey().isEmpty()) {
                String baseUrl = (userConfig.getBaseUrl() != null && !userConfig.getBaseUrl().isEmpty())
                        ? userConfig.getBaseUrl() : getProviderDefaultBaseUrl(userConfig.getProvider());
                String model = (userConfig.getModel() != null && !userConfig.getModel().isEmpty())
                        ? userConfig.getModel() : aiConfig.getModel();
                return new EffectiveAiConfig(userConfig.getApiKey(), baseUrl, model, userConfig.getProvider(), userConfig.getApiSecret());
            }
        }
        return new EffectiveAiConfig(aiConfig.getApiKey(), aiConfig.getBaseUrl(), aiConfig.getModel(), null, null);
    }

    private String getProviderDefaultBaseUrl(String provider) {
        String url = providerRegistry.defaultBaseUrl(provider);
        return url != null ? url : aiConfig.getBaseUrl();
    }

    /** 内部持有解析后的 AI 配置（provider 用于选择协议适配器，apiSecret 供文心鉴权）。 */
    private record EffectiveAiConfig(String apiKey, String baseUrl, String model, String provider, String apiSecret) {
        EffectiveAiConfig(String apiKey, String baseUrl, String model) {
            this(apiKey, baseUrl, model, null, null);
        }
    }

    /** 判断是否因达到 max_tokens 限制而截断输出。 */
    private boolean isFinishReasonLength(Map<String, Object> choice) {
        Object finishReason = choice.get("finish_reason");
        return finishReason != null && "length".equals(finishReason.toString());
    }

    private String buildSystemPrompt(List<DocDocument> contextDocs) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 knowflow 知识库助手。请基于知识库内容回答用户问题，回答需准确、简洁、有帮助。");
        sb.append("如果问题与知识库无关，也可正常对话，但应引导用户使用知识库功能。");
        if (contextDocs != null && !contextDocs.isEmpty()) {
            sb.append("\n\n以下是检索到的相关文档，请优先参考这些内容作答：\n");
            for (int i = 0; i < contextDocs.size(); i++) {
                DocDocument doc = contextDocs.get(i);
                sb.append(String.format("[%d] %s\n%s\n\n",
                        i + 1,
                        doc.getTitle(),
                        doc.getSummary() != null ? doc.getSummary() : ""));
            }
        }
        return sb.toString();
    }

    private String generateFallbackReply(String userMessage) {
        return "AI 服务尚未配置或暂时不可用。您的问题是：\"" + userMessage
                + "\"。\n\n请管理员在 application.yml 中配置有效的 AI API Key（ai.api-key）以启用智能问答功能。\n"
                + "当前支持 OpenAI 兼容接口（DeepSeek / 通义千问 / OpenAI 等），配置示例：\n"
                + "```\nai:\n  api-key: 你的API密钥\n  base-url: https://api.deepseek.com/v1\n  model: deepseek-chat\n```";
    }

    @Override
    public String chatWithImages(String text, List<String> images, List<DocDocument> contextDocs, String model, Long userId) {
        if (images == null || images.isEmpty()) {
            return chat(text, contextDocs, model, userId);
        }
        if (text == null || text.isBlank()) {
            text = "请描述这些图片的内容";
        }
        String systemPrompt = buildSystemPrompt(contextDocs);
        String userMessage = systemPrompt + "\n\n用户问题：" + text;
        return callVisionModel(userMessage, images, model, userId);
    }

    @Override
    public String gradeEssay(String question, String userAnswer, String correctAnswer) {
        String sysPrompt = "你是一个严格的评卷老师。请对以下主观题答案进行评分（0-100分），"
                + "并给出简短评语。请只返回 JSON 格式：{\"score\": 分数, \"feedback\": \"评语\"}";
        String userPrompt = "题目：" + question + "\n"
                + "参考答案：" + correctAnswer + "\n"
                + "学生答案：" + userAnswer;
        try {
            return callModel(sysPrompt, userPrompt, 0.3, null, null);
        } catch (Exception e) {
            log.warn("AI 评分失败，返回默认结果: {}", e.getMessage());
            // 降级：简单字符串包含判定
            boolean match = userAnswer.toLowerCase().contains(correctAnswer.toLowerCase())
                    || correctAnswer.toLowerCase().contains(userAnswer.toLowerCase());
            return "{\"score\": " + (match ? 85 : 40) + ", \"feedback\": \""
                    + (match ? "基本正确，建议完善细节" : "与参考答案差异较大，建议重新理解题目") + "\"}";
        }
    }

    /**
     * 视觉模型调用：将用户消息和图片组装为多模态 content 数组发送。
     * 图片格式必须为 base64 编码，不含 data:image... 前缀。
     */
    private String callVisionModel(String userMessage, List<String> images, String model, Long userId) {
        var effective = resolveEffectiveConfig(userId);
        // 视觉模型名优先使用传入的 model，其次 visionModel 配置，再次 model 配置
        String visionModel = (model != null && !model.isBlank()) ? model
                : (aiConfig.getVisionModel() != null ? aiConfig.getVisionModel() : aiConfig.getModel());

        if (!isConfigured()) {
            return generateFallbackReply(userMessage);
        }

        try {
            // 构建多模态 content 数组
            List<Map<String, Object>> multimodalContent = new ArrayList<>();
            multimodalContent.add(Map.of("type", "text", "text", userMessage));
            for (String img : images) {
                String dataUrl = img.startsWith("data:") ? img : "data:image/png;base64," + img;
                multimodalContent.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", dataUrl)
                ));
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", visionModel);
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", "你是 KnowFlow AI 知识库助手，可以根据图片内容进行识别和分析。"),
                    Map.of("role", "user", "content", multimodalContent)
            ));
            requestBody.put("max_tokens", aiConfig.getMaxTokens());
            requestBody.put("temperature", 0.7);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("Vision request: model={}, images={}, msgLen={}", visionModel, images.size(), userMessage.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(effective.baseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + effective.apiKey())
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Map<String, Object> responseMap = objectMapper.readValue(resp.body(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    if (content == null) content = choices.get(0).get("text") != null ? (String) choices.get(0).get("text") : "（AI 未返回有效内容）";
                    return content;
                }
            }
            log.error("Vision API 调用失败: status={}", resp.statusCode());
            return "抱歉，图像分析失败（状态码 " + resp.statusCode() + "）";
        } catch (Exception e) {
            log.error("视觉模型调用失败: {}", e.getMessage());
            return "抱歉，图像分析失败：" + e.getMessage();
        }
    }

    // ==================== 编程 Agent 扩展：流式对话与健康检查 ====================

    /**
     * 按 configId 解析用户配置；configId 为 null 时回退到 active 配置或全局配置。
     * 编程 Agent 场景下用户可从全部配置中自由选择，因此显式按 id 查询。
     */
    private EffectiveAiConfig resolveConfigById(Long userId, Long configId) {
        if (configId != null && userId != null) {
            UserAiConfig cfg = userAiConfigMapper.selectById(configId);
            if (cfg != null && cfg.getUserId().equals(userId)
                    && cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
                String baseUrl = (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isEmpty())
                        ? cfg.getBaseUrl() : getProviderDefaultBaseUrl(cfg.getProvider());
                String model = (cfg.getModel() != null && !cfg.getModel().isEmpty())
                        ? cfg.getModel()
                        : (providerRegistry.defaultModel(cfg.getProvider()) != null
                            ? providerRegistry.defaultModel(cfg.getProvider()) : aiConfig.getModel());
                return new EffectiveAiConfig(cfg.getApiKey(), baseUrl, model, cfg.getProvider(), cfg.getApiSecret());
            }
        }
        return resolveEffectiveConfig(userId);
    }

    /**
     * 流式对话：发起 OpenAI 兼容 {@code stream:true} 请求，逐 token 通过 SSE 推送给前端。
     * <p>
     * 实现：使用 {@link HttpResponse.BodyHandlers#ofLines()} 接收行流，逐行解析 {@code data:} 前缀，
     * 提取 {@code choices[0].delta.content} 增量推送 {@code delta} 事件，结束时推送 {@code done}。
     * <p>
     * 注意：{@code httpClient.send} 仍为阻塞调用，但 ofLines 返回的 Stream 在 forEach 消费时
     * 才逐行读取，可在调用线程同步处理；为避免长时间占用 Servlet 线程，Controller 层应在异步线程中调用本方法。
     */
    @Override
    public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId, SseEmitter emitter) {
        streamChat(systemPrompt, userPrompt, userId, configId, emitter, null);
    }

    /**
     * 带完成回调的流式对话实现。
     * <p>
     * 无论成功或失败，都会在流结束时调用 callback（如果非 null）：
     * <ul>
     *   <li>成功：onComplete(fullContent, true)</li>
     *   <li>失败：onComplete(errorMsg, false)</li>
     * </ul>
     */
    @Override
    public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                           SseEmitter emitter, com.knowflow.service.AiService.StreamCompletionCallback callback) {
        streamChat(systemPrompt, userPrompt, userId, configId, emitter, callback, null, null, null);
    }

    /**
     * 带运行时参数的流式对话实现：temperature / maxTokens / topP 为 null 时回退到默认值。
     */
    @Override
    public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                           SseEmitter emitter, com.knowflow.service.AiService.StreamCompletionCallback callback,
                           Double temperature, Integer maxTokens, Double topP) {
        EffectiveAiConfig effective = resolveConfigById(userId, configId);
        if (effective.apiKey() == null || effective.apiKey().isEmpty()
                || (!"local".equals(effective.apiKey()) && effective.apiKey().startsWith("sk-placeholder"))) {
            String errMsg = "AI 服务未配置，请先在设置中添加模型配置";
            sendSseEvent(emitter, "error", Map.of("error", errMsg));
            completeSse(emitter);
            if (callback != null) callback.onComplete(errMsg, false);
            return;
        }
        try {
            ModelAdapter adapter = adapterFactory.getAdapter(effective.provider());
            ModelAdapter.ChatRequest req = new ModelAdapter.ChatRequest();
            req.model = effective.model();
            req.apiKey = effective.apiKey();
            req.apiSecret = effective.apiSecret();
            req.baseUrl = effective.baseUrl();
            req.temperature = temperature != null ? temperature : 0.7;
            req.maxTokens = maxTokens != null ? maxTokens : aiConfig.getMaxTokens();
            req.topP = topP;
            ModelAdapter.ChatMessage sysMsg = new ModelAdapter.ChatMessage("system", systemPrompt);
            ModelAdapter.ChatMessage usrMsg = new ModelAdapter.ChatMessage("user", userPrompt);
            req.messages = Arrays.asList(sysMsg, usrMsg);

            StringBuilder full = new StringBuilder();
            adapter.streamChat(req,
                    delta -> {
                        if (delta.delta != null && !delta.delta.isEmpty()) {
                            full.append(delta.delta);
                            sendSseEvent(emitter, "delta", Map.of("content", delta.delta));
                        }
                    },
                    done -> {
                        sendSseEvent(emitter, "done", Map.of("content", done.content));
                        if (callback != null) callback.onComplete(done.content, true);
                    });
        } catch (AiException e) {
            log.error("流式对话模型异常: userId={}, configId={}, provider={}, err={}",
                    userId, configId, effective.provider(), e.getMessage());
            String errMsg = "AI 流式调用失败：" + e.getMessage();
            sendSseEvent(emitter, "error", Map.of("error", errMsg));
            if (callback != null) callback.onComplete(errMsg, false);
        } catch (Exception e) {
            log.error("流式对话异常: userId={}, configId={}, err={}", userId, configId, e.getMessage(), e);
            String errMsg = "AI 流式调用失败：" + e.getMessage();
            sendSseEvent(emitter, "error", Map.of("error", errMsg));
            if (callback != null) callback.onComplete(errMsg, false);
        } finally {
            completeSse(emitter);
        }
    }

    /** 模型可用性检测：发送一个极短请求，验证配置是否可用并测量延迟。 */
    @Override
    public String healthCheck(Long userId, Long configId) {
        EffectiveAiConfig effective = resolveConfigById(userId, configId);
        if (effective.apiKey() == null || effective.apiKey().isEmpty()
                || (!"local".equals(effective.apiKey()) && effective.apiKey().startsWith("sk-placeholder"))) {
            return "{\"ok\":false,\"error\":\"未配置有效的 API Key\"}";
        }
        long start = System.currentTimeMillis();
        try {
            ModelAdapter adapter = adapterFactory.getAdapter(effective.provider());
            ModelAdapter.ChatRequest req = new ModelAdapter.ChatRequest();
            req.model = effective.model();
            req.apiKey = effective.apiKey();
            req.apiSecret = effective.apiSecret();
            req.baseUrl = effective.baseUrl();
            req.maxTokens = 5;
            req.messages = List.of(new ModelAdapter.ChatMessage("user", "hi"));
            ModelAdapter.ChatResult r = adapter.chat(req);
            long latency = System.currentTimeMillis() - start;
            return "{\"ok\":true,\"latencyMs\":" + latency + "}";
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return "{\"ok\":false,\"latencyMs\":" + latency
                    + ",\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    // ==================== 编程 Agent 多轮 + 工具调用通道 ====================

    @Override
    public com.knowflow.ai.ModelAdapter.ChatResult chatMulti(
            List<com.knowflow.ai.ModelAdapter.ChatMessage> messages,
            List<com.knowflow.ai.ModelAdapter.ToolSpec> tools,
            Long userId, Long configId) {
        EffectiveAiConfig effective = resolveConfigById(userId, configId);
        if (effective.apiKey() == null || effective.apiKey().isEmpty()
                || (!"local".equals(effective.apiKey()) && effective.apiKey().startsWith("sk-placeholder"))) {
            throw new BusinessException("AI 服务未配置，请先在设置中添加模型配置");
        }
        ModelAdapter adapter = adapterFactory.getAdapter(effective.provider());
        ModelAdapter.ChatRequest req = new ModelAdapter.ChatRequest();
        req.model = effective.model();
        req.apiKey = effective.apiKey();
        req.apiSecret = effective.apiSecret();
        req.baseUrl = effective.baseUrl();
        req.messages = messages;
        req.tools = tools;
        req.temperature = 0.3;
        req.maxTokens = aiConfig.getMaxTokens();
        try {
            return adapter.chat(req);
        } catch (AiException e) {
            throw new BusinessException("AI 调用失败：" + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("AI 调用异常：" + e.getMessage());
        }
    }

    @Override
    public void streamMulti(
            List<com.knowflow.ai.ModelAdapter.ChatMessage> messages,
            List<com.knowflow.ai.ModelAdapter.ToolSpec> tools,
            Long userId, Long configId,
            java.util.function.Consumer<com.knowflow.ai.ModelAdapter.TokenDelta> onToken,
            java.util.function.Consumer<com.knowflow.ai.ModelAdapter.StreamDone> onDone,
            Double temperature, Integer maxTokens, Double topP) {
        EffectiveAiConfig effective = resolveConfigById(userId, configId);
        if (effective.apiKey() == null || effective.apiKey().isEmpty()
                || (!"local".equals(effective.apiKey()) && effective.apiKey().startsWith("sk-placeholder"))) {
            throw new BusinessException("AI 服务未配置，请先在设置中添加模型配置");
        }
        ModelAdapter adapter = adapterFactory.getAdapter(effective.provider());
        ModelAdapter.ChatRequest req = new ModelAdapter.ChatRequest();
        req.model = effective.model();
        req.apiKey = effective.apiKey();
        req.apiSecret = effective.apiSecret();
        req.baseUrl = effective.baseUrl();
        req.messages = messages;
        req.tools = tools;
        req.temperature = temperature != null ? temperature : 0.3;
        req.maxTokens = maxTokens != null ? maxTokens : aiConfig.getMaxTokens();
        req.topP = topP;
        try {
            adapter.streamChat(req, onToken, onDone);
        } catch (AiException e) {
            throw new BusinessException("AI 流式调用失败：" + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("AI 流式调用异常：" + e.getMessage());
        }
    }

    /** 推送 SSE 事件（data 字段为 JSON 字符串）。 */
    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: event={}, err={}", eventName, e.getMessage());
        }
    }

    private void completeSse(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
