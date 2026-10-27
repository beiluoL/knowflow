package com.knowflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.DocDocument;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3F 任务 E：搜索「类型 / 时间过滤下推服务端」+ 分页正确性回归。
 *
 * <p>历史实现：类型/时间过滤在前端完成，浏览器先拉至多 1000 条再本地过滤，导致
 * 漏结果、total 不准、翻页基于本地快照；相关度路径服务端只对最新 500 条候选打分，
 * 但 total 回报 DB 全量命中数，造成「total 很大、深页为空」。</p>
 *
 * <p>本类用唯一标记关键词造隔离数据，断言最终分页数据（records/total/pages），而非仅 HTTP 200。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d2_403;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class DocServerFilterPaginationTest {

    private static final String MARKER = "p3fFilterMarker";
    private static final String WINDOW_MARKER = "p3fWindowMarker";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DocDocumentMapper docMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper json = new ObjectMapper();

    private Long d1PdfOld;
    private Long d2MdRecent;
    private Long d3Note10d;
    private Long d4DocOld;
    private Long d5PdfNoteRecent;

    @BeforeAll
    void seed() {
        d1PdfOld = insertDoc(MARKER + "-alpha", "p3f-marker-a.pdf", null, 40);
        d2MdRecent = insertDoc(MARKER + "-beta", "p3f-marker-b.md", "markdown", 3);
        d3Note10d = insertDoc(MARKER + "-gamma", "p3f-marker-c.txt", "笔记", 10);
        d4DocOld = insertDoc(MARKER + "-delta", "p3f-marker-d.txt", null, 40);
        d5PdfNoteRecent = insertDoc(MARKER + "-epsilon", "p3f-marker-e.pdf", "笔记", 1);

        // 相关度候选窗口验证：520 条同关键词文档（超过 RELEVANCE_CANDIDATE_LIMIT=500）。
        // 已造过则跳过（PER_CLASS + 共享上下文，防止重复计数）。
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doc_document WHERE title LIKE ? AND deleted=0",
                Long.class, "%" + WINDOW_MARKER + "%");
        if (existing == null || existing < 520) {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i < 520; i++) {
                rows.add(new Object[]{WINDOW_MARKER + "-" + i, "p3f-window-" + i + ".txt",
                        "x", 1, now, now});
            }
            jdbcTemplate.batchUpdate("INSERT INTO doc_document "
                    + "(title, file_name, content, status, create_time, update_time, deleted) "
                    + "VALUES (?,?,?,?,?,?,0)", rows);
        }
    }

    private Long insertDoc(String title, String fileName, String tags, long ageDays) {
        DocDocument doc = new DocDocument();
        doc.setTitle(title);
        doc.setContent("p3f 内容 " + title);
        doc.setFileName(fileName);
        doc.setTags(tags);
        doc.setStatus(1);
        docMapper.insert(doc);
        LocalDateTime t = LocalDateTime.now().minusDays(ageDays).withNano(0);
        jdbcTemplate.update("UPDATE doc_document SET create_time=?, update_time=? WHERE id=?",
                t, t, doc.getId());
        return doc.getId();
    }

    private JsonNode search(String... params) throws Exception {
        var req = get("/api/docs").param("keyword", MARKER);
        for (int i = 0; i + 1 < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        String body = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("data");
    }

    private Set<Long> idsOf(JsonNode data) {
        Set<Long> ids = new HashSet<>();
        data.get("records").forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("P3F-E1 类型过滤 pdf：只命中 .pdf 文件名，total 精确")
    void filterPdf() throws Exception {
        JsonNode data = search("type", "pdf", "sort", "time");
        assertEquals(2, data.get("total").asInt(), "pdf 类型必须且只能命中两篇 .pdf");
        assertEquals(Set.of(d1PdfOld, d5PdfNoteRecent), idsOf(data));
    }

    @Test
    @DisplayName("P3F-E2 类型过滤 markdown：按 .md 后缀 / markdown 标签命中")
    void filterMarkdown() throws Exception {
        JsonNode data = search("type", "markdown");
        assertEquals(1, data.get("total").asInt());
        assertEquals(Set.of(d2MdRecent), idsOf(data));
    }

    @Test
    @DisplayName("P3F-E3 类型过滤 note：按笔记标签命中，与文件后缀无关")
    void filterNote() throws Exception {
        JsonNode data = search("type", "note");
        assertEquals(2, data.get("total").asInt());
        assertEquals(Set.of(d3Note10d, d5PdfNoteRecent), idsOf(data));
    }

    @Test
    @DisplayName("P3F-E4 类型过滤 doc：非笔记（含 tags 为空；markdown 标签按历史口径也属于 doc）")
    void filterDoc() throws Exception {
        JsonNode data = search("type", "doc");
        assertEquals(3, data.get("total").asInt());
        assertEquals(Set.of(d1PdfOld, d2MdRecent, d4DocOld), idsOf(data));
    }

    @Test
    @DisplayName("P3F-E5 时间过滤：近 7 天命中 2 篇，近 30 天命中 3 篇，40 天前两篇均被排除（含边界日）")
    void filterTimeRange() throws Exception {
        String weekAgo = LocalDateTime.now().minusDays(6).toLocalDate().toString();
        String monthAgo = LocalDateTime.now().minusDays(30).toLocalDate().toString();

        JsonNode week = search("from", weekAgo, "sort", "time");
        assertEquals(2, week.get("total").asInt());
        assertEquals(Set.of(d2MdRecent, d5PdfNoteRecent), idsOf(week));

        JsonNode month = search("from", monthAgo, "sort", "time");
        assertEquals(3, month.get("total").asInt());
        assertEquals(Set.of(d2MdRecent, d3Note10d, d5PdfNoteRecent), idsOf(month));
    }

    @Test
    @DisplayName("P3F-E6 类型+时间组合：近 31 天的 pdf 只有 1 篇（40 天前的 pdf 被时间排除）")
    void combinedTypeAndTime() throws Exception {
        String monthAgo = LocalDateTime.now().minusDays(30).toLocalDate().toString();
        JsonNode data = search("type", "pdf", "from", monthAgo, "sort", "time");
        assertEquals(1, data.get("total").asInt());
        assertEquals(Set.of(d5PdfNoteRecent), idsOf(data));
    }

    @Test
    @DisplayName("P3F-E7 非法 type / 非法日期 → 400，不静默忽略")
    void invalidParamsAre400() throws Exception {
        mockMvc.perform(get("/api/docs").param("keyword", MARKER).param("type", "exe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/docs").param("keyword", MARKER).param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("P3F-E8 分页稳定性：pageSize=2 三页覆盖全部 5 篇且无重复/丢失（id 兜底排序）")
    void stablePaginationAcrossPages() throws Exception {
        Set<Long> all = new HashSet<>();
        for (int p = 1; p <= 3; p++) {
            JsonNode data = search("sort", "time", "pageNum", String.valueOf(p), "pageSize", "2");
            assertEquals(5, data.get("total").asInt(), "total 在翻页过程中必须稳定");
            Set<Long> page = idsOf(data);
            assertTrue(all.stream().noneMatch(page::contains), "第 " + p + " 页与前页出现重复文档：翻页漂移");
            all.addAll(page);
        }
        assertEquals(Set.of(d1PdfOld, d2MdRecent, d3Note10d, d4DocOld, d5PdfNoteRecent), all);
    }

    @Test
    @DisplayName("P3F-E9 相关度候选窗口：520 命中时 total 诚实截断到 500，第 51 页为空；time 排序仍可翻到 520")
    void relevanceWindowIsHonestAndBypassable() throws Exception {
        JsonNode rel = windowSearch("relevance", 1, 10);
        assertEquals(500, rel.get("total").asInt(),
                "相关度打分只覆盖最新 500 条候选，total 必须截断，不能回报 520 造成深页空转");
        assertEquals(50, rel.get("pages").asInt());
        assertEquals(10, rel.get("records").size());

        JsonNode deep = windowSearch("relevance", 51, 10);
        assertEquals(0, deep.get("records").size(), "窗口外的第 51 页必须为空，而不是返回乱序数据");

        JsonNode byTime = windowSearch("time", 52, 10);
        assertEquals(520, byTime.get("total").asInt(), "time 排序走 DB 全量分页，不受相关度候选窗口限制");
        assertEquals(52, byTime.get("pages").asInt());
        assertEquals(10, byTime.get("records").size(), "第 52 页仍应返回数据");
    }

    private JsonNode windowSearch(String sort, int pageNum, int pageSize) throws Exception {
        String body = mockMvc.perform(get("/api/docs")
                        .param("keyword", WINDOW_MARKER)
                        .param("sort", sort)
                        .param("pageNum", String.valueOf(pageNum))
                        .param("pageSize", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("data");
    }
}
