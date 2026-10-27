package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.entity.PrivateConversation;
import com.knowflow.entity.PrivateConversationRead;
import com.knowflow.entity.PrivateMessage;
import com.knowflow.exception.GlobalExceptionHandler;
import com.knowflow.mapper.PrivateConversationMapper;
import com.knowflow.mapper.PrivateConversationReadMapper;
import com.knowflow.mapper.PrivateMessageMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-D4：{@code PrivateMessageServiceImpl} 的 <b>HTTP 错误语义</b> 收敛验证
 * （MockMvc 打真实端点 + 真实 Service/DB + 真实 {@code GlobalExceptionHandler} 映射）。
 *
 * <p><b>修复前</b>：Service 用裸 {@code RuntimeException} 表达 5 类用户级错误，
 * 被兜底 {@code @ExceptionHandler(Exception.class)} 统一降级为
 * <b>HTTP 500「系统内部错误」</b>——用户自己的非法操作、权限不足、资源不存在
 * 都被描述成服务端故障，误导用户与监控。</p>
 *
 * <p><b>本轮修复</b>（5 处，全部双参 {@code BusinessException}，文案一字未改）：</p>
 * <table border="1">
 *   <caption>语义收敛表</caption>
 *   <tr><th>方法</th><th>条件</th><th>修复前</th><th>修复后</th></tr>
 *   <tr><td>{@code getOrCreateConversation}</td><td>自己与自己建会话</td><td>500</td><td>400</td></tr>
 *   <tr><td>{@code recallMessage}</td><td>消息不存在</td><td>500</td><td>404</td></tr>
 *   <tr><td>{@code recallMessage}</td><td>撤回他人消息</td><td>500</td><td>403</td></tr>
 *   <tr><td>{@code checkParticipant}</td><td>会话不存在</td><td>500</td><td>404</td></tr>
 *   <tr><td>{@code checkParticipant}</td><td>非会话参与者</td><td>500</td><td>403</td></tr>
 * </table>
 *
 * <p><b>真正的服务端异常一律保持 500</b>：本类同时断言未知 {@code RuntimeException}
 * 仍被兜底为 500，且该兜底路径未被本轮改动（防止「为了消掉 RuntimeException 而误改」）。</p>
 *
 * <p><b>零副作用</b>：越权撤回 / 撤回不存在消息两条路径，都在任何写入之前抛异常，
 * 本类以「行快照相等」验证 recalled / content / update_time / deleted 与
 * 会话、已读游标全部未变。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
