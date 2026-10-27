package com.knowflow.controller;

import com.knowflow.service.ChatService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-A 第一批：{@link ChatController} 的 principal 安全收敛测试（MockMvc 打真实端点 + 真实安全过滤链）。
 *
 * <p>缺陷背景：{@code ChatController} 原先内联 {@code (Long) authentication.getPrincipal()}。
 * 对已登录请求该强转成立；但 {@code /api/chat/stream} 因 SSE 异步分发需求被
 * {@code SecurityConfig} 配置为 {@code permitAll}，且未 {@code anonymous().disable()}，
 * 匿名 principal 是字符串 {@code "anonymousUser"} → {@code ClassCastException} → 落进
 * {@code GlobalExceptionHandler} 兜底 → **HTTP 500「系统内部错误」**，
 * 而正确语义应为 **401 未登录**（参照既有 {@code JwtAuthenticationEntryPoint} 的 401 契约）。</p>
 *
 * <p>修复方式：收敛到既有 {@link com.knowflow.common.SecurityUtils}，
 * 复用既有 {@code BusinessException(401, ...)} 业务码机制，不新建鉴权机制。</p>
 *
 * <p>覆盖：匿名 → 401、无效 token → 401、有效 token 行为不变（非 SSE 端点专属断言，
 * 避免测试依赖外部 LLM）、受保护端点过滤链语义不变、用户隔离语义不变。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库名：本项目 H2 url 固定 mem:knowflow + DB_CLOSE_DELAY=-1（跨 Spring 上下文共享同库），
// 新增上下文会重跑 schema 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
@TestPropertySource(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1a_principal;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        // 本类只注入FakeAiConfiguration；开放的是Mock合同，不是真实Provider。
        "knowflow.ai.capability.enabled=true"})
class ChatPrincipalAuthTest {

    /** 本类专属用户，避免与共享库中的种子数据/其他用例相互干扰。 */
    private static final Long UID = 97001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ChatService chatService;

    /** 合法请求体（content 非空，可穿过 @Valid）。 */
    private static final String VALID_BODY = "{\"content\":\"你好\"}";

    private String validToken() {
        return jwtUtils.generateToken(UID, "p1a-user", "USER");
    }

    /**
     * 核心场景：匿名访问 SSE 端点。
     * <p>修复前 → 500；修复后 → 401，且响应体与 {@code JwtAuthenticationEntryPoint} 文案一致。</p>
     */
    @Test
    @DisplayName("P1-A1: 匿名访问 /api/chat/stream → 401（原为 500）")
    void anonymousStreamReturns401() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
    }

    /**
     * 无效/伪造 token：{@code JwtAuthenticationFilter} 验签失败后不建立认证，
     * 等价于匿名 → 401（修复前同样落 500）。
     */
    @Test
    @DisplayName("P1-A2: 无效 token 访问 /api/chat/stream → 401（原为 500）")
    void invalidTokenStreamReturns401() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer not.a.real.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * 有效 token 走通 SSE 正常流程：返回 200（不再是 401/500），
     * 且同步落库的会话归属正确 userId —— 证明收敛后 userId 解析未被破坏。
     * <p>AI 调用由 {@link FakeAiConfiguration} 替身接管，测试不依赖外部 LLM。</p>
     */
    @Test
    @DisplayName("P1-A3: 有效 token 访问 /api/chat/stream → 200，SSE 正常流程不变")
    void validTokenStreamKeepsWorkingFlow() throws Exception {
        // 捕获基线：本类其他用例（P1-A5）也会为同一用户创建会话，故断言增量而非绝对值
        int before = chatService.getConversationList(UID).size();

        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        // streamSend 在调用 AI 之前同步创建会话并落库用户消息：会话必须归属于 token 中的 userId
        assertEquals(before + 1, chatService.getConversationList(UID).size(),
                "SSE 正常流程应基于 token 解析出的真实 userId 建立会话");
    }

    /** 有效 token 的普通读接口行为不变（该端点本就在 authenticated() 之后，属回归保护）。 */
    @Test
    @DisplayName("P1-A4 回归: 有效 token 访问对话列表 → 200（行为不变）")
    void validTokenConversationListReturns200() throws Exception {
        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 有效 token 的写接口行为不变：创建会话返回体带 id，证明 userId 被正确传入 Service。 */
    @Test
    @DisplayName("P1-A5 回归: 有效 token 创建对话 → 200 且返回会话 id")
    void validTokenCreateConversationReturns200() throws Exception {
        mockMvc.perform(post("/api/chat/conversations")
                        .param("title", "P1-A 回归会话")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    /**
     * 受保护端点匿名访问仍由安全过滤链返回 401（文案来自 {@code JwtAuthenticationEntryPoint}）。
     * <p>证明本轮改动只影响 permitAll 的 SSE 端点，未削弱其他端点的鉴权语义。</p>
     */
    @Test
    @DisplayName("P1-A6 回归: 匿名访问受保护对话接口 → 401（过滤链语义不变）")
    void anonymousProtectedEndpointStill401() throws Exception {
        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
    }

    /**
     * 用户隔离语义不变：有效 token 删除不属于自己的会话 → 404 业务异常（而非越权成功）。
     */
    @Test
    @DisplayName("P1-A7 回归: 删除不存在的会话 → 404（用户隔离语义不变）")
    void deleteForeignConversationReturns404() throws Exception {
        mockMvc.perform(delete("/api/chat/conversations/999999")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /**
     * 回归：匿名 + 非法请求体时，参数校验先于鉴权 → 400（与修复前行为一致，未被本轮改动影响）。
     */
    @Test
    @DisplayName("P1-A8 回归: 匿名 + 非法请求体 → 400（校验先于鉴权，行为不变）")
    void anonymousWithInvalidBodyStill400() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
    @Test
    @DisplayName("Chat V1: Owner可读；Other/Admin不可跨用户读写或删除")
    void privateConversationHasNoAdministratorSharingBypass() throws Exception {
        Long id = chatService.createConversation(UID, "Chat V1 ownership").getId();
        mockMvc.perform(get("/api/chat/conversations/" + id + "/messages")
                .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        for (String role : new String[]{"USER", "ADMIN"}) {
            String token = "Bearer " + jwtUtils.generateToken(UID + 1, "chat-other", role);
            mockMvc.perform(get("/api/chat/conversations/" + id + "/messages").header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
            mockMvc.perform(delete("/api/chat/conversations/" + id).header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
            mockMvc.perform(post("/api/chat/send").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"conversationId\":" + id + ",\"content\":\"private\",\"useKnowledgeBase\":false}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        }
        mockMvc.perform(get("/api/chat/conversations/" + id + "/messages"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/chat/conversations/" + id + "/messages")
                .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

}
