package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Architecture-4.3：知识处理「接受」落库结果。
 *
 * <p>接受即正式进入 Knowledge Core（kg_entity / kg_relation / resource_knowledge_mapping），
 * 全部由确定性规则写入；AI 不直接参与落库。已接受的知识点列表（含 id）供前端生成学习活动候选深链。</p>
 */
@Data
public class KnowledgeAcceptResultVO {

    private Long docId;
    private String docTitle;
    private Long learningNodeId;
    private Long chapterId;
    private String learningNodeTitle;

    /** 新建实体数。 */
    private int entityCreated;
    /** 复用既有实体数（同名归并，不重复建）。 */
    private int entityReused;
    /** 新增关系边数。 */
    private int relationsAdded;
    /** 新增 DOC 映射行数（resource_knowledge_mapping，ACCEPTED）。 */
    private int mappingsCreated;
    /** 文档是否已挂到学习节点（学习节点已含该文档也算 true）。 */
    private boolean nodeLinked;
    /** 跳过的关系数（非法，如引用不存在概念）。 */
    private int relationsSkipped;

    /** 本次接受进入 Knowledge Core 的知识点（供 4.2 深链消费）。 */
    private List<AcceptedKnowledge> accepted = new ArrayList<>();

    @Data
    public static class AcceptedKnowledge {
        private Long knowledgeId;
        private String name;
        private String type;
        /** true=本次新建；false=复用既有实体。 */
        private boolean created;
    }
}