// 独立 H2 库名：与 PrivateMessagePrincipalAuthTest 隔离，避免上下文/种子数据互相污染
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d4_im;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class PrivateMessageErrorSemanticsTest {

    /** 会话参与者 A：本人操作的发起者。 */
    private static final Long USER_A = 97601L;
    /** 会话参与者 B：对端（也是「他人消息」的 owner）。 */
    private static final Long USER_B = 97602L;
    /** 局外人 C：非任何会话参与者。 */
    private static final Long USER_C = 97603L;

    /** 绝不存在的会话 / 消息 id。 */
    private static final long MISSING_ID = 999999998L;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PrivateConversationMapper conversationMapper;

    @Autowired
    private PrivateConversationReadMapper readMapper;

    @Autowired
    private PrivateMessageMapper messageMapper;

    // ============================ 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p1d4-im-" + userId, "USER");
    }

    private JsonNode body(MockHttpServletRequestBuilder req) throws Exception {
        return json.readTree(mockMvc.perform(req).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 断言「业务错误」的四元组：HTTP status + body.code + body.message + 无 data 载荷。 */
    private void assertBusinessError(MockHttpServletRequestBuilder req,
                                     int expectedHttp, int expectedCode, String expectedMessage) throws Exception {
        var response = mockMvc.perform(req).andReturn().getResponse();
        JsonNode node = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(expectedHttp, response.getStatus(), "HTTP status 不符：" + node);
        assertEquals(expectedCode, node.path("code").asInt(), "body.code 不符：" + node);
        assertEquals(expectedMessage, node.path("message").asText(), "错误文案被本轮意外改动：" + node);
        assertTrue(node.path("data").isMissingNode() || node.path("data").isNull(),
                "业务错误响应不应携带 data 载荷：" + node);
    }

    private long conversationOf(Long actorId, Long targetUserId) throws Exception {
        JsonNode node = body(post("/api/im/private/conversations")
                .param("targetUserId", String.valueOf(targetUserId))
                .header("Authorization", auth(actorId)));
        assertEquals(200, node.path("code").asInt(), "建会话应成功");
        return node.path("data").path("id").asLong();
    }

    /** 发一条消息并返回其 id。 */
    private long sendAndGetId(Long actorId, long conversationId, String content) throws Exception {
        mockMvc.perform(post("/api/im/private/messages")
                        .header("Authorization", auth(actorId))
                        .contentType("application/json")
                        .content("{\"conversationId\":" + conversationId + ",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        PrivateMessage row = messageMapper.selectOne(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getContent, content).last("LIMIT 1"));
        assertNotNull(row, "消息必须落库：" + content);
        return row.getId();
    }

    /** 消息行的完整快照，用于「零副作用」逐字段比对。 */
    private String snapshot(long messageId) {
        PrivateMessage m = messageMapper.selectById(messageId);
        assertNotNull(m, "快照对象必须存在");
        return m.getId() + "|" + m.getConversationId() + "|" + m.getSenderId() + "|" + m.getContent()
                + "|" + m.getMessageType() + "|" + m.getRecalled() + "|" + m.getDeleted()
                + "|" + m.getCreateTime() + "|" + m.getUpdateTime();
    }

    private String conversationSnapshot(long conversationId) {
        PrivateConversation c = conversationMapper.selectById(conversationId);
        assertNotNull(c, "会话必须存在");
        return c.getId() + "|" + c.getUserAId() + "|" + c.getUserBId()
                + "|" + c.getLastMessageId() + "|" + c.getLastMessageTime() + "|" + c.getUpdateTime();
    }

    private long readRowCount() {
        return readMapper.selectCount(new LambdaQueryWrapper<PrivateConversationRead>());
    }

    private Long readCursor(long conversationId, Long userId) {
        PrivateConversationRead row = readMapper.selectOne(new LambdaQueryWrapper<PrivateConversationRead>()
                .eq(PrivateConversationRead::getConversationId, conversationId)
                .eq(PrivateConversationRead::getUserId, userId).last("LIMIT 1"));
        return row == null ? null : row.getLastReadMessageId();
    }

    // ============================================================
    // 一、checkParticipant：200 / 403 / 404 / 401
    // ============================================================

    @Test
    @DisplayName("P1D4-1 checkParticipant: 本人参与者读本会话 → 200 + code200（正常路径未被破坏）")
    void participantCanReadOwnConversation() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        sendAndGetId(USER_A, conv, "P1D4-owner-readable");

        JsonNode node = body(get("/api/im/private/conversations/" + conv + "/messages")
                .header("Authorization", auth(USER_A)));
        assertEquals(200, node.path("code").asInt());
        assertTrue(node.path("data").path("records").toString().contains("P1D4-owner-readable"),
                "参与者应能读到会话内容");
    }

    @Test
    @DisplayName("P1D4-2 checkParticipant: 非参与者 → 403 + code403「无权访问该会话」（原 500）")
    void outsiderGetsForbiddenNotServerError() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        sendAndGetId(USER_A, conv, "P1D4-forbidden-secret");

        assertBusinessError(get("/api/im/private/conversations/" + conv + "/messages")
                .header("Authorization", auth(USER_C)), 403, 403, "无权访问该会话");
    }

    @Test
    @DisplayName("P1D4-3 checkParticipant: 会话不存在 → 404 + code404「会话不存在」（原 500）")
    void missingConversationGets404() throws Exception {
        assertBusinessError(get("/api/im/private/conversations/" + MISSING_ID + "/messages")
                .header("Authorization", auth(USER_A)), 404, 404, "会话不存在");
    }

    @Test
    @DisplayName("P1D4-4 checkParticipant 同族: 未读/已读/发消息 三条路径语义一致（403 与 404）")
    void checkParticipantSemanticsAreConsistentAcrossEndpoints() throws Exception {
        long conv = conversationOf(USER_A, USER_B);

        // 非参与者：三条路径全部 403，且文案一致
        assertBusinessError(get("/api/im/private/conversations/" + conv + "/unread")
                .header("Authorization", auth(USER_C)), 403, 403, "无权访问该会话");
        assertBusinessError(post("/api/im/private/conversations/" + conv + "/read")
                .header("Authorization", auth(USER_C)), 403, 403, "无权访问该会话");
        assertBusinessError(post("/api/im/private/messages")
                .header("Authorization", auth(USER_C))
                .contentType("application/json")
                .content("{\"conversationId\":" + conv + ",\"content\":\"x\"}"), 403, 403, "无权访问该会话");

        // 会话不存在：三条路径全部 404
        assertBusinessError(get("/api/im/private/conversations/" + MISSING_ID + "/unread")
                .header("Authorization", auth(USER_A)), 404, 404, "会话不存在");
        assertBusinessError(post("/api/im/private/conversations/" + MISSING_ID + "/read")
                .header("Authorization", auth(USER_A)), 404, 404, "会话不存在");
        assertBusinessError(post("/api/im/private/messages")
                .header("Authorization", auth(USER_A))
                .contentType("application/json")
                .content("{\"conversationId\":" + MISSING_ID + ",\"content\":\"x\"}"), 404, 404, "会话不存在");
    }

    @Test
    @DisplayName("P1D4-5 匿名: 未登录仍为 401（认证语义优先于 403/404，未被本轮改动）")
    void anonymousStillGets401() throws Exception {
        assertBusinessError(get("/api/im/private/conversations/" + MISSING_ID + "/messages"), 401, 401, "未登录或登录已失效");
        assertBusinessError(delete("/api/im/private/messages/" + MISSING_ID), 401, 401, "未登录或登录已失效");
        assertBusinessError(post("/api/im/private/conversations").param("targetUserId", String.valueOf(USER_B)),
                401, 401, "未登录或登录已失效");
    }

    // ============================================================
    // 二、recallMessage：200 / 403 / 404 + 零副作用
    // ============================================================

    @Test
    @DisplayName("P1D4-6 recall: 撤回自己的消息 → 200 + code200，recalled=1")
    void recallOwnMessageSucceeds() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        long msgId = sendAndGetId(USER_A, conv, "P1D4-recall-mine");

        mockMvc.perform(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, messageMapper.selectById(msgId).getRecalled().intValue(), "本人撤回必须生效");
    }

    @Test
    @DisplayName("P1D4-7 recall: 撤回他人消息 → 403 + code403「只能撤回自己的消息」（原 500）")
    void recallOthersMessageGetsForbidden() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        long msgId = sendAndGetId(USER_B, conv, "P1D4-recall-others");

        // A 请求撤回 B 的消息
        assertBusinessError(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_A)),
                403, 403, "只能撤回自己的消息");
    }

    @Test
    @DisplayName("P1D4-8 recall: 消息不存在 → 404 + code404「消息不存在」（不是 403）")
    void recallMissingMessageGets404NotForbidden() throws Exception {
        assertBusinessError(delete("/api/im/private/messages/" + MISSING_ID).header("Authorization", auth(USER_A)),
                404, 404, "消息不存在");
    }

    @Test
    @DisplayName("P1D4-9 零副作用: 越权撤回后 消息/会话/已读游标 逐字段未变")
    void forbiddenRecallHasZeroSideEffect() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        long msgId = sendAndGetId(USER_B, conv, "P1D4-zero-side-effect");

        String msgBefore = snapshot(msgId);
        String convBefore = conversationSnapshot(conv);
        long readsBefore = readRowCount();
        Long aCursorBefore = readCursor(conv, USER_A);
        Long bCursorBefore = readCursor(conv, USER_B);

        // 三种越权身份：会话内对端(A 撤回 B 的消息)、局外人(C)
        assertBusinessError(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_A)),
                403, 403, "只能撤回自己的消息");
        assertBusinessError(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_C)),
                403, 403, "只能撤回自己的消息");

        assertEquals(msgBefore, snapshot(msgId), "越权撤回不得改动消息任何字段（含 recalled/content/update_time/deleted）");
        assertEquals(0, messageMapper.selectById(msgId).getRecalled().intValue(), "recalled 必须仍为 0");
        assertEquals("P1D4-zero-side-effect", messageMapper.selectById(msgId).getContent(), "content 必须原样保留");
        assertEquals(convBefore, conversationSnapshot(conv), "越权撤回不得改动会话（lastMessageId/时间戳）");
        assertEquals(readsBefore, readRowCount(), "越权撤回不得新增/改动已读记录");
        assertEquals(aCursorBefore, readCursor(conv, USER_A));
        assertEquals(bCursorBefore, readCursor(conv, USER_B));
    }

    @Test
    @DisplayName("P1D4-10 零副作用: 撤回不存在消息后 全库消息/会话/已读行数不变")
    void missingRecallHasZeroSideEffect() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        long msgId = sendAndGetId(USER_A, conv, "P1D4-missing-recall-baseline");

        long messagesBefore = messageMapper.selectCount(new LambdaQueryWrapper<PrivateMessage>());
        long conversationsBefore = conversationMapper.selectCount(new LambdaQueryWrapper<PrivateConversation>());
        long readsBefore = readRowCount();
        String msgBefore = snapshot(msgId);

        assertBusinessError(delete("/api/im/private/messages/" + MISSING_ID).header("Authorization", auth(USER_A)),
                404, 404, "消息不存在");

        assertEquals(messagesBefore, messageMapper.selectCount(new LambdaQueryWrapper<PrivateMessage>()), "不得新增/删除消息行");
        assertEquals(conversationsBefore, conversationMapper.selectCount(new LambdaQueryWrapper<PrivateConversation>()), "不得新增/删除会话行");
        assertEquals(readsBefore, readRowCount(), "不得新增/改动已读行");
        assertEquals(msgBefore, snapshot(msgId), "既有消息必须逐字段未变");
    }

    // ============================================================
    // 三、getOrCreateConversation：200 / 400
    // ============================================================

    @Test
    @DisplayName("P1D4-11 getOrCreate: A → B → 200 + code200，参与者为 {A,B}（正常路径）")
    void createConversationWithOtherSucceeds() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        PrivateConversation row = conversationMapper.selectById(conv);
        assertEquals(USER_A, row.getUserAId());
        assertEquals(USER_B, row.getUserBId());
    }

    @Test
    @DisplayName("P1D4-12 getOrCreate: A → A → 400 + code400「不能与自己发起私聊」（原 500）")
    void selfConversationGetsBadRequest() throws Exception {
        assertBusinessError(post("/api/im/private/conversations")
                .param("targetUserId", String.valueOf(USER_A))
                .header("Authorization", auth(USER_A)), 400, 400, "不能与自己发起私聊");
    }

    @Test
    @DisplayName("P1D4-13 getOrCreate: 自聊拦截后未落库任何会话（零副作用）")
    void selfConversationRejectedBeforeAnyWrite() throws Exception {
        long before = conversationMapper.selectCount(new LambdaQueryWrapper<PrivateConversation>());
        assertBusinessError(post("/api/im/private/conversations")
                .param("targetUserId", String.valueOf(USER_A))
                .header("Authorization", auth(USER_A)), 400, 400, "不能与自己发起私聊");
        assertEquals(before, conversationMapper.selectCount(new LambdaQueryWrapper<PrivateConversation>()),
                "自聊请求必须在建会话之前被拒绝");
    }

    // ============================================================
    // 四、真正的服务端异常仍为 500（防止过度收敛）
    // ============================================================

    @Test
    @DisplayName("P1D4-14 保留 500: 未知 RuntimeException 仍被兜底为 HTTP 500「系统内部错误」")
    void unknownRuntimeExceptionStillMapsTo500() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> result = handler.handleException(new RuntimeException("模拟未知内部故障"));
        assertEquals(500, result.getCode(), "未知异常必须保持 code=500，不得被本轮降级");
        assertEquals("系统内部错误", result.getMessage(), "不得把内部异常原文/堆栈回传用户");

        ResponseStatus annotation = GlobalExceptionHandler.class
                .getMethod("handleException", Exception.class)
                .getAnnotation(ResponseStatus.class);
        assertNotNull(annotation, "兜底处理器必须声明 HTTP 状态");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, annotation.value(),
                "兜底异常处理器必须仍是 HTTP 500（本轮未改动 GlobalExceptionHandler）");
    }

    @Test
    @DisplayName("P1D4-15 契约: 4xx 业务错误 HTTP status ≡ body.code（不出现 200+4xx 或 4xx+500）")
    void httpStatusAlwaysEqualsBodyCode() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        long msgId = sendAndGetId(USER_B, conv, "P1D4-contract");

        assertStatusEqualsCode(get("/api/im/private/conversations/" + conv + "/messages")
                .header("Authorization", auth(USER_C)), 403);
        assertStatusEqualsCode(get("/api/im/private/conversations/" + MISSING_ID + "/messages")
                .header("Authorization", auth(USER_A)), 404);
        assertStatusEqualsCode(delete("/api/im/private/messages/" + msgId)
                .header("Authorization", auth(USER_A)), 403);
        assertStatusEqualsCode(delete("/api/im/private/messages/" + MISSING_ID)
                .header("Authorization", auth(USER_A)), 404);
        assertStatusEqualsCode(post("/api/im/private/conversations")
                .param("targetUserId", String.valueOf(USER_A))
                .header("Authorization", auth(USER_A)), 400);
        assertStatusEqualsCode(get("/api/im/private/conversations"), 401);
    }

    private void assertStatusEqualsCode(MockHttpServletRequestBuilder req, int expected) throws Exception {
        var response = mockMvc.perform(req).andReturn().getResponse();
        JsonNode node = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(expected, response.getStatus(), "HTTP status 不符：" + node.path("message").asText());
        assertEquals(expected, node.path("code").asInt(),
                "HTTP status 与 body.code 必须一致（否则前端分类链路会拿到矛盾信号）");
    }

    @Test
    @DisplayName("P1D4-16 回归: 成功链路仍为 HTTP 200 + body.code=200（未引入 200+500）")
    void successEnvelopeUnchanged() throws Exception {
        long conv = conversationOf(USER_A, USER_B);
        var response = mockMvc.perform(get("/api/im/private/conversations/" + conv + "/messages")
                        .header("Authorization", auth(USER_A))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(200, json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("code").asInt());
    }
}
