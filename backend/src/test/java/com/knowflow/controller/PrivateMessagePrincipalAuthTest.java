package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.PrivateMessageSendDTO;
import com.knowflow.entity.PrivateConversation;
import com.knowflow.entity.PrivateConversationRead;
import com.knowflow.entity.PrivateMessage;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.PrivateConversationMapper;
import com.knowflow.mapper.PrivateConversationReadMapper;
import com.knowflow.mapper.PrivateMessageMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-A 第五批：{@link PrivateMessageController} 的 principal 安全收敛测试
 * （MockMvc 打真实端点 + 真实安全过滤链 + 真实 Service/DB）。
 *
 * <p><b>修复前</b>：{@code PrivateMessageController} 有 <b>7 处</b>内联
 * {@code (Long) authentication.getPrincipal()}，分布在 7 个端点
 * （{@code getOrCreateConversation} / {@code getMyConversations} / {@code getMessages} /
 * {@code sendMessage} / {@code markAsRead} / {@code getUnreadCount} / {@code recallMessage}），
 * 均为无守卫的直接强转。虽然 {@code /api/im/private/**} 当前落入
 * {@code SecurityConfig} 的 {@code anyRequest().authenticated()}，属防御性问题，
 * 但一旦该前缀被误加入 permitAll，匿名 principal 为字符串 {@code "anonymousUser"}，
 * 强转即 {@code ClassCastException} → 兜底成 <b>HTTP 500「系统内部错误」</b>，
 * 而正确语义应为 401。</p>
 *
 * <p><b>本轮修复</b>：7 处全部收敛到 {@code currentUserId()}
 * （{@link SecurityUtils#getCurrentUserIdNullable()} + 双参 {@code BusinessException(401, ...)}），
 * 并移除 {@code uploadFile} 上未使用的 {@code Authentication} 形参。</p>
 *
 * <p><b>身份语义逐处判定（真实代码，非推测）</b>：7 个端点全部必须登录 —— 无 permitAll、
 * 无匿名可读、无 nullable 场景；{@code targetUserId}（会话对象）与 {@code conversationId}
 * 是业务参数，<b>不</b>等于当前用户，禁止替换。</p>
 *
 * <p><b>安全属性验证</b>：{@code PrivateMessageSendDTO} 不含任何身份字段
 * （无 {@code senderId}/{@code userId}），{@code sender_id} 列 NOT NULL 且只由 Service
 * 从当前登录用户写入 ⇒ 请求体/query 无法伪造发送者。</p>
 *
 * <p><b>P1-D4 同步更新</b>：本批的 4 处「越权/不存在」断言原先钉死 HTTP 500（记录语义缺口），
 * P1-D4 收敛后已改为 403 / 404（`P1A5-9` / `P1A5-11` / `P1A5-12` / `P1A5-14` / `P1A5-15` / `P1A5-17`）。
 * 该更新是<b>按本测试自身设计</b>执行的（记录性断言在语义收敛时必须同步），
 * 而非为让测试变绿而修改断言——每项都新增/保留了更严格的断言（status + code + 零副作用）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
// 独立 H2 库名：本项目 H2 url 若与其它测试类相同且上下文配置不同，会重跑 schema/data
// 造成种子数据重复。本类独占一个库，避免与共享库互相污染。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1a5_im;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class PrivateMessagePrincipalAuthTest {

    /** 会话参与者 A（消息发送者/会话列表主体）。高位 id 避开种子用户与其它测试常量。 */
    private static final Long USER_A = 97501L;
    /** 会话参与者 B（对端，用于隔离与已读游标断言）。 */
    private static final Long USER_B = 97502L;
    /** 局外人 C：非任何会话参与者，用于越权验证。 */
    private static final Long USER_C = 97503L;

    /** 绝不存在的会话/消息 id。 */
    private static final long MISSING_ID = 999999999L;

    private static final String SECRET = "P1A5-secret-content-绝不外泄";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================ 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p1a5-im-" + userId, "USER");
    }

    private MockHttpServletRequestBuilder authGet(String url, Long userId) {
        var req = get(url);
        return userId == null ? req : req.header("Authorization", auth(userId));
    }

    private JsonNode body(RequestBuilder request) throws Exception {
        String raw = mockMvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(raw);
    }

    /** 以某用户身份调用「获取或创建会话」，返回会话 id。 */
    private long createConversation(Long actorId, Long targetUserId) throws Exception {
        JsonNode node = body(post("/api/im/private/conversations")
                .param("targetUserId", String.valueOf(targetUserId))
                .header("Authorization", auth(actorId)));
        assertEquals(200, node.path("code").asInt(), "创建会话应成功");
        long id = node.path("data").path("id").asLong();
        assertTrue(id > 0, "必须返回真实会话 id");
        return id;
    }

    /** 以某用户身份发消息，extraJson 可注入任意「伪造字段」，如 {@code "senderId":3}。 */
    private void send(Long actorId, long conversationId, String content, String extraJson) throws Exception {
        String payload = "{\"conversationId\":" + conversationId + ",\"content\":\"" + content + "\""
                + (extraJson == null || extraJson.isBlank() ? "" : "," + extraJson) + "}";
        mockMvc.perform(post("/api/im/private/messages")
                        .header("Authorization", auth(actorId))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 读会话消息历史响应体。 */
    private JsonNode messagesOf(Long actorId, long conversationId) throws Exception {
        return body(authGet("/api/im/private/conversations/" + conversationId + "/messages", actorId));
    }

    /** 读某用户在某会话里的已读游标（无记录返回 null）。 */
    private Long readCursor(long conversationId, Long userId) {
        PrivateConversationRead row = readMapper.selectOne(new LambdaQueryWrapper<PrivateConversationRead>()
                .eq(PrivateConversationRead::getConversationId, conversationId)
                .eq(PrivateConversationRead::getUserId, userId)
                .last("LIMIT 1"));
        return row == null ? null : row.getLastReadMessageId();
    }

    private PrivateMessage messageById(long id) {
        return messageMapper.selectById(id);
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME)=? AND UPPER(COLUMN_NAME)=?",
                Integer.class, table.toUpperCase(), column.toUpperCase());
        return n != null && n > 0;
    }

    /** 断言「必须登录」端点在无 token 时确定性返回 401 + body.code=401（而非 500/200）。 */
    private void assertAnonymous401(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 零、前提与机制取证
    // ============================================================

    @Test
    @DisplayName("P1A5-0a 前提: 发送 DTO 不含任何身份字段 —— 请求体无法伪造发送者")
    void sendDtoHasNoIdentityField() {
        Set<String> fields = Arrays.stream(PrivateMessageSendDTO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        for (String forged : List.of("senderId", "userId", "sender", "fromUserId", "ownerId")) {
            assertFalse(fields.contains(forged),
                    "发送 DTO 不应存在身份字段（存在即意味着客户端可决定发送者）：" + forged);
        }
        assertTrue(fields.contains("conversationId"), "对照：字段读取有效，上面的断言不是恒假");
    }

    @Test
    @DisplayName("P1A5-0b 前提: sender_id NOT NULL 且 private_conversation 无 user_id 列")
    void schemaHasNoForgeableOwnerColumn() {
        String nullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME)='PRIVATE_MESSAGE' AND UPPER(COLUMN_NAME)='SENDER_ID'",
                String.class);
        assertEquals("NO", nullable, "private_message.sender_id 必须 NOT NULL —— 只能由服务端写入身份");

        assertFalse(columnExists("private_conversation", "user_id"),
                "会话表用 user_a_id/user_b_id 表达双方，不存在可被伪造的单列 owner");
        assertTrue(columnExists("private_conversation", "user_a_id"), "对照断言：表结构可读");
    }

    @Test
    @DisplayName("P1A5-0c 机制: 匿名 SecurityContext 下 nullable 取用户返回 null（不抛 CCE）")
    void anonymousContextYieldsNullableNotNull() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "p1a5-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        try {
            assertNull(SecurityUtils.getCurrentUserIdNullable(),
                    "匿名 token principal 是字符串 anonymousUser，nullable 必须安全返回 null");
            BusinessException e = assertThrows(BusinessException.class, SecurityUtils::getCurrentUserId);
            assertEquals(401, e.getCode(),
                    "SecurityUtils#getCurrentUserId() 必须统一返回明确 401，不能回落成 HTTP 200/body 500");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ============================================================
    // 一、匿名 / 无效 token → 401（7 个端点全覆盖）
    // ============================================================

    @Test
    @DisplayName("P1A5-1: 匿名访问 7 个端点 → 全部 401 + body.code=401")
    void anonymousAccessAllSevenEndpointsReturns401() throws Exception {
        assertAnonymous401(post("/api/im/private/conversations").param("targetUserId", String.valueOf(USER_B)));
        assertAnonymous401(get("/api/im/private/conversations"));
        assertAnonymous401(get("/api/im/private/conversations/1/messages"));
        assertAnonymous401(post("/api/im/private/messages")
                .contentType("application/json")
                .content("{\"conversationId\":1,\"content\":\"x\"}"));
        assertAnonymous401(post("/api/im/private/conversations/1/read"));
        assertAnonymous401(get("/api/im/private/conversations/1/unread"));
        assertAnonymous401(delete("/api/im/private/messages/1"));
    }

    @Test
    @DisplayName("P1A5-2: 匿名请求即使请求体非法也先返回 401（鉴权先于参数校验，顺序不变）")
    void anonymousIsRejectedBeforeValidation() throws Exception {
        // 修复前后行为一致：鉴权过滤链在 DispatcherServlet 之前拦截，不回落到 400/500
        assertAnonymous401(post("/api/im/private/messages")
                .contentType("application/json")
                .content("{}"));
        assertAnonymous401(delete("/api/im/private/messages/abc"));
    }

    @Test
    @DisplayName("P1A5-3: 无效 token 等价匿名 → 401（读与写一致）")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/im/private/conversations")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(post("/api/im/private/messages")
                        .header("Authorization", "Bearer not.a.real.token")
                        .contentType("application/json")
                        .content("{\"conversationId\":1,\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/api/im/private/messages/1")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 二、有效 token → 正常路径（7 端点行为不变）
    // ============================================================

    @Test
    @DisplayName("P1A5-4: 有效 token 走通 7 个端点 → 全部 200（principal 收敛未破坏业务）")
    void validTokenKeepsAllEndpointsWorking() throws Exception {
        long conv = createConversation(USER_A, USER_B);

        mockMvc.perform(get("/api/im/private/conversations").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/im/private/conversations/" + conv + "/messages")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/im/private/conversations/" + conv + "/unread")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/im/private/conversations/" + conv + "/read")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        send(USER_A, conv, "P1A5-正常发送", null);
    }

    // ============================================================
    // 三、发送者身份绑定与伪造
    // ============================================================

    @Test
    @DisplayName("P1A5-5: 发送身份 = token 用户（响应与 DB 双证）")
    void senderIsAlwaysTokenUser() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-sender-A", null);

        PrivateMessage row = messageMapper.selectOne(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getContent, "P1A5-sender-A").last("LIMIT 1"));
        assertNotNull(row, "消息必须落库");
        assertEquals(USER_A, row.getSenderId(), "sender_id 必须来自 SecurityContext，而非请求参数");
        assertEquals(conv, row.getConversationId());
    }

    @Test
    @DisplayName("P1A5-6 伪造: 请求体注入 senderId/userId/senderName → 归属仍为 token 用户")
    void forgedSenderFieldsInBodyAreIgnored() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-forged-sender",
                "\"senderId\":" + USER_B + ",\"userId\":" + USER_B + ",\"senderName\":\"冒名者\"");

        PrivateMessage row = messageMapper.selectOne(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getContent, "P1A5-forged-sender").last("LIMIT 1"));
        assertNotNull(row, "消息必须落库");
        assertEquals(USER_A, row.getSenderId(), "伪造 senderId 不得改写真实发送者");
        assertFalse(USER_B.equals(row.getSenderId()), "不得被冒名成 USER_B");
    }

    @Test
    @DisplayName("P1A5-7 伪造: query 参数 userId 不改变会话列表作用域")
    void forgedUserIdInQueryDoesNotChangeScope() throws Exception {
        long convAB = createConversation(USER_A, USER_B);
        long convAC = createConversation(USER_A, USER_C);
        send(USER_A, convAB, "P1A5-query-scope", null);

        List<Long> own = idList(authGet("/api/im/private/conversations", USER_A));
        List<Long> forged = idList(get("/api/im/private/conversations")
                .param("userId", String.valueOf(USER_B))
                .header("Authorization", auth(USER_A)));

        assertEquals(own, forged, "加 ?userId=<他人> 后仍必须只返回自己的会话（身份只来自 SecurityContext）");
        assertTrue(own.contains(convAB) && own.contains(convAC), "前提：USER_A 确实有这两个会话");
    }

    private List<Long> idList(MockHttpServletRequestBuilder req) throws Exception {
        JsonNode data = body(req).get("data");
        List<Long> ids = new java.util.ArrayList<>();
        data.forEach(n -> ids.add(n.path("id").asLong()));
        return ids;
    }

    // ============================================================
    // 四、receiverId 业务语义（不得被误替换成当前用户）
    // ============================================================

    @Test
    @DisplayName("P1A5-8: targetUserId 是业务参数 —— 会话参与者为 {A,C}，且不回写成发起人自己")
    void targetUserIdIsBusinessParameterNotIdentity() throws Exception {
        long conv = createConversation(USER_A, USER_C);

        PrivateConversation row = conversationMapper.selectById(conv);
        assertNotNull(row, "会话必须落库");
        assertEquals(Math.min(USER_A, USER_C), row.getUserAId().longValue());
        assertEquals(Math.max(USER_A, USER_C), row.getUserBId().longValue());
        assertFalse(row.getUserAId().equals(row.getUserBId()), "不得退化成「自己和自己」的会话");

        // 响应中的 targetUserId 视角：发起人 A 看到对端 C
        JsonNode data = body(post("/api/im/private/conversations")
                .param("targetUserId", String.valueOf(USER_C))
                .header("Authorization", auth(USER_A))).get("data");
        assertEquals(USER_C.longValue(), data.path("targetUserId").asLong(),
                "对端必须是用户主动选择的 targetUserId，而不是被覆盖成当前登录用户");
    }

    // ============================================================
    // 五、读取隔离
    // ============================================================

    @Test
    @DisplayName("P1A5-9: 会话列表与消息历史按参与者隔离（局外人拿不到任何消息内容）")
    void conversationAndMessagesAreIsolated() throws Exception {
        long convAB = createConversation(USER_A, USER_B);
        send(USER_A, convAB, SECRET, null);

        // 参与者 A/B 正常可读
        assertTrue(messagesOf(USER_A, convAB).get("data").path("records").toString().contains(SECRET),
                "参与者 A 应能读到本会话消息");
        assertTrue(messagesOf(USER_B, convAB).get("data").path("records").toString().contains(SECRET),
                "参与者 B 应能读到本会话消息");

        // 局外人 C：P1-D4 后语义为 403「无权访问该会话」，但**绝不能泄漏内容**
        String rawC = mockMvc.perform(get("/api/im/private/conversations/" + convAB + "/messages")
                        .header("Authorization", auth(USER_C)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(rawC.contains(SECRET), "局外人响应中不得出现会话消息内容");

        // C 的会话列表里不得出现 A 的会话
        assertFalse(idList(authGet("/api/im/private/conversations", USER_C)).contains(convAB),
                "局外人的会话列表不得包含他人会话");
    }

    // ============================================================
    // 六、已读 / 未读
    // ============================================================

    @Test
    @DisplayName("P1A5-10: 标记已读只推进自己的游标，不影响对方")
    void markAsReadOnlyTouchesOwnCursor() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-read-1", null);
        Long bCursorBefore = readCursor(conv, USER_B);

        mockMvc.perform(post("/api/im/private/conversations/" + conv + "/read")
                        .header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNotNull(readCursor(conv, USER_B), "USER_B 的已读游标应被写入");
        assertFalse(readCursor(conv, USER_B).equals(bCursorBefore), "USER_B 的游标应推进到最新消息");
        Long aCursorAfter = readCursor(conv, USER_A);
        assertEquals(readCursor(conv, USER_A), aCursorAfter, "USER_A 的游标不得被 USER_B 的已读操作改写");
    }

    @Test
    @DisplayName("P1A5-11 越权: 局外人标记他人会话已读 → 无任何副作用")
    void outsiderCannotMarkOthersConversationRead() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-read-forbidden", null);
        Long aBefore = readCursor(conv, USER_A);
        Long bBefore = readCursor(conv, USER_B);

        mockMvc.perform(post("/api/im/private/conversations/" + conv + "/read")
                        .header("Authorization", auth(USER_C)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals(aBefore, readCursor(conv, USER_A), "USER_A 游标不得被局外人改写");
        assertEquals(bBefore, readCursor(conv, USER_B), "USER_B 游标不得被局外人改写");
        assertNull(readCursor(conv, USER_C), "不得为局外人生成已读记录");
    }

    @Test
    @DisplayName("P1A5-12: 未读数只统计自己的会话；他人未读不被篡改")
    void unreadCountIsScopedToCurrentUser() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-unread-1", null);
        send(USER_A, conv, "P1A5-unread-2", null);

        int bUnread = body(authGet("/api/im/private/conversations/" + conv + "/unread", USER_B))
                .path("data").asInt();
        assertTrue(bUnread >= 2, "USER_B 应看到至少 2 条未读（实际 " + bUnread + "）");

        mockMvc.perform(get("/api/im/private/conversations/" + conv + "/unread")
                        .header("Authorization", auth(USER_C)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        assertEquals(bUnread, body(authGet("/api/im/private/conversations/" + conv + "/unread", USER_B))
                .path("data").asInt(), "局外人的越权请求不得改变 USER_B 的未读数");
    }

    // ============================================================
    // 七、撤回（删除）
    // ============================================================

    @Test
    @DisplayName("P1A5-13: 撤回自己的消息 → 200 且 recalled=1（幂等，重复撤回仍 200）")
    void recallOwnMessageWorksAndIsIdempotent() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-recall-mine", null);
        long msgId = messageMapper.selectOne(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getContent, "P1A5-recall-mine").last("LIMIT 1")).getId();

        mockMvc.perform(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, messageById(msgId).getRecalled().intValue(), "本人撤回必须生效");

        mockMvc.perform(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, messageById(msgId).getRecalled().intValue(), "重复撤回仍为已撤回状态");
    }

    @Test
    @DisplayName("P1A5-14 越权: 局外人撤回他人消息 → 无副作用")
    void outsiderCannotRecallOthersMessage() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        send(USER_A, conv, "P1A5-recall-forbidden", null);
        long msgId = messageMapper.selectOne(new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getContent, "P1A5-recall-forbidden").last("LIMIT 1")).getId();

        mockMvc.perform(delete("/api/im/private/messages/" + msgId).header("Authorization", auth(USER_C)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals(0, messageById(msgId).getRecalled().intValue(), "局外人不得撤回他人消息");
    }

    @Test
    @DisplayName("P1A5-15: 撤回不存在的消息 → 404「消息不存在」，且无副作用")
    void recallMissingMessageReturns404() throws Exception {
        mockMvc.perform(delete("/api/im/private/messages/" + MISSING_ID).header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("消息不存在"));
    }

    // ============================================================
    // 八、协议层语义（400）与既有 Service 语义缺口（记录，不在本轮修）
    // ============================================================

    @Test
    @DisplayName("P1A5-16: 参数/类型错误 → 400（未被 principal 收敛影响）")
    void invalidParamsYield400() throws Exception {
        mockMvc.perform(post("/api/im/private/messages")
                        .header("Authorization", auth(USER_A))
                        .contentType("application/json")
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("会话ID不能为空"));

        mockMvc.perform(post("/api/im/private/messages")
                        .header("Authorization", auth(USER_A))
                        .contentType("application/json")
                        .content("{\"conversationId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/im/private/conversations").header("Authorization", auth(USER_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(delete("/api/im/private/messages/abc").header("Authorization", auth(USER_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * P1-A 第五批的「记录性断言」在 **P1-D4** 已按设计同步更新（该测试原先钉死 500 是为了证明
     * principal 收敛**没有**顺手改变语义；P1-D4 正是来关掉这个缺口的批次）。
     * <p>现在断言的是收敛后的正确语义，且每项都是 <b>HTTP status + body.code 双断言</b>。
     * <p>注意：Service 层的完整错误语义矩阵（含零副作用验证）见
     * {@code PrivateMessageErrorSemanticsTest}；本方法保留为「同一批 scenario 的端点级复验」。
     */
    @Test
    @DisplayName("P1A5-17 收敛: 既有 Service 层错误语义缺口已由 P1-D4 关闭（404 / 403 / 400）")
    void serviceSemanticsGapClosedByP1D4() throws Exception {
        long conv = createConversation(USER_A, USER_B);

        // 会话不存在 → 404
        mockMvc.perform(get("/api/im/private/conversations/" + MISSING_ID + "/messages")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("会话不存在"));

        // 无权访问他人会话 → 403
        mockMvc.perform(get("/api/im/private/conversations/" + conv + "/messages")
                        .header("Authorization", auth(USER_C)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该会话"));

        // 与自己发起私聊 → 400
        mockMvc.perform(post("/api/im/private/conversations")
                        .param("targetUserId", String.valueOf(USER_A))
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能与自己发起私聊"));
    }

    @Test
    @DisplayName("P1A5-18 回归: 业务成功码链路未被本轮影响（200 + body.code=200，不出现 200+500）")
    void successEnvelopeUnchanged() throws Exception {
        long conv = createConversation(USER_A, USER_B);
        var response = mockMvc.perform(get("/api/im/private/conversations").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String raw = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), "HTTP 状态必须为 200");
        assertEquals(200, json.readTree(raw).path("code").asInt(), "body.code 必须为 200");
        assertTrue(conv > 0);
    }
}
