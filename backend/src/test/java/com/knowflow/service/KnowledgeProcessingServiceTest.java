package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.KnowledgeProcessAcceptRequestDTO;
import com.knowflow.dto.KnowledgeProcessRequestDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.KgRelation;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.ResourceKnowledgeMapping;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.mapper.KgRelationMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.ResourceKnowledgeMappingMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.vo.KnowledgeAcceptResultVO;
import com.knowflow.vo.KnowledgeProcessResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static com.knowflow.support.FakeAiConfiguration.THROW_SENTINEL;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.3：Knowledge Processing 2.0 集成测试（真实 H2 + Fake AI Provider）。
 *
 * <p>覆盖 §48/§49：Material→Processing、Processing→Knowledge、去重、关系校验、节点映射、
 * LLM 非法 JSON / 缺字段 / 超时、空资料、长资料、幂等、用户隔离（按用户路由 + 无重复污染）、
 * 不支持模式、资源不存在。每测试独占一组实体 id（H2 单上下文内物理行不被 @TableLogic 清理，
 * 避免主键复用冲突）。</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库名：本项目 H2 url 固定 mem:knowflow + DB_CLOSE_DELAY=-1（跨 Spring 上下文共享同库），
// 新增上下文会重跑 schema 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染/冲突共享库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_kp_4_3;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class KnowledgeProcessingServiceTest {

    private static final Long UID_A = 83001L;
    private static final Long UID_B = 83002L;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private KnowledgeProcessingService processingService;
    @Autowired private DocDocumentMapper docMapper;
    @Autowired private KgEntityMapper kgEntityMapper;
    @Autowired private KgRelationMapper kgRelationMapper;
    @Autowired private ResourceKnowledgeMappingMapper mappingMapper;
    @Autowired private LearningChapterMapper chapterMapper;

    @BeforeEach
    void setUp() {
        FakeAiConfiguration.reset();
        // Both explicit actors exercise administrative mutation; their DB roles must agree
        // with that intent instead of UID_B inheriting UID_A's request authority.
        for (Long id : List.of(UID_A, UID_B)) {
            jdbc.update("MERGE INTO sys_user(id,username,role,deleted) KEY(id) VALUES(?,?,'ADMIN',0)",
                    id, "kp-admin-" + id);
        }
        // Global entity/learning-node mutation positives require a real administrative context.
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(UID_A,"unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @org.junit.jupiter.api.AfterEach void clearAuthentication(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}

    // ===== helpers =====

    private DocDocument doc(Long id, String title, String content) {
        DocDocument d = new DocDocument();
        d.setId(id);
        d.setTitle(title);
        d.setContent(content);
        d.setStatus(1);
        docMapper.insert(d);
        return d;
    }

    private KgEntity seedEntity(Long id, String name, Long docId) {
        KgEntity e = new KgEntity();
        e.setId(id);
        e.setDocId(docId);
        e.setName(name);
        e.setType("CONCEPT");
        kgEntityMapper.insert(e);
        return e;
    }

    private void scriptAi(String json) {
        FakeAiConfiguration.script(json);
    }

    private KnowledgeProcessRequestDTO req(Long docId) {
        KnowledgeProcessRequestDTO r = new KnowledgeProcessRequestDTO();
        r.setDocId(docId);
        return r;
    }

    private KnowledgeProcessRequestDTO req(Long docId, Long nodeId, String mode) {
        KnowledgeProcessRequestDTO r = req(docId);
        r.setLearningNodeId(nodeId);
        r.setMode(mode);
        return r;
    }

    private KnowledgeProcessAcceptRequestDTO acceptReq(Long docId, Long nodeId, String[][] concepts,
                                                       String[][] relations) {
        KnowledgeProcessAcceptRequestDTO dto = new KnowledgeProcessAcceptRequestDTO();
        dto.setDocId(docId);
        dto.setLearningNodeId(nodeId);
        if (concepts != null) {
            dto.setConcepts(new java.util.ArrayList<>());
            for (String[] c : concepts) {
                KnowledgeProcessAcceptRequestDTO.Item it = new KnowledgeProcessAcceptRequestDTO.Item();
                it.setName(c[0]);
                it.setType(c.length > 1 ? c[1] : "CONCEPT");
                it.setDescription(c.length > 2 ? c[2] : null);
                dto.getConcepts().add(it);
            }
        }
        if (relations != null) {
            dto.setRelations(new java.util.ArrayList<>());
            for (String[] r : relations) {
                KnowledgeProcessAcceptRequestDTO.Relation rel = new KnowledgeProcessAcceptRequestDTO.Relation();
                rel.setSource(r[0]);
                rel.setTarget(r[1]);
                rel.setRelation(r[2]);
                dto.getRelations().add(rel);
            }
        }
        return dto;
    }

    private static final String EXTRACTION_JSON = """
            {"summary":"这是一段摘要。",
             "concepts":[
               {"name":"ZKP-GC","type":"CONCEPT","description":"垃圾回收"},
               {"name":"ZKP-Young-GC","type":"PRINCIPLE","description":"新生代回收"}
             ],
             "relations":[
               {"source":"ZKP-Young-GC","target":"ZKP-GC","relation":"PART_OF","description":"组成"}
             ],
             "prerequisites":[
               {"subject":"ZKP-Young-GC","requires":["ZKP-GC"]}
             ]}
            """;

    // ===== tests =====

    @Test
    @DisplayName("A4.3-1: Material→Processing→候选(零写库)：结构合法、无 AI 直接入库")
    void processProducesCandidatesWithoutWriting() {
        doc(931101L, "JVM GC 原理", "正文：GC 负责回收不再使用的对象。Young GC 发生在新生代。");
        scriptAi(EXTRACTION_JSON);
        KnowledgeProcessResultVO vo = processingService.process(req(931101L), UID_A);
        assertNotNull(vo);
        assertEquals("JVM GC 原理", vo.getDocTitle());
        assertEquals(2, vo.getConceptTotal());
        assertTrue(vo.getConcepts().stream().allMatch(c -> "NEW".equals(c.getStatus())));
        assertTrue(vo.getSummary().contains("摘要"));
        assertEquals("KNOWLEDGE_PROCESS_V1", vo.getPromptVersion());
        // relations: PART_OF ×1 + prerequisites 派生 PREREQUISITE(ZKP-GC → ZKP-Young-GC) ×1
        assertEquals(2, vo.getRelationValid());
        assertTrue(vo.getRelations().stream().anyMatch(r ->
                "PREREQUISITE".equals(r.getRelation())
                        && "ZKP-GC".equals(r.getSource()) && "ZKP-Young-GC".equals(r.getTarget())));
        // 关键：process 零写库
        assertEquals(0, kgEntityMapper.selectCount(new LambdaQueryWrapper<KgEntity>()
                .likeRight(KgEntity::getName, "ZKP-")), "process 不得写入 kg_entity");
        assertEquals(0, kgRelationMapper.selectCount(new LambdaQueryWrapper<KgRelation>()
                .likeRight(KgRelation::getDescription, "组成")), "process 不得写入 kg_relation");
    }

    @Test
    @DisplayName("A4.3-2: 知识去重：归一化名与既有实体比对 → EXISTING 复用建议")
    void dedupMarksExisting() {
        doc(931102L, "资料", "正文：MVCC 相关内容。");
        seedEntity(931201L, "ZKP-EXIST", 931102L);
        scriptAi("""
                {"summary":"s","concepts":[{"name":"ZKP-EXIST","type":"CONCEPT","description":"d"}],
                 "relations":[],"prerequisites":[]}
                """);
        KnowledgeProcessResultVO vo = processingService.process(req(931102L), UID_A);
        assertEquals(1, vo.getConceptTotal());
        assertEquals(1, vo.getConceptExisting());
        assertEquals(0, vo.getConceptNew());
        KnowledgeProcessResultVO.ConceptCandidate c = vo.getConcepts().get(0);
        assertEquals("EXISTING", c.getStatus());
        assertEquals(931201L, c.getExistingId());
        assertEquals("ZKP-EXIST", c.getMatchedName());
    }

    @Test
    @DisplayName("A4.3-3: 批内重复概念：大小写归一化去重，仅保留一条并计警告")
    void dedupWithinBatch() {
        doc(931103L, "资料", "正文：重复概念测试。");
        scriptAi("""
                {"summary":"s",
                 "concepts":[{"name":"ZKP-Dup","type":"CONCEPT"},{"name":"zkp-dup","type":"CONCEPT"}],
                 "relations":[],"prerequisites":[]}
                """);
        KnowledgeProcessResultVO vo = processingService.process(req(931103L), UID_A);
        assertEquals(1, vo.getConceptTotal());
        assertEquals(1, vo.getConceptInvalid());
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("重复")));
    }

    @Test
    @DisplayName("A4.3-4: 关系校验：未知引用 → INVALID；自环丢弃")
    void relationValidation() {
        doc(931104L, "资料", "正文：关系校验测试。");
        scriptAi("""
                {"summary":"s",
                 "concepts":[{"name":"ZKP-Real","type":"CONCEPT"}],
                 "relations":[
                   {"source":"ZKP-Real","target":"ZKP-Ghost-概念","relation":"USES","description":"未知引用"},
                   {"source":"ZKP-Real","target":"ZKP-Real","relation":"RELATED_TO","description":"自环"}
                 ],"prerequisites":[]}
                """);
        KnowledgeProcessResultVO vo = processingService.process(req(931104L), UID_A);
        assertEquals(2, vo.getRelationInvalid(), "未知引用与自环均应判无效");
        assertEquals(0, vo.getRelationValid());
        assertTrue(vo.getRelations().stream().noneMatch(r -> "VALID".equals(r.getStatus())));
    }

    @Test
    @DisplayName("A4.3-5: LLM 非法 JSON：自动重试一次后明确失败(422)，不静默通过")
    void invalidJsonFailsWithRetry() {
        doc(931105L, "资料", "正文：非法 JSON 测试。");
        FakeAiConfiguration.script("这不是 JSON 输出", "仍然不是 JSON");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> processingService.process(req(931105L), UID_A));
        assertEquals(422, ex.getCode());
        assertTrue(ex.getMessage().contains("无法解析"));
        assertEquals(2, FakeAiConfiguration.completeCalls, "非法 JSON 应自动重试一次");
        assertEquals(0, kgEntityMapper.selectCount(new LambdaQueryWrapper<KgEntity>()
                .eq(KgEntity::getDocId, 931105L)), "非法输出不得入库");
    }

    @Test
    @DisplayName("A4.3-6: LLM 缺字段：合法 JSON 但无 concepts → 0 候选 + 明确警告")
    void missingFieldYieldsWarning() {
        doc(931106L, "资料", "正文：缺字段测试。");
        scriptAi("{\"summary\":\"只有摘要\"}");
        KnowledgeProcessResultVO vo = processingService.process(req(931106L), UID_A);
        assertEquals(0, vo.getConceptTotal());
        assertTrue(vo.getSummary().contains("只有摘要"));
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("未提取到任何知识候选")));
    }

    @Test
    @DisplayName("A4.3-7: LLM 超时/异常 → 明确失败(422)")
    void llmFailureFailsClearly() {
        doc(931107L, "资料", "正文：超时测试。");
        FakeAiConfiguration.script(THROW_SENTINEL);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> processingService.process(req(931107L), UID_A));
        assertEquals(422, ex.getCode());
        assertTrue(ex.getMessage().contains("AI 服务调用失败"));
    }

    @Test
    @DisplayName("A4.3-8: 空资料 → 422，不调用 LLM")
    void emptyMaterialRejected() {
        doc(931108L, "空资料", null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> processingService.process(req(931108L), UID_A));
        assertEquals(422, ex.getCode());
        assertTrue(ex.getMessage().contains("无正文"));
        assertEquals(0, FakeAiConfiguration.completeCalls, "空资料不应调用 LLM");
    }

    @Test
    @DisplayName("A4.3-9: 长资料 → 段落窗口截断 + 警告（≤12000 字符入模）")
    void longMaterialTruncated() {
        String longBody = "段首。".repeat(5000); // ~15000 字符无换行
        doc(931109L, "长资料", longBody);
        scriptAi("{\"summary\":\"s\",\"concepts\":[],\"relations\":[],\"prerequisites\":[]}");
        KnowledgeProcessResultVO vo = processingService.process(req(931109L), UID_A);
        assertTrue(vo.getContentTruncated(), "超长资料应标记截断");
        assertTrue(vo.getProcessedChars() <= 12000, "入模字符不得超过字符预算");
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("未覆盖")), "应给出未覆盖剩余内容的截断警告");
    }

    @Test
    @DisplayName("A4.3-10: 接受→Knowledge Core 落库；重复接受幂等（实体/关系/映射不重复）")
    void acceptIsIdempotent() {
        doc(931110L, "资料", "正文：接受幂等测试。");
        KnowledgeProcessAcceptRequestDTO dto = acceptReq(931110L, null,
                new String[][]{{"ZKP-ACCEPT-A", "CONCEPT"}, {"ZKP-ACCEPT-B", "CONCEPT"}},
                new String[][]{{"ZKP-ACCEPT-A", "ZKP-ACCEPT-B", "USES"}});

        KnowledgeAcceptResultVO first = processingService.accept(dto, UID_A);
        assertEquals(2, first.getEntityCreated());
        assertEquals(0, first.getEntityReused());
        assertEquals(1, first.getRelationsAdded());
        assertEquals(2, first.getMappingsCreated());
        assertEquals(2, kgEntityMapper.selectCount(new LambdaQueryWrapper<KgEntity>()
                .likeRight(KgEntity::getName, "ZKP-ACCEPT-")));

        KnowledgeAcceptResultVO second = processingService.accept(dto, UID_A);
        assertEquals(0, second.getEntityCreated(), "重复接受不得新建实体");
        assertEquals(2, second.getEntityReused());
        assertEquals(0, second.getRelationsAdded(), "重复接受不得新建边");
        assertEquals(0, second.getMappingsCreated(), "重复接受不得新建映射");
        assertEquals(2, kgEntityMapper.selectCount(new LambdaQueryWrapper<KgEntity>()
                .likeRight(KgEntity::getName, "ZKP-ACCEPT-")), "实体仍只有 2 条");
        Long aId = kgEntityMapper.selectOne(new LambdaQueryWrapper<KgEntity>()
                .eq(KgEntity::getName, "ZKP-ACCEPT-A")).getId();
        assertEquals(1, kgRelationMapper.selectCount(new LambdaQueryWrapper<KgRelation>()
                .eq(KgRelation::getSourceEntityId, aId)));
    }

    @Test
    @DisplayName("A4.3-11: 节点映射：接受时文档幂等挂到章节 doc_ids，且 DOC→ACCEPTED 映射就绪")
    void nodeMapping() {
        doc(931111L, "JVM GC 教程", "正文：节点映射测试。");
        LearningChapter ch = new LearningChapter();
        ch.setId(931301L);
        ch.setPathId(1L);
        ch.setTitle("TEST-KP-node");
        ch.setDocIds("930999");
        chapterMapper.insert(ch);

        KnowledgeProcessAcceptRequestDTO dto = acceptReq(931111L, 931301L,
                new String[][]{{"ZKP-NODE-GC", "CONCEPT"}}, null);
        KnowledgeAcceptResultVO vo = processingService.accept(dto, UID_A);
        assertTrue(vo.isNodeLinked());

        LearningChapter reload = chapterMapper.selectById(931301L);
        assertEquals("930999,931111", reload.getDocIds(), "文档应幂等追加到 doc_ids");
        assertEquals(1, mappingMapper.selectCount(new LambdaQueryWrapper<ResourceKnowledgeMapping>()
                .eq(ResourceKnowledgeMapping::getResourceType, "DOC")
                .eq(ResourceKnowledgeMapping::getResourceId, 931111L)
                .eq(ResourceKnowledgeMapping::getStatus, "ACCEPTED")));
        // 幂等：换一个用户再接受不产生重复 docIds，也不重复建实体
        processingService.accept(dto, UID_B);
        assertEquals("930999,931111", chapterMapper.selectById(931301L).getDocIds());
        assertEquals(1, kgEntityMapper.selectCount(new LambdaQueryWrapper<KgEntity>()
                .eq(KgEntity::getName, "ZKP-NODE-GC")));
    }

    @Test
    @DisplayName("A4.3-12: 接受时未知端点关系被跳过，不写脏边")
    void acceptSkipsUnresolvableRelation() {
        doc(931112L, "资料", "正文：脏边防护。");
        KnowledgeProcessAcceptRequestDTO dto = acceptReq(931112L, null,
                new String[][]{{"ZKP-CLEAN", "CONCEPT"}},
                new String[][]{{"ZKP-CLEAN", "ZKP-NO-SUCH-ENTITY", "USES"}});
        KnowledgeAcceptResultVO vo = processingService.accept(dto, UID_A);
        assertEquals(1, vo.getEntityCreated());
        assertEquals(1, vo.getRelationsSkipped());
        assertEquals(0, vo.getRelationsAdded());
    }

    @Test
    @DisplayName("A4.3-13: 用户隔离：AI 调用按用户路由；B 接受 A 已接受的资料不重复建实体")
    void userIsolation() {
        doc(931113L, "隔离资料", "正文：用户隔离测试。");
        scriptAi("{\"summary\":\"s\",\"concepts\":[{\"name\":\"ZKP-ISO\",\"type\":\"CONCEPT\"}],"
                + "\"relations\":[],\"prerequisites\":[]}");
        processingService.process(req(931113L), UID_A);
        assertEquals(UID_A, FakeAiConfiguration.lastUserId, "A 处理应按 A 的 Key 路由");
        FakeAiConfiguration.script("{\"summary\":\"s\",\"concepts\":[{\"name\":\"ZKP-ISO\",\"type\":\"CONCEPT\"}],"
                + "\"relations\":[],\"prerequisites\":[]}");
        processingService.process(req(931113L), UID_B);
        assertEquals(UID_B, FakeAiConfiguration.lastUserId, "B 处理应按 B 的 Key 路由");

        KnowledgeProcessAcceptRequestDTO dto = acceptReq(931113L, null,
                new String[][]{{"ZKP-ISO", "CONCEPT"}}, null);
        KnowledgeAcceptResultVO a1 = processingService.accept(dto, UID_A);
        assertEquals(1, a1.getEntityCreated());
        KnowledgeAcceptResultVO b1 = processingService.accept(dto, UID_B);
        assertEquals(0, b1.getEntityCreated(), "B 不应重复创建 A 已接受的知识实体");
        assertEquals(1, b1.getEntityReused());
        assertEquals(1, kgEntityMapper.selectCount(new LambdaQueryWrapper<KgEntity>()
                .eq(KgEntity::getName, "ZKP-ISO")));
    }

    @Test
    @DisplayName("A4.3-14: 不支持的 mode → 400 明确报错")
    void unsupportedModeRejected() {
        doc(931114L, "资料", "正文：模式校验。");
        KnowledgeProcessRequestDTO r = req(931114L, null, "STUDY_GUIDE");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> processingService.process(r, UID_A));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的处理模式"));
    }

    @Test
    @DisplayName("A4.3-15: 资料来源或学习节点不存在 → 404")
    void notFound() {
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> processingService.process(req(999999L), UID_A));
        assertEquals(404, ex1.getCode());
        doc(931115L, "资料", "正文：节点不存在。");
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> processingService.process(req(931115L, 999999L, null), UID_A));
        assertEquals(404, ex2.getCode());
    }
}
