package com.knowflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiConfig;
import com.knowflow.entity.DocDocument;
import com.knowflow.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean isConfigured() {
        String key = aiConfig.getApiKey();
        return key != null && !key.isEmpty() && !key.startsWith("sk-placeholder");
    }

    /** 调用大模型生成回复；未配置或调用失败时返回降级提示文案，不抛异常。 */
    @Override
    public String chat(String userMessage, List<DocDocument> contextDocs) {
        if (!isConfigured()) {
            return generateFallbackReply(userMessage);
        }
        try {
            String systemPrompt = buildSystemPrompt(contextDocs);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfig.getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            log.error("AI API 调用失败: status={} body={}", response.statusCode(), response.body());
            return generateFallbackReply(userMessage);
        } catch (Exception e) {
            log.error("AI 服务异常: {}", e.getMessage(), e);
            return generateFallbackReply(userMessage);
        }
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
