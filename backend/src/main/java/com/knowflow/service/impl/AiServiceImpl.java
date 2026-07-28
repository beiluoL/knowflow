package com.knowflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiConfig;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.UserAiConfig;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.UserAiConfigMapper;
import com.knowflow.service.AiService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
                return new EffectiveAiConfig(userConfig.getApiKey(), baseUrl, model);
            }
        }
        return new EffectiveAiConfig(aiConfig.getApiKey(), aiConfig.getBaseUrl(), aiConfig.getModel());
    }

    private String getProviderDefaultBaseUrl(String provider) {
        switch (provider) {
            case "deepseek": return "https://api.deepseek.com/v1";
            case "siliconflow": return "https://api.siliconflow.cn/v1";
            case "openai": return "https://api.openai.com/v1";
            case "qwen": return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            default: return aiConfig.getBaseUrl();
        }
    }

    /** 内部持有解析后的 AI 配置。 */
    private record EffectiveAiConfig(String apiKey, String baseUrl, String model) {}

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
}
