package com.knowflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.ai.capability.AiCapabilityExecutor;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.ai.capability.AiCapabilityStateService;
import com.knowflow.ai.capability.AiFailureKind;
import com.knowflow.ai.capability.AiRawResult;
import com.knowflow.ai.capability.ChatModelCapability;
import com.knowflow.ai.capability.ChatModelCapabilityRegistry;
import com.knowflow.ai.OpenAiResponsesStreamClient;
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
import com.knowflow.vo.ChatModelCapabilityVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
    private final AiCapabilityExecutor capabilityExecutor;
    private final AiCapabilityStateService capabilityStateService;
    private final AiCapabilityProperties capabilityProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiResponsesStreamClient responsesStreamClient = new OpenAiResponsesStreamClient(objectMapper);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 所有受控模型通道共用系统总开关；身份/资源归属仍由各业务入口负责。 */
    private void requireAiEnabled() {
        if (!capabilityProperties.isEnabled()) throw new AiCapabilityException(AiFailureKind.SYSTEM_DISABLED, null);
    }

    @Override
    public boolean isConfigured() {
        return isConfigured(resolveEffectiveConfig(null));
    }

    @Override
    public boolean isConfigured(Long userId) {
        return isConfigured(resolveEffectiveConfig(userId));
    }

    @Override
    public String currentScope(Long userId) {
        EffectiveAiConfig effective = resolveEffectiveConfig(userId);
        String model = effective.model() == null ? "default" : effective.model();
        String provider = effective.provider() == null ? "platform" : effective.provider();
        return provider + ":" + model;
    }

    private boolean isConfigured(EffectiveAiConfig effective) {
        if (!capabilityProperties.isEnabled()) {
            return false;
        }
        String key = effective.apiKey();
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
        } catch (AiCapabilityException e) {
            log.warn("AI 对话降级 kind={}: {}", e.getKind(), e.getMessage());
            // 聊天场景：明确告知当前 AI 状态，绝不伪装成模型回答用户的问题
            return generateFallbackReply(userMessage, e.getKind());
        } catch (Exception e) {
            log.warn("AI 对话降级: {}", e.getMessage());
            return generateFallbackReply(userMessage, AiFailureKind.UNKNOWN);
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
    public String completeStructured(String systemPrompt,String userPrompt,String model,Long userId){
        return callModel(systemPrompt,userPrompt,0.3,model,userId,true);
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
     * 统一的大模型调用入口：经 AI 能力执行器获得熔断/限流/重试/预算护栏。
     * 失败时抛 {@link AiCapabilityException}（携带可区分的 {@link AiFailureKind}），
     * 由业务层按功能选择降级；此处绝不返回伪造内容。
     */
    private String callModel(String systemPrompt, String userPrompt, double temperature, String model, Long userId) {
        return callModel(systemPrompt,userPrompt,temperature,model,userId,false);
    }

    private String callModel(String systemPrompt,String userPrompt,double temperature,String model,Long userId,boolean structured){
        EffectiveAiConfig effective = resolveEffectiveConfig(userId);
        if (!isConfigured(effective)) {
            throw new AiCapabilityException(
                    capabilityProperties.isEnabled() ? AiFailureKind.UNCONFIGURED : AiFailureKind.SYSTEM_DISABLED,
                    "请在个人设置中配置有效 API Key，或由管理员开启系统 AI");
        }
        String effectiveModel = (model != null && !model.isBlank()) ? model : effective.model();
        String scope = (effective.provider() == null ? "platform" : effective.provider()) + ":" + effectiveModel;
        AiRawResult result = capabilityExecutor.execute(scope, false,
                () -> invokeChatCompletion(effective, effectiveModel, systemPrompt, userPrompt, temperature, structured));
        return result.content();
    }

    /**
     * 单次真实 HTTP 调用（重试由执行器负责）。所有供应商异常在此被分类为
     * {@link AiCapabilityException}，不再用笼统的“系统错误”。
     */
    private AiRawResult invokeChatCompletion(EffectiveAiConfig effective, String effectiveModel,
                                             String systemPrompt, String userPrompt, double temperature, boolean structured) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", effectiveModel);
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("max_tokens", aiConfig.getMaxTokens());
            requestBody.put("temperature", temperature);
            // 仅显式JSON任务且已知DeepSeek协议时使用；普通对话与其他供应商不附加专有参数。
            boolean deepSeek="deepseek".equalsIgnoreCase(effective.provider());
            try{deepSeek|="api.deepseek.com".equalsIgnoreCase(URI.create(effective.baseUrl()).getHost());}catch(IllegalArgumentException ignored){}
            if(structured&&deepSeek){
                requestBody.put("thinking",Map.of("type","disabled"));
                requestBody.put("response_format",Map.of("type","json_object"));
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(effective.baseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + effective.apiKey())
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            requireAiEnabled();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new AiCapabilityException(AiFailureKind.INVALID_RESPONSE, "响应缺少 choices");
                }
                Map<String, Object> first = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) first.get("message");
                String content = message == null ? null : (String) message.get("content");
                if (content == null) {
                    throw new AiCapabilityException(AiFailureKind.INVALID_RESPONSE, "响应缺少 message.content");
                }
                Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
                int totalTokens = 0;
                if (usage != null && usage.get("total_tokens") instanceof Number n) {
                    totalTokens = n.intValue();
                }
                // 输出达到上限时追加明确提示（历史行为保留），不算结构错误
                if (usage != null && Boolean.TRUE.equals(isFinishReasonLength(first))) {
                    int maxChars = aiConfig.getMaxContentChars();
                    if (content.length() > maxChars) {
                        content = content.substring(0, maxChars);
                    }
                    content += "\n\n---\n*注意：本次回复已达到模型最大输出长度，内容可能被截断。如需更完整的内容，请分多次询问或缩短问题范围。*";
                }
                return new AiRawResult(content, totalTokens);
            }
            throw classifyHttpError(status, response.body(), response.headers().firstValue("Retry-After").orElse(null));
        } catch (AiCapabilityException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new AiCapabilityException(AiFailureKind.TIMEOUT, "读取超时（" + aiConfig.getTimeoutSeconds() + "s）");
        } catch (UnknownHostException | ConnectException e) {
            throw new AiCapabilityException(AiFailureKind.NETWORK_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCapabilityException(AiFailureKind.CANCELLED, "请求被中断");
        } catch (Exception e) {
            // BodyPublishers/JSON 序列化等本地异常：请求本身没发出去，属于非法请求构造
            throw new AiCapabilityException(AiFailureKind.INVALID_REQUEST,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 按 HTTP 状态码与响应体把供应商错误映射为失败分类。 */
    private AiCapabilityException classifyHttpError(int status, String body, String retryAfter) {
        String snippet = body == null ? "" : (body.length() > 300 ? body.substring(0, 300) : body);
        String low = snippet.toLowerCase();
        Long retryAfterMs = parseRetryAfterMs(retryAfter);
        if (status == 401 || status == 403) {
            return new AiCapabilityException(AiFailureKind.INVALID_KEY, "HTTP " + status);
        }
        if (status == 402 || low.contains("insufficient") || low.contains("quota")
                || low.contains("balance") || low.contains("billing") || low.contains("额度") || low.contains("余额")) {
            return new AiCapabilityException(AiFailureKind.QUOTA_EXHAUSTED, "HTTP " + status);
        }
        if (status == 429) {
            return new AiCapabilityException(AiFailureKind.RATE_LIMITED, "HTTP 429", retryAfterMs);
        }
        if (status >= 500) {
            return new AiCapabilityException(AiFailureKind.PROVIDER_ERROR, "HTTP " + status);
        }
        if (status == 400) {
            // 部分供应商用 400 表达模型不支持能力（如对不支持工具的模型下发 tools）
            if (low.contains("does not support") || low.contains("unsupported") || low.contains("not support")) {
                return new AiCapabilityException(AiFailureKind.UNSUPPORTED_CAPABILITY, snippet);
            }
            return new AiCapabilityException(AiFailureKind.INVALID_REQUEST, snippet);
        }
        return new AiCapabilityException(AiFailureKind.UNKNOWN, "HTTP " + status + " " + snippet);
    }

    /** Retry-After 可能是秒数或 HTTP 日期；仅接受数字秒形式，其它忽略。 */
    private Long parseRetryAfterMs(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds <= 0 ? null : seconds * 1000;
        } catch (NumberFormatException e) {
            return null;
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

    /**
     * 聊天场景的失败告知：明确说明当前状态与原因，不伪装成模型回答。
     * 注意：这不是“AI 内容”，而是一段用户可理解的服务状态说明。
     */
    private String generateFallbackReply(String userMessage, AiFailureKind kind) {
        String reason = switch (kind == null ? AiFailureKind.UNKNOWN : kind) {
            case UNCONFIGURED -> "AI 助手尚未配置 API Key。请在「设置 → AI 模型配置」中填写有效的 Key；"
                    + "在此期间，题库练习、规则组卷、考试与错题复习等全部基础功能均可正常使用。";
            case SYSTEM_DISABLED -> "管理员已关闭 AI 增强。基础学习功能不受影响，你可以继续使用题库练习与规则组卷。";
            case USER_DISABLED -> "你已关闭 AI 增强。可在设置中重新开启；基础学习功能不受影响。";
            case INVALID_KEY -> "AI API Key 无效或已失效（401/403）。请检查 Key 是否正确、额度是否正常。";
            case RATE_LIMITED -> "AI 供应商当前限流（429），请稍后再试；也可以直接使用规则组卷与题库练习。";
            case QUOTA_EXHAUSTED -> "AI 账户额度不足，请充值或更换模型配置；基础学习功能不受影响。";
            case TIMEOUT, NETWORK_ERROR -> "AI 响应超时或网络不可达，请稍后重试；也可以先使用规则组卷继续学习。";
            case PROVIDER_ERROR -> "AI 供应商服务暂时异常（5xx），已自动重试仍失败，请稍后再试。";
            case CIRCUIT_OPEN -> "AI 服务已触发熔断保护（短时间失败过多），请稍后再试；基础学习功能不受影响。";
            case BUDGET_EXCEEDED -> "今日 AI 调用预算已用完，请明天再试或联系管理员。";
            case INVALID_RESPONSE -> "AI 返回了无法解析的内容，请重试或换一种问法。";
            case UNSUPPORTED_CAPABILITY -> "当前模型不支持该能力，请切换支持的模型。";
            default -> "AI 服务暂时不可用。你的问题是：\"" + userMessage
                    + "\"。\n\n基础学习功能（题库、组卷、考试、错题）不受影响，可继续使用。";
        };
        return "（AI 助手暂不可用）" + reason;
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

    /**
     * @deprecated Phase 3H：不再返回伪造的兜底分数。AI 不可用时直接抛出结构化失败，
     * 由调用方决定“保持待评分”。考试评卷请使用 {@code ExamAiService.gradeSubjective}。
     */
    @Override
    @Deprecated
    public String gradeEssay(String question, String userAnswer, String correctAnswer) {
        String sysPrompt = "你是一个严格的评卷老师。请对以下主观题答案进行评分（0-100分），"
                + "并给出简短评语。请只返回 JSON 格式：{\"score\": 分数, \"feedback\": \"评语\"}";
        String userPrompt = "题目：" + question + "\n"
                + "参考答案：" + correctAnswer + "\n"
                + "学生答案：" + userAnswer;
        // AI 失败直接向上抛 AiCapabilityException——禁止再回 85/40 假分数
        return callModel(sysPrompt, userPrompt, 0.3, null, null);
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

        if (!isConfigured(userId)) {
            return generateFallbackReply(userMessage,
                    capabilityProperties.isEnabled() ? AiFailureKind.UNCONFIGURED : AiFailureKind.SYSTEM_DISABLED);
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

            requireAiEnabled();
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
        streamChat(systemPrompt, userPrompt, userId, configId, emitter, callback,
                temperature, maxTokens, topP, false);
    }

    /**
     * 深度思考流式对话：推理内容与最终回答分开推送，协议如下（均与旧 delta/done/error 兼容）。
     * <ul>
     *   <li>status：{ phase: thinking | answering }</li>
     *   <li>reasoning：{ content, kind }（仅 Provider 真实返回时）</li>
     *   <li>reasoning_done：{ durationMs, kind }</li>
     *   <li>answer：{ content }（新协议，最终回答增量），同时继续推送 delta 以兼容旧客户端</li>
     *   <li>done：{ content, reasoning?, reasoningKind?, reasoningDurationMs? }</li>
     * </ul>
     * 不伪造推理内容：无推理文本时不发送 reasoning 事件。
     */
    @Override
    public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                           SseEmitter emitter, com.knowflow.service.AiService.StreamCompletionCallback callback,
                           Double temperature, Integer maxTokens, Double topP, boolean deepThinking) {
        streamChat(systemPrompt, userPrompt, userId, configId, emitter, callback,
                temperature, maxTokens, topP, deepThinking, null);
    }

    @Override
    public void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                           SseEmitter emitter, com.knowflow.service.AiService.StreamCompletionCallback callback,
                           Double temperature, Integer maxTokens, Double topP, boolean deepThinking,
                           String model) {
        requireAiEnabled();
        EffectiveAiConfig resolved = resolveConfigById(userId, configId);
        // 用户显式选择的模型优先于配置默认值：能力判定与实际请求必须使用同一个模型
        final EffectiveAiConfig effective = (model != null && !model.isBlank())
                ? new EffectiveAiConfig(resolved.apiKey(), resolved.baseUrl(), model,
                        resolved.provider(), resolved.apiSecret())
                : resolved;
        if (effective.apiKey() == null || effective.apiKey().isEmpty()
                || (!"local".equals(effective.apiKey()) && effective.apiKey().startsWith("sk-placeholder"))) {
            String errMsg = "AI 服务未配置，请先在设置中添加模型配置";
            sendSseEvent(emitter, "error", Map.of("error", errMsg));
            completeSse(emitter);
            if (callback != null) callback.onComplete(errMsg, null, "none", null, false);
            return;
        }
        // full 在 try 外声明：用户中断/异常时仍能保存已生成的截断内容
        StringBuilder full = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        long[] reasoningStart = {0L};
        long[] reasoningDuration = {0L};
        boolean[] reasoningStarted = {false};
        boolean[] reasoningDoneSent = {false};
        boolean[] answered = {false};
        boolean[] clientAborted = {false};
        boolean[] callbackDone = {false};

        ChatModelCapability capability = ChatModelCapabilityRegistry.resolve(effective.provider(),
                effective.model(), providerRegistry.supportsTools(effective.provider()));
        boolean useSummary = deepThinking && capability.isReasoningSupported()
                && "summary".equals(capability.getReasoningKind());
        String reasoningKind = useSummary ? "summary" : "provider_text";

        Consumer<String> onReasoning = text -> {
            if (!deepThinking || text == null || text.isEmpty()) return;
            if (!reasoningStarted[0]) {
                reasoningStarted[0] = true;
                reasoningStart[0] = System.currentTimeMillis();
                sendSseEvent(emitter, "status", Map.of("phase", "thinking"));
            }
            reasoning.append(text);
            if (!sendSseEvent(emitter, "reasoning", Map.of("content", text, "kind", reasoningKind))) {
                clientAborted[0] = true;
                throw new ClientAbortedException();
            }
        };
        Consumer<String> onAnswer = text -> {
            if (text == null || text.isEmpty()) return;
            if (!answered[0]) {
                answered[0] = true;
                emitReasoningDone(emitter, reasoningStarted, reasoningStart, reasoningDuration, reasoningDoneSent, reasoningKind);
                sendSseEvent(emitter, "status", Map.of("phase", "answering"));
            }
            full.append(text);
            // answer 为新协议；delta 继续推送，保证旧客户端（仅识别 delta）不受影响
            if (!sendSseEvent(emitter, "answer", Map.of("content", text))) {
                clientAborted[0] = true;
                throw new ClientAbortedException();
            }
            if (!sendSseEvent(emitter, "delta", Map.of("content", text))) {
                clientAborted[0] = true;
                throw new ClientAbortedException();
            }
        };

        try {
            ModelAdapter.ChatRequest req = new ModelAdapter.ChatRequest();
            req.model = effective.model();
            req.apiKey = effective.apiKey();
            req.apiSecret = effective.apiSecret();
            req.baseUrl = effective.baseUrl();
            req.temperature = temperature != null ? temperature : 0.7;
            req.maxTokens = maxTokens != null ? maxTokens : aiConfig.getMaxTokens();
            req.topP = topP;
            req.messages = Arrays.asList(
                    new ModelAdapter.ChatMessage("system", systemPrompt),
                    new ModelAdapter.ChatMessage("user", userPrompt));

            requireAiEnabled();
            if (useSummary) {
                // OpenAI Responses API：只读取官方 reasoning summary，不冒充完整思维链
                responsesStreamClient.stream(req, new OpenAiResponsesStreamClient.Handler() {
                    @Override
                    public void onReasoning(String delta) {
                        onReasoning.accept(delta);
                    }

                    @Override
                    public void onContent(String delta) {
                        onAnswer.accept(delta);
                    }
                });
                if (!clientAborted[0]) {
                    finishSuccess(emitter, callback, full, reasoning, reasoningStarted, reasoningStart,
                            reasoningDuration, reasoningDoneSent, reasoningKind, callbackDone);
                }
            } else {
                ModelAdapter adapter = adapterFactory.getAdapter(effective.provider());
                // adapter 解析期间总开关可能被关闭：外呼前再校验一次，关闭态绝不消耗付费 Token
                requireAiEnabled();
                adapter.streamChat(req,
                        delta -> {
                            if (delta.reasoning != null && !delta.reasoning.isEmpty()) {
                                onReasoning.accept(delta.reasoning);
                            }
                            if (delta.delta != null && !delta.delta.isEmpty()) {
                                onAnswer.accept(delta.delta);
                            }
                        },
                        done -> {
                            if (clientAborted[0]) return;
                            if (done.content != null && done.content.length() > full.length()) {
                                full.setLength(0);
                                full.append(done.content);
                            }
                            if (reasoning.length() == 0 && done.reasoning != null && !done.reasoning.isEmpty()) {
                                // 适配器在结束时才回填完整推理（增量按理已推送过，这里只兜底累积）
                                reasoning.append(done.reasoning);
                            }
                            finishSuccess(emitter, callback, full, reasoning, reasoningStarted, reasoningStart,
                                    reasoningDuration, reasoningDoneSent, reasoningKind, callbackDone);
                        });
            }
            if (clientAborted[0] && !callbackDone[0]) {
                callbackDone[0] = true;
                if (callback != null) notifyComplete(callback, full.toString(), reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
            }
        } catch (ClientAbortedException e) {
            // 客户端已断开：停止下游读取并保存已生成内容，不再发送任何事件
            callbackDone[0] = true;
            if (callback != null) callback.onComplete(full.toString(), reasoningText(reasoning), false);
        } catch (AiCapabilityException e) {
            if (clientAborted[0]) {
                callbackDone[0] = true;
                if (callback != null) notifyComplete(callback, full.toString(), reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
                return;
            }
            // 已开始的流在外呼前被关停：发送明确错误，不将部分内容标记为完成。
            sendSseEvent(emitter, "error", Map.of("error", e.getKind().userMessage()));
            callbackDone[0] = true;
            if (callback != null) notifyComplete(callback,
                    full.length() > 0 ? full.toString() : e.getKind().userMessage(),
                    reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
        } catch (AiException e) {
            if (clientAborted[0]) {
                callbackDone[0] = true;
                if (callback != null) notifyComplete(callback, full.toString(), reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
                return;
            }
            log.error("流式对话模型异常: userId={}, configId={}, provider={}, err={}",
                    userId, configId, effective.provider(), e.getMessage());
            String errMsg = "AI 流式调用失败：" + e.getMessage();
            sendSseEvent(emitter, "error", Map.of("error", errMsg));
            // 异常时若已生成 partial content，优先回传 partial（success=false），便于上层保存为截断消息
            String partial = full.length() > 0 ? full.toString() : errMsg;
            callbackDone[0] = true;
            if (callback != null) notifyComplete(callback, partial, reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
        } catch (Exception e) {
            if (clientAborted[0]) {
                callbackDone[0] = true;
                if (callback != null) notifyComplete(callback, full.toString(), reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
                return;
            }
            log.error("流式对话异常: userId={}, configId={}, err={}", userId, configId, e.getMessage(), e);
            String errMsg = "AI 流式调用失败：" + e.getMessage();
            sendSseEvent(emitter, "error", Map.of("error", errMsg));
            String partial = full.length() > 0 ? full.toString() : errMsg;
            callbackDone[0] = true;
            if (callback != null) notifyComplete(callback, partial, reasoning, reasoningKind, reasoningStarted, reasoningStart, false);
        } finally {
            completeSse(emitter);
        }
    }

    /** 正常结束：补发 reasoning_done 与 done，并回调持久化层。 */
    private void finishSuccess(SseEmitter emitter, com.knowflow.service.AiService.StreamCompletionCallback callback,
                               StringBuilder full, StringBuilder reasoning,
                               boolean[] reasoningStarted, long[] reasoningStart, long[] reasoningDuration,
                               boolean[] reasoningDoneSent, String reasoningKind, boolean[] callbackDone) {
        if (callbackDone[0]) return;
        emitReasoningDone(emitter, reasoningStarted, reasoningStart, reasoningDuration, reasoningDoneSent, reasoningKind);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", full.toString());
        String reasoningText = reasoningText(reasoning);
        if (reasoningText != null) {
            payload.put("reasoning", reasoningText);
            payload.put("reasoningKind", reasoningKind);
            payload.put("reasoningDurationMs", reasoningDuration[0]);
        }
        sendSseEvent(emitter, "done", payload);
        callbackDone[0] = true;
        if (callback != null) notifyComplete(callback, full.toString(), reasoning, reasoningKind,
                reasoningStarted, reasoningStart, true);
    }

    /**
     * 统一回调：携带推理类型与真实耗时（无推理内容时 kind=none、duration=null）。
     * 保证控制器/持久化层始终拿到同一套字段，避免成功与失败分支结构不一致。
     */
    private void notifyComplete(com.knowflow.service.AiService.StreamCompletionCallback callback, String content,
                                StringBuilder reasoning, String reasoningKind,
                                boolean[] reasoningStarted, long[] reasoningStart, boolean success) {
        if (callback == null) return;
        String text = reasoningText(reasoning);
        Long duration = reasoningStarted[0] ? Math.max(0L, System.currentTimeMillis() - reasoningStart[0]) : null;
        callback.onComplete(content, text, text == null ? "none" : reasoningKind,
                text == null ? null : duration, success);
    }

    private void emitReasoningDone(SseEmitter emitter, boolean[] reasoningStarted, long[] reasoningStart,
                                   long[] reasoningDuration, boolean[] reasoningDoneSent, String reasoningKind) {
        if (!reasoningStarted[0] || reasoningDoneSent[0]) return;
        reasoningDoneSent[0] = true;
        reasoningDuration[0] = Math.max(0L, System.currentTimeMillis() - reasoningStart[0]);
        sendSseEvent(emitter, "reasoning_done",
                Map.of("durationMs", reasoningDuration[0], "kind", reasoningKind));
    }

    private String reasoningText(StringBuilder reasoning) {
        return reasoning.length() == 0 ? null : reasoning.toString();
    }

    /** 客户端断开时用于中断下游流读取的内部异常（不向前端发送 error 事件）。 */
    private static class ClientAbortedException extends RuntimeException {
    }

    @Override
    public ChatModelCapabilityVO getModelCapabilities(Long userId) {
        EffectiveAiConfig effective = resolveEffectiveConfig(userId);
        ChatModelCapabilityVO vo = new ChatModelCapabilityVO();
        vo.setProvider(effective.provider());
        vo.setDefaultModel(effective.model());
        List<String> models = new ArrayList<>(getAvailableModels());
        if (effective.model() != null && !effective.model().isBlank() && !models.contains(effective.model())) {
            models.add(0, effective.model());
        }
        boolean supportsTools = providerRegistry.supportsTools(effective.provider());
        for (String model : models) {
            vo.getModels().add(ChatModelCapabilityRegistry.resolve(effective.provider(), model, supportsTools));
        }
        return vo;
    }

    /** 模型可用性检测：发送一个极短请求，验证配置是否可用并测量延迟。 */
    @Override
    public String healthCheck(Long userId, Long configId) {
        if (!capabilityProperties.isEnabled()) return "{\"ok\":false,\"error\":\"AI 增强已被系统关闭\"}";
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
            requireAiEnabled();
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
        requireAiEnabled();
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
        // 不支持 tools 的模型（如 Ollama 上的 deepseek-coder:6.7b）直接下发 tools 会 400，必须跳过
        boolean supportTools = providerRegistry.supportsTools(effective.provider());
        req.tools = supportTools ? tools : null;
        req.temperature = 0.3;
        req.maxTokens = aiConfig.getMaxTokens();
        try {
            requireAiEnabled();
            return adapter.chat(req);
        } catch (AiCapabilityException e) {
            throw e;
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
        requireAiEnabled();
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
        // 不支持 tools 的模型（如 Ollama 上的 deepseek-coder:6.7b）直接下发 tools 会 400，必须跳过
        boolean supportTools = providerRegistry.supportsTools(effective.provider());
        req.tools = supportTools ? tools : null;
        req.temperature = temperature != null ? temperature : 0.3;
        req.maxTokens = maxTokens != null ? maxTokens : aiConfig.getMaxTokens();
        req.topP = topP;
        try {
            requireAiEnabled();
            adapter.streamChat(req, onToken, onDone);
        } catch (AiCapabilityException e) {
            throw e;
        } catch (AiException e) {
            throw new BusinessException("AI 流式调用失败：" + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("AI 流式调用异常：" + e.getMessage());
        }
    }

    @Override
    public boolean supportsTools(Long userId, Long configId) {
        try {
            EffectiveAiConfig effective = resolveConfigById(userId, configId);
            return providerRegistry.supportsTools(effective.provider());
        } catch (Exception e) {
            // 解析失败时保守地认为支持，让上层按正常路径走（失败再降级）
            return true;
        }
    }

    /** 推送 SSE 事件（data 字段为 JSON 字符串）。返回 false 表示客户端已断开。 */
    private boolean sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: event={}, err={}", eventName, e.getMessage());
            return false;
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
