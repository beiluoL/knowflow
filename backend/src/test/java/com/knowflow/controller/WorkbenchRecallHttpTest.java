package com.knowflow.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.WbRecallSession;
import com.knowflow.mapper.WbRecallSessionMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 主动回忆（三轮闭卷默写）HTTP 回归测试。
 *
 * <p>覆盖：创建 / 草稿 / 编辑保护 / 轮次幂等 / 遗漏确认 / 第三轮服务端到期门禁 /
 * 复制 / 归档 / 删除 / 越权 403 / 笔记来源权限。</p>
 *
 * <p><b>第三轮时间</b>：不修改真实用户数据；仅在本测试独占的 H2 内存库内，
 * 将 {@code round3DueTime} 调整为过去时间以模拟「已到期」，用于验证服务端门禁。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_recall_http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/knowflow-recall-test-no-config.json"
})
class WorkbenchRecallHttpTest {

    private static final String BASE = "/api/workbench/recall-sessions";
    private static final String SOURCE = "HashMap 基于数组和链表实现，扩容时重新计算索引，红黑树退化时转为链表。";

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private WbRecallSessionMapper recallMapper;

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "recall-" + userId, "USER");
    }

    private JsonNode resultData(String raw) throws Exception {
        JsonNode node = json.readTree(raw);
        assertNotNull(node.get("data"), "接口必须返回 data：" + raw);
        return node.get("data");
    }

    private long createSession(Long userId, String payload) throws Exception {
        String raw = mockMvc.perform(post(BASE)
                        .header("Authorization", auth(userId))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return resultData(raw).asLong();
    }

    private void submitRound(Long userId, long id, int round, String text) throws Exception {
        mockMvc.perform(post(BASE + "/" + id + "/submit")
                        .header("Authorization", auth(userId))
                        .contentType("application/json")
                        .content("{\"round\":" + round + ",\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private JsonNode getSession(Long userId, long id) throws Exception {
        String raw = mockMvc.perform(get(BASE + "/" + id).header("Authorization", auth(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return resultData(raw);
    }

    // ============================ 创建 / 草稿 ============================

    @Test
    @DisplayName("RECALL-1: 创建会话 → 状态 IN_PROGRESS、轮次 1、标题缺省取首行")
    void createSessionStartsAtRoundOne() throws Exception {
        long id = createSession(120001L, "{\"sourceText\":\"# HashMap 原理\\n数组 + 链表\"}");

        JsonNode data = getSession(120001L, id);
        assertEquals("IN_PROGRESS", data.get("status").asText());
        assertEquals(1, data.get("currentRound").asInt());
        assertEquals("HashMap 原理", data.get("title").asText());
        assertEquals("PASTE", data.get("sourceType").asText());
        assertEquals(0, data.get("archived").asInt());
        assertTrue(data.hasNonNull("serverNow"));
        assertFalse(data.get("round3Available").asBoolean());
    }

    @Test
    @DisplayName("RECALL-2: 保存为草稿 → DRAFT，且可继续编辑原文")
    void draftSessionIsEditable() throws Exception {
        long id = createSession(120002L, "{\"title\":\"草稿会话\",\"sourceText\":\"旧原文\",\"draft\":true}");

        mockMvc.perform(put(BASE + "/" + id)
                        .header("Authorization", auth(120002L))
                        .contentType("application/json")
                        .content("{\"title\":\"草稿会话改\",\"sourceText\":\"新原文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title").value("草稿会话改"))
                .andExpect(jsonPath("$.data.sourceText").value("新原文"));
    }

    @Test
    @DisplayName("RECALL-3: 原文为空 → HTTP 400 + code 400")
    void createWithoutSourceRejected() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", auth(120003L))
                        .contentType("application/json")
                        .content("{\"title\":\"空原文\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 权限隔离 ============================

    @Test
    @DisplayName("RECALL-4: 他人会话读/改/删 → HTTP 403 + code 403，且零副作用")
    void otherUserSessionYields403() throws Exception {
        long id = createSession(120004L, "{\"title\":\"本人会话\",\"sourceText\":\"原始内容\"}");

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", auth(120005L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put(BASE + "/" + id)
                        .header("Authorization", auth(120005L))
                        .contentType("application/json")
                        .content("{\"title\":\"越权改名\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", auth(120005L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals("本人会话", getSession(120004L, id).get("title").asText());
    }

    @Test
    @DisplayName("RECALL-5: 匿名访问 → HTTP 401 + code 401")
    void anonymousYields401() throws Exception {
        long id = createSession(120006L, "{\"sourceText\":\"匿名测试\"}");
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("RECALL-6: 列表只返回当前用户会话")
    void listIsScopedToOwner() throws Exception {
        createSession(120007L, "{\"title\":\"A 的会话\",\"sourceText\":\"A\"}");
        createSession(120008L, "{\"title\":\"B 的会话\",\"sourceText\":\"B\"}");

        String raw = mockMvc.perform(get(BASE).header("Authorization", auth(120007L)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode list = resultData(raw);
        assertTrue(list.isArray());
        assertEquals(1, list.size());
        assertEquals("A 的会话", list.get(0).get("title").asText());
    }

    @Test
    @DisplayName("RECALL-7: 引用他人笔记创建会话 → HTTP 403")
    void noteSourceRequiresOwnership() throws Exception {
        String noteRaw = mockMvc.perform(post("/api/workbench/notes")
                        .header("Authorization", auth(120009L))
                        .contentType("application/json")
                        .content("{\"title\":\"私有笔记\",\"noteColumn\":\"私有内容\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long noteId = resultData(noteRaw).asLong();

        mockMvc.perform(post(BASE)
                        .header("Authorization", auth(120010L))
                        .contentType("application/json")
                        .content("{\"sourceText\":\"引用\",\"noteId\":" + noteId + ",\"sourceType\":\"NOTE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        long ownId = createSession(120009L,
                "{\"sourceText\":\"引用\",\"noteId\":" + noteId + ",\"sourceType\":\"NOTE\"}");
        JsonNode own = getSession(120009L, ownId);
        assertEquals("NOTE", own.get("sourceType").asText());
        assertEquals("私有笔记", own.get("sourceLabel").asText());
    }

    // ============================ 编辑保护 / 轮次 ============================

    @Test
    @DisplayName("RECALL-8: 已开始会话修改原文 → HTTP 409，标题仍可修改")
    void startedSessionLocksSourceText() throws Exception {
        long id = createSession(120011L, "{\"title\":\"原文锁定\",\"sourceText\":\"快照原文\"}");

        mockMvc.perform(put(BASE + "/" + id)
                        .header("Authorization", auth(120011L))
                        .contentType("application/json")
                        .content("{\"sourceText\":\"试图覆盖快照\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(put(BASE + "/" + id)
                        .header("Authorization", auth(120011L))
                        .contentType("application/json")
                        .content("{\"title\":\"仅改标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("仅改标题"))
                .andExpect(jsonPath("$.data.sourceText").value("快照原文"));
    }

    @Test
    @DisplayName("RECALL-9: 第一轮草稿可保存，提交后草稿冻结并顺序推进")
    void draftThenSubmitAdvancesRound() throws Exception {
        long id = createSession(120012L, "{\"sourceText\":\"" + SOURCE + "\"}");

        mockMvc.perform(post(BASE + "/" + id + "/draft")
                        .header("Authorization", auth(120012L))
                        .contentType("application/json")
                        .content("{\"round\":1,\"text\":\"第一轮中途草稿\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.round1Draft").value("第一轮中途草稿"));

        submitRound(120012L, id, 1, "HashMap数组链表扩容索引红黑树");

        JsonNode after = getSession(120012L, id);
        assertEquals(2, after.get("currentRound").asInt());
        assertEquals("HashMap数组链表扩容索引红黑树", after.get("round1Text").asText());
        assertTrue(after.get("round1Score").asInt() > 0);
        assertTrue(after.get("round1Draft").isNull());

        // 已提交轮次不可覆盖
        mockMvc.perform(post(BASE + "/" + id + "/submit")
                        .header("Authorization", auth(120012L))
                        .contentType("application/json")
                        .content("{\"round\":1,\"text\":\"覆盖第一轮\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        // 第一轮草稿也不可再写
        mockMvc.perform(post(BASE + "/" + id + "/draft")
                        .header("Authorization", auth(120012L))
                        .contentType("application/json")
                        .content("{\"round\":1,\"text\":\"再次写草稿\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        assertEquals("HashMap数组链表扩容索引红黑树", getSession(120012L, id).get("round1Text").asText());
    }

    @Test
    @DisplayName("RECALL-10: 跳过第一轮直接提交第二轮 → HTTP 409")
    void roundOrderIsEnforced() throws Exception {
        long id = createSession(120013L, "{\"sourceText\":\"顺序测试\"}");
        mockMvc.perform(post(BASE + "/" + id + "/submit")
                        .header("Authorization", auth(120013L))
                        .contentType("application/json")
                        .content("{\"round\":2,\"text\":\"跳轮\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("RECALL-11: 未提交轮次不能确认遗漏；提交后可持久化")
    void gapsRequireSubmittedRound() throws Exception {
        long id = createSession(120014L, "{\"sourceText\":\"" + SOURCE + "\"}");

        mockMvc.perform(post(BASE + "/" + id + "/gaps")
                        .header("Authorization", auth(120014L))
                        .contentType("application/json")
                        .content("{\"round\":1,\"gaps\":[\"扩容\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        submitRound(120014L, id, 1, "数组链表");
        mockMvc.perform(post(BASE + "/" + id + "/gaps")
                        .header("Authorization", auth(120014L))
                        .contentType("application/json")
                        .content("{\"round\":1,\"gaps\":[\"扩容\",\"红黑树\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        JsonNode data = getSession(120014L, id);
        assertTrue(data.get("gapJson").asText().contains("扩容"));
        assertTrue(data.get("gapJson").asText().contains("红黑树"));
    }

    // ============================ 第三轮到期门禁 ============================

    @Test
    @DisplayName("RECALL-12: 第二轮完成 → 生成 60 分钟后的 dueAt；未到期提交第三轮 → 409")
    void round3DueTimeBlocksEarlySubmit() throws Exception {
        long id = createSession(120015L, "{\"sourceText\":\"" + SOURCE + "\"}");
        submitRound(120015L, id, 1, "数组链表");
        submitRound(120015L, id, 2, "数组链表扩容");

        JsonNode afterRound2 = getSession(120015L, id);
        assertEquals(3, afterRound2.get("currentRound").asInt());
        assertEquals("IN_PROGRESS", afterRound2.get("status").asText());
        assertTrue(afterRound2.hasNonNull("round3DueTime"));
        assertFalse(afterRound2.get("round3Available").asBoolean());
        // 直接读库校验服务端持久化的 dueAt ≈ 完成时间 + 60 分钟（不依赖 JSON 时间格式）
        LocalDateTime due = recallMapper.selectById(id).getRound3DueTime();
        assertNotNull(due);
        assertFalse(due.isBefore(LocalDateTime.now().plusMinutes(59)));
        assertFalse(due.isAfter(LocalDateTime.now().plusMinutes(61)));

        mockMvc.perform(post(BASE + "/" + id + "/submit")
                        .header("Authorization", auth(120015L))
                        .contentType("application/json")
                        .content("{\"round\":3,\"text\":\"提前提交\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("第三轮复测尚未到期，请等待复测时间"));
        // 未到期也不能保存第三轮草稿
        mockMvc.perform(post(BASE + "/" + id + "/draft")
                        .header("Authorization", auth(120015L))
                        .contentType("application/json")
                        .content("{\"round\":3,\"text\":\"提前草稿\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        assertTrue(getSession(120015L, id).get("round3Text").isNull());
    }

    @Test
    @DisplayName("RECALL-13: dueAt 到达后可提交第三轮 → COMPLETED，历史轮次保持不变")
    void round3CompletesAfterDueTime() throws Exception {
        long id = createSession(120016L, "{\"sourceText\":\"" + SOURCE + "\"}");
        submitRound(120016L, id, 1, "数组链表");
        submitRound(120016L, id, 2, "数组链表扩容");

        // 仅在测试独占库内把到期时间调到过去，模拟时间推进（不触碰真实用户数据）
        recallMapper.update(null, Wrappers.<WbRecallSession>lambdaUpdate()
                .eq(WbRecallSession::getId, id)
                .set(WbRecallSession::getRound3DueTime, LocalDateTime.now().minusMinutes(1)));

        JsonNode ready = getSession(120016L, id);
        assertTrue(ready.get("round3Available").asBoolean());

        submitRound(120016L, id, 3, "数组链表扩容索引红黑树");

        JsonNode done = getSession(120016L, id);
        assertEquals("COMPLETED", done.get("status").asText());
        assertTrue(done.hasNonNull("completedTime"));
        assertEquals("数组链表", done.get("round1Text").asText());
        assertEquals("数组链表扩容", done.get("round2Text").asText());
        assertEquals("数组链表扩容索引红黑树", done.get("round3Text").asText());
        assertTrue(done.get("round3Score").asInt() > 0);

        // 完成后不可再覆盖任何轮次
        mockMvc.perform(post(BASE + "/" + id + "/submit")
                        .header("Authorization", auth(120016L))
                        .contentType("application/json")
                        .content("{\"round\":3,\"text\":\"覆盖完成记录\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ============================ 复制 / 归档 / 删除 ============================

    @Test
    @DisplayName("RECALL-14: 复制为新会话 → DRAFT 副本，原文快照一致")
    void copyCreatesDraftSnapshot() throws Exception {
        long id = createSession(120017L, "{\"title\":\"原会话\",\"sourceText\":\"复制原文\"}");
        submitRound(120017L, id, 1, "复制原文");

        String raw = mockMvc.perform(post(BASE + "/" + id + "/copy")
                        .header("Authorization", auth(120017L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long copyId = resultData(raw).asLong();

        assertNotEquals(id, copyId);
        JsonNode copy = getSession(120017L, copyId);
        assertEquals("DRAFT", copy.get("status").asText());
        assertEquals("复制原文", copy.get("sourceText").asText());
        assertEquals(1, copy.get("currentRound").asInt());
        assertTrue(copy.get("round1Text").isNull());
        assertTrue(copy.get("title").asText().contains("副本"));
    }

    @Test
    @DisplayName("RECALL-15: 归档 / 取消归档 / 删除")
    void archiveAndDelete() throws Exception {
        long id = createSession(120018L, "{\"sourceText\":\"归档与删除\"}");

        mockMvc.perform(put(BASE + "/" + id + "/archive").param("archived", "1")
                        .header("Authorization", auth(120018L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, getSession(120018L, id).get("archived").asInt());

        mockMvc.perform(put(BASE + "/" + id + "/archive").param("archived", "0")
                        .header("Authorization", auth(120018L)))
                .andExpect(status().isOk());
        assertEquals(0, getSession(120018L, id).get("archived").asInt());

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", auth(120018L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", auth(120018L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
