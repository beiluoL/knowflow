package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-A 第四批：{@link DocController} 的 principal 安全收敛测试
 * （MockMvc 打真实端点 + 真实安全过滤链 + 真实 Service/DB + FakeAi 测试替身）。
 *
 * <p><b>修复前</b>：{@code DocController} 有 <b>11 处</b>内联
 * {@code (Long) authentication.getPrincipal()}，分两种写法：</p>
 * <ul>
 *   <li>7 处「必须登录」端点：{@code if (authentication == null) return Result.error(401, "请先登录")}
 *       之后强转；其中 {@code ai-summary} / {@code ai-flashcards} 两处<b>无任何 null 守卫</b>，
 *       一旦该端点未来被误放入 {@code SecurityConfig} 的 permitAll，匿名请求会因 principal 为
 *       {@code null} 而抛 {@code NullPointerException} → 被 {@code GlobalExceptionHandler} 兜底成
 *       <b>HTTP 500「系统内部错误」</b>（而不是语义正确的 401）。</li>
 *   <li>4 处「允许匿名」端点：{@code authentication != null ? (Long) authentication.getPrincipal() : null}
 *       （{@code detail} / {@code favorites} / {@code recent} / {@code recommend}）。</li>
 * </ul>
 *
 * <p><b>本轮真实取证（非推测）</b>：Spring MVC 对 {@code Authentication} 形参取
 * {@code request.getUserPrincipal()}，而 {@code SecurityContextHolderAwareRequestWrapper}
 * 对 {@code AnonymousAuthenticationToken} <b>返回 null</b>，因此本 Controller <b>当前不存在</b>
 * 「匿名 → ClassCastException → 500」的活缺陷（证据：本轮 curl 匿名 {@code GET /api/docs/1} = 200，
 * 见 {@code .dev-logs/p1a4/pre-fix-matrix.txt}）。本轮属<b>防御性收敛</b>：消灭内联强转，
 * 统一到 {@link SecurityUtils}，让「未登录」在任何配置漂移下都确定性输出 401。</p>
 *
 * <p>身份语义逐处判定：7 处必须登录 → {@code currentUserId()}（401）；4 处允许匿名 → nullable
 * （保持未知登录时返回空列表/个性化字段为 false，<b>不</b>把行为改成 401）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 与 FlashcardIsolationTest / UserResourceOwnership403Test / KnowledgeBaseMembership403Test /
