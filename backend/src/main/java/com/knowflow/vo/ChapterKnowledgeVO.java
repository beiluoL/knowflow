package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * Architecture-4.2：章节 → 知识点解析结果（Knowledge Context 的唯一服务端来源）。
 *
 * <p>推导链（全部为真实数据，不做任何名称猜测）：</p>
 * <pre>
 *   learning_chapter.docIds
 *     → resource_knowledge_mapping(resource_type='DOC', status='ACCEPTED')
 *     → kg_entity
 * </pre>
 *
 * <p>为什么必须新增这个端点（§47）：项目内没有任何现有端点暴露「文档 → 知识点」映射，
 * {@code /api/learning/mastery} 只按用户返回掌握度（无章节维度），
 * {@code /api/knowledge/entity-graph} 是 AI 抽取图谱（口径与 Mastery 映射不同且需按 docId 逐次调用）。
 * 因此本端点是「唯一能可靠回答这一章对应哪些知识点」的只读查询，不新增表 / 不新增字段。</p>
 */
@Data
public class ChapterKnowledgeVO {

    /** 章节 ID */
    private Long chapterId;

    /** 章节标题（当前节点文案用） */
    private String chapterTitle;

    /** 章节关联的文档 ID（来自 learning_chapter.docIds，解析失败为空列表） */
    private List<Long> docIds;

    /** 该章节解析出的知识点（按 id 升序，稳定可测；无映射时为空列表，前端应如实提示） */
    private List<KnowledgeRefVO> knowledge;

    /** 已审核、可读且通过ACCEPTED映射关联的练习；不暴露答案。 */
    private List<QuestionRef> questions;
    public record QuestionRef(Long id, String title, String questionType) {}
}
