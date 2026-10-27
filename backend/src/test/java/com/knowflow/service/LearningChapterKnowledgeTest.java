package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.ResourceKnowledgeMapping;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.ResourceKnowledgeMappingMapper;
import com.knowflow.vo.ChapterKnowledgeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.2 §3~§6：章节 → 知识点解析集成测试（真实 H2，无 mock）。
 * <p>
 * 推导链（全部真实映射，不做名称猜测、不新增字段）：
 * {@code learning_chapter.docIds → resource_knowledge_mapping(DOC, ACCEPTED) → kg_entity}。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningChapterKnowledgeTest {

    /** 独立测试用户无关：本测试直接操作资源表，避开 data.sql 种子与用户隔离逻辑。 */
    @Autowired private LearningService learningService;
    @Autowired private LearningChapterMapper chapterMapper;
    @Autowired private ResourceKnowledgeMappingMapper mappingMapper;
    @Autowired private KgEntityMapper kgEntityMapper;
    @Autowired private com.knowflow.mapper.DocDocumentMapper docs;

    @BeforeEach
    void setUp() {
        // 幂等兜底：清掉本测试可能残留的行（每次 H2 全新加载）
        mappingMapper.delete(new LambdaQueryWrapper<ResourceKnowledgeMapping>()
                .eq(ResourceKnowledgeMapping::getResourceType, "DOC")
                .in(ResourceKnowledgeMapping::getResourceId, 900101L, 900202L));
        kgEntityMapper.delete(new LambdaQueryWrapper<KgEntity>().in(KgEntity::getId, 900001L));
        chapterMapper.delete(new LambdaQueryWrapper<LearningChapter>()
                .likeRight(LearningChapter::getTitle, "TEST-CK-"));
    }

    @Test
    @DisplayName("A4.2-1: 章节→知识点真实映射解析（docIds→mapping(DOC,ACCEPTED)→kg_entity）")
    void resolveChapterKnowledge() {
        com.knowflow.entity.DocDocument source=new com.knowflow.entity.DocDocument();source.setId(900101L);source.setTitle("TEST-CK-source");source.setContent("real source");source.setStatus(1);docs.insert(source);
        KgEntity ke = new KgEntity();
        ke.setId(900001L);
        ke.setDocId(900101L);
        ke.setName("JVM GC");
        ke.setType("CONCEPT");
        kgEntityMapper.insert(ke);

        ResourceKnowledgeMapping m = new ResourceKnowledgeMapping();
        m.setResourceType("DOC");
        m.setResourceId(900101L);
        m.setKnowledgeId(900001L);
        m.setSource("MANUAL");
        m.setConfidence(BigDecimal.ONE);
        m.setStatus("ACCEPTED");
        mappingMapper.insert(m);

        LearningChapter ch = new LearningChapter();
        ch.setPathId(1L); // 逻辑外键，引用种子路径，仅满足 NOT NULL 约束
        ch.setTitle("TEST-CK-GC");
        ch.setDocIds("900101");
        chapterMapper.insert(ch);

        ChapterKnowledgeVO vo = learningService.getChapterKnowledge(ch.getId());
        assertEquals(ch.getId(), vo.getChapterId());
        assertEquals("TEST-CK-GC", vo.getChapterTitle());
        assertEquals(List.of(900101L), vo.getDocIds());
        assertEquals(1, vo.getKnowledge().size(), "被 ACCEPTED 映射的文档应解析出 1 个知识点");
        assertEquals(900001L, vo.getKnowledge().get(0).getId());
        assertEquals("JVM GC", vo.getKnowledge().get(0).getName());
        assertEquals("DOC", vo.getKnowledge().get(0).getSource());
    }

    @Test
    @DisplayName("A4.2-2: 章节无 ACCEPTED 映射 → 知识点为空列表（禁止按标题猜测）")
    void emptyWhenNoMapping() {
        LearningChapter ch = new LearningChapter();
        ch.setPathId(1L);
        ch.setTitle("TEST-CK-EMPTY");
        ch.setDocIds("900202");
        chapterMapper.insert(ch);

        ChapterKnowledgeVO vo = learningService.getChapterKnowledge(ch.getId());
        assertNotNull(vo);
        assertTrue(vo.getKnowledge().isEmpty(), "无 ACCEPTED 映射时必须返回空列表，不得猜测");
    }

    @Test
    @DisplayName("A4.2-3: 章节不存在 → 404（禁止回退猜测）")
    void notFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> learningService.getChapterKnowledge(99999999L));
        assertEquals(404, ex.getCode());
    }
}