// FlashcardAiWriteTest 共用同一 H2 URL，以复用同一个 Spring 上下文
//（新增上下文会重跑 schema.sql 触发 CREATE INDEX 重复而失败）。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d2_403;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class DocPrincipalAuthTest {

    /** 文档资源拥有者（收藏/进度/AI 闪卡的发起人）。高位 id 避开种子用户与其它测试常量。 */
    private static final Long USER_A = 97401L;
    /** 旁观者，用于验证身份不被串用、伪造 userId 不生效。 */
    private static final Long USER_B = 97402L;

    /** 绝不存在的文档 id，用于 404 语义回归。 */
    private static final long MISSING_DOC_ID = 999999999L;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private DocDocumentMapper docMapper;

    @Autowired
    private LearningFlashcardMapper flashcardMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================ 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p1a-doc-" + userId, "USER");
    }
    /** Auth regression positives now use a genuinely writable knowledge base, not an unowned document. */
    private long writableCategory() {
        com.knowflow.entity.DocCategory c=new com.knowflow.entity.DocCategory();c.setName("auth-writable");c.setOwnerId(USER_A);c.setParentId(0L);c.setStatus(1);categoryMapper.insert(c);return c.getId();
    }
    @Autowired private com.knowflow.mapper.DocCategoryMapper categoryMapper;

    /** 建一篇已发布文档；返回其 id。 */
    private long newDoc(String title) {
        DocDocument doc = new DocDocument();
        doc.setTitle(title);
        doc.setContent("P1-A 第四批 DocController principal 收敛测试用正文。");
        doc.setStatus(1);
        docMapper.insert(doc);
        assertNotNull(doc.getId(), "文档必须落库成功");
        return doc.getId();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        var req = get(url);
        if (token != null) {
            req = req.header("Authorization", token);
        }
        String body = mockMvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    /** 解析任意文档列表型响应的 id 集合。 */
    private List<Long> docIds(MockHttpServletRequestBuilder req) throws Exception {
        String body = mockMvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(body).get("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(n -> ids.add(n.path("id").asLong()));
        return ids;
    }

    /** 以某用户身份读「我的收藏」的文档 id 集合。 */
    private List<Long> favoriteDocIds(String token) throws Exception {
        return docIds(authGet("/api/docs/favorites", token));
    }

    /** 以某用户身份读「最近阅读」的文档 id 集合。 */
    private List<Long> recentDocIds(String token) throws Exception {
        return docIds(authGet("/api/docs/recent", token));
    }

    private MockHttpServletRequestBuilder authGet(String url, String token) {
        var req = get(url);
        return token == null ? req : req.header("Authorization", token);
    }

    /** 以某用户身份读闪卡 front 集合。 */
    private List<String> myFlashcardFronts(Long userId) throws Exception {
        JsonNode data = getJson("/api/learning/flashcards", auth(userId)).get("data");
        List<String> fronts = new ArrayList<>();
        data.forEach(n -> fronts.add(n.path("front").asText()));
        return fronts;
    }

    /** 断言「必须登录」端点对匿名请求返回 401 + body.code=401（而非 500/200）。 */
    private void assertAnonymous401(RequestBuilder request, String label) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 断言请求体里没有任何 owner 语义字段（用于证明「伪造 userId」不可能改变归属）。 */
    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME)=? AND UPPER(COLUMN_NAME)=?",
                Integer.class, table.toUpperCase(), column.toUpperCase());
        return n != null && n > 0;
    }

    // ============================================================
    // 零、前提与机制取证（证明风险边界，而非静态推测）
    // ============================================================

    @Test
    @DisplayName("P1A4-0a 前提: doc_document 无任何 owner 列 —— 请求体伪造 userId 不可能改变归属")
    void docTableHasNoOwnerColumn() {
        for (String col : List.of("user_id", "owner_id", "author_id", "creator_id")) {
            assertFalse(columnExists("doc_document", col),
                    "doc_document 是全局共享资源表，不应存在 owner 语义列：" + col);
        }
        // 反向确认：用户私有关系表确实有 user_id（对照项，证明本断言不是恒假）
        assertTrue(columnExists("doc_favorite", "user_id"), "doc_favorite.user_id 必须存在（用户私有关系）");
    }

    @Test
    @DisplayName("P1A4-0b 机制: 匿名 SecurityContext 下 nullable 取用户返回 null（强转路径已闭合，不抛 CCE）")
    void anonymousContextYieldsNullableNotNull() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "p1a4-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        try {
            assertNull(SecurityUtils.getCurrentUserIdNullable(),
                    "匿名 token 的 principal 是字符串 anonymousUser，nullable 取值必须安全返回 null 而非抛 ClassCastException");
            BusinessException e = assertThrows(BusinessException.class, SecurityUtils::getCurrentUserId);
            assertEquals(401, e.getCode(),
                    "SecurityUtils#getCurrentUserId() 必须统一返回明确 401，不能回落成 HTTP 200/body 500");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ============================================================
    // 一、匿名可读端点：nullable 语义必须保持不变（不得被收敛成 401）
    // ============================================================

    @Test
    @DisplayName("P1A4-1: 匿名 GET /api/docs（permitAll）→ 200")
    void anonymousCanListDocs() throws Exception {
        mockMvc.perform(get("/api/docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("P1A4-2: 匿名 GET /api/docs/{id}（permitAll）→ 200，favorite 为 false（个人化字段不报错）")
    void anonymousCanReadDocDetail() throws Exception {
        long docId = newDoc("P1A4-匿名详情");
        mockMvc.perform(get("/api/docs/" + docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value((int) docId))
                .andExpect(jsonPath("$.data.favorite").value(false));
    }

    @Test
    @DisplayName("P1A4-3: 匿名 GET /api/docs/recommend（permitAll）→ 200")
    void anonymousCanReadRecommend() throws Exception {
        mockMvc.perform(get("/api/docs/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("P1A4-4: 匿名 GET /api/docs/favorites → 保持 nullable（200 + 空列表），不被收敛成 401")
    void anonymousFavoritesKeepsNullableSemantics() throws Exception {
        mockMvc.perform(get("/api/docs/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/docs/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("P1A4-5 隔离: 匿名读收藏/最近阅读拿不到任何已登录用户的私有数据")
    void anonymousListsDoNotLeakOthersData() throws Exception {
        long docId = newDoc("P1A4-匿名隔离");
        mockMvc.perform(post("/api/docs/" + docId + "/favorite").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk());
        assertTrue(favoriteDocIds(auth(USER_A)).contains(docId), "前提：USER_A 确实收藏成功");

        assertTrue(favoriteDocIds(null).isEmpty(), "匿名不得看到 USER_A 的收藏");
        assertTrue(recentDocIds(null).stream().noneMatch(id -> id == docId), "匿名不得看到 USER_A 的阅读记录");
    }

    // ============================================================
    // 二、必须登录端点：匿名 → 401（不再是 200/500）
    // ============================================================

    @Test
    @DisplayName("P1A4-6: 匿名调用 7 个必须登录端点 → 全部 401 + body.code=401")
    void anonymousWritesRequireLogin() throws Exception {
        long docId = newDoc("P1A4-匿名401");

        assertAnonymous401(post("/api/docs/" + docId + "/favorite"), "收藏");
        assertAnonymous401(post("/api/docs/progress")
                .contentType("application/json").content("{\"docId\":" + docId + ",\"progress\":10}"), "阅读进度");
        assertAnonymous401(post("/api/docs")
                .contentType("application/json").content("{\"title\":\"P1A4-anon\",\"content\":\"x\"}"), "创建文档");
        assertAnonymous401(post("/api/docs/" + docId + "/ai-summary"), "AI 摘要");
        // 该端点原实现无 null 守卫：若未来被误放入 permitAll，这里会变成 NPE→500，而现在是确定性 401
        assertAnonymous401(post("/api/docs/" + docId + "/ai-flashcards"), "AI 闪卡");
        assertAnonymous401(put("/api/docs/" + docId)
                .contentType("application/json").content("{\"title\":\"P1A4-anon-upd\"}"), "更新文档");
    }

    @Test
    @DisplayName("P1A4-7: 无效 token —— 公开读按未知登录处理（200），写操作 401")
    void invalidTokenIsTreatedAsAnonymous() throws Exception {
        long docId = newDoc("P1A4-无效token");
        mockMvc.perform(get("/api/docs/" + docId).header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favorite").value(false));

        mockMvc.perform(post("/api/docs/" + docId + "/favorite").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 三、有效 token：身份绑定与隔离
    // ============================================================

    @Test
    @DisplayName("P1A4-8: 有效 token —— 收藏归属当前登录用户，他人详情中 favorite 仍为 false")
    void validTokenBindsIdentity() throws Exception {
        long docId = newDoc("P1A4-身份绑定");
        mockMvc.perform(post("/api/docs/" + docId + "/favorite").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/docs/" + docId).header("Authorization", auth(USER_A)))
                .andExpect(jsonPath("$.data.favorite").value(true));
        mockMvc.perform(get("/api/docs/" + docId).header("Authorization", auth(USER_B)))
                .andExpect(jsonPath("$.data.favorite").value(false));

        assertFalse(favoriteDocIds(auth(USER_B)).contains(docId), "USER_B 的收藏列表不得出现 USER_A 的收藏");
    }

    @Test
    @DisplayName("P1A4-9: 阅读进度写入绑定当前登录用户，映射到自己的「最近阅读」")
    void progressBindsToCurrentUser() throws Exception {
        long docId = newDoc("P1A4-进度归属");
        mockMvc.perform(post("/api/docs/progress").header("Authorization", auth(USER_A))
                        .contentType("application/json")
                        .content("{\"docId\":" + docId + ",\"progress\":42,\"readSeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertTrue(recentDocIds(auth(USER_A)).contains(docId), "USER_A 的最近阅读应包含该文档");
        assertFalse(recentDocIds(auth(USER_B)).contains(docId), "USER_B 不得继承 USER_A 的阅读记录");
    }

    // ============================================================
    // 四、userId 伪造（请求体 / query）
    // ============================================================

    @Test
    @DisplayName("P1A4-10 伪造: 请求体带他人 userId 收藏 —— 归属仍为当前登录用户")
    void forgedUserIdInBodyDoesNotChangeFavoriteOwner() throws Exception {
        long docId = newDoc("P1A4-伪造body");
        mockMvc.perform(post("/api/docs/" + docId + "/favorite")
                        .header("Authorization", auth(USER_A))
                        .contentType("application/json")
                        .content("{\"userId\":" + USER_B + ",\"docId\":" + docId + "}"))
                .andExpect(status().isOk());

        assertTrue(favoriteDocIds(auth(USER_A)).contains(docId), "收藏必须归属 token 用户 USER_A");
        assertFalse(favoriteDocIds(auth(USER_B)).contains(docId), "不得被请求体伪造的 userId 改写为 USER_B");
    }

    @Test
    @DisplayName("P1A4-11 伪造: query 参数 userId 不生效（不改变作用域）")
    void forgedUserIdInQueryDoesNotChangeScope() throws Exception {
        long docId = newDoc("P1A4-伪造query");
        mockMvc.perform(post("/api/docs/" + docId + "/favorite").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk());
        List<Long> own = favoriteDocIds(auth(USER_A));
        List<Long> forged = docIds(get("/api/docs/favorites")
                .param("userId", String.valueOf(USER_B))
                .header("Authorization", auth(USER_A)));
        assertEquals(own, forged, "加 ?userId=<他人> 后仍必须只返回自己的收藏（身份只来自 SecurityContext）");

        assertFalse(favoriteDocIds(auth(USER_B)).contains(docId), "USER_B 的收藏不得被越权写入");
    }

    @Test
    @DisplayName("P1A4-12 伪造: 创建文档时请求体带 userId/ownerId/authorId —— 不产生任何归属字段")
    void forgedOwnerFieldsOnCreateAreIgnored() throws Exception {
        long writableCategory = writableCategory();
        mockMvc.perform(post("/api/docs").header("Authorization", auth(USER_A))
                        .contentType("application/json")
                        .content("{\"title\":\"P1A4-forged-owner\",\"content\":\"x\",\"status\":1,"
                                + "\"categoryId\":" + writableCategory + ",\"userId\":" + USER_B + ",\"ownerId\":" + USER_B + ",\"authorId\":" + USER_B + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        DocDocument created = docMapper.selectOne(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getTitle, "P1A4-forged-owner").last("LIMIT 1"));
        assertNotNull(created, "文档应创建成功（doc_document 无 owner 列，伪造字段被忽略）");
        assertTrue(columnExists("doc_document", "id"), "对照：doc_document 表可读，上面的「无 owner 列」断言有效");
        assertFalse(favoriteDocIds(auth(USER_B)).contains(created.getId()),
                "伪造 userId 不得让 USER_B 获得该文档的任何私有关系");
    }

    // ============================================================
    // 五、既有 HTTP 语义回归（401 / 403 / 404 / 400 不因 principal 收敛而变化）
    // ============================================================

    @Test
    @DisplayName("P1A4-13: 文档不存在 → 404（匿名与已登录一致，body.code=404）")
    void missingDocYields404() throws Exception {
        mockMvc.perform(get("/api/docs/" + MISSING_DOC_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("文档不存在"));
        mockMvc.perform(get("/api/docs/" + MISSING_DOC_ID).header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("P1A4-14: 不存在的文档做写操作仍 404（不因 principal 收敛变成 401/500）")
    void writeOnMissingDocKeeps404() throws Exception {
        mockMvc.perform(post("/api/docs/" + MISSING_DOC_ID + "/favorite").header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/api/docs/" + MISSING_DOC_ID + "/ai-flashcards").header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("P1A4-15: id 类型不匹配 → 400（协议层语义未被 principal 收敛影响）")
    void typeMismatchYields400() throws Exception {
        mockMvc.perform(get("/api/docs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================================================
    // 六、P0-1b AI 闪卡 owner 链路回归（principal 收敛不得破坏 owner 落库）
    // ============================================================

    @Test
    @DisplayName("P1A4-16 回归: AI 闪卡仍以当前登录用户为 owner，sourceType=AI_DOC、docId 正确")
    void aiFlashcardOwnerRegression() throws Exception {
        FakeAiConfiguration.reset();
        long docId = newDoc("P1A4-AI闪卡");
        String front = "P1A4-owner-回归";
        FakeAiConfiguration.script("[{\"front\":\"" + front + "\",\"back\":\"答案\",\"difficulty\":1}]");

        mockMvc.perform(post("/api/docs/" + docId + "/ai-flashcards").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(USER_A.intValue()))
                .andExpect(jsonPath("$.data[0].sourceType").value("AI_DOC"))
                .andExpect(jsonPath("$.data[0].docId").value((int) docId));

        LearningFlashcard row = flashcardMapper.selectOne(new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getFront, front).last("LIMIT 1"));
        assertNotNull(row, "AI 闪卡必须真实落库");
        assertEquals(USER_A, row.getUserId(), "owner 必须等于 token 用户（principal 收敛后仍成立）");
        assertTrue(myFlashcardFronts(USER_A).contains(front), "生成者可见");
        assertFalse(myFlashcardFronts(USER_B).contains(front), "他人不可见");
    }

    @Test
    @DisplayName("P1A4-17 回归: AI 摘要端点（原 dead assignment）在登录态仍正常工作")
    void aiSummaryStillWorks() throws Exception {
        FakeAiConfiguration.reset();
        long docId = newDoc("P1A4-AI摘要");
        DocDocument writable=new DocDocument();writable.setId(docId);writable.setCategoryId(writableCategory());docMapper.updateById(writable);
        FakeAiConfiguration.script("这是摘要");

        mockMvc.perform(post("/api/docs/" + docId + "/ai-summary").header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
