package com.knowflow.dto;

import lombok.Data;

import java.util.List;

/**
 * Architecture-4.3：知识处理「接受」请求。
 *
 * <p>客户端把处理结果中用户勾选、编辑后的候选回传，服务端<strong>重新做确定性校验后</strong>才落库
 * （不信任客户端数据；入库逻辑全部是规则代码，AI 不直接写库）。</p>
 */
@Data
public class KnowledgeProcessAcceptRequestDTO {

    /** 资料（doc_document）ID，必填。 */
    private Long docId;

    /** 可选：目标学习节点（learning_chapter.id）。提供后会把该文档幂等挂到章节 doc_ids，打通 4.2 节点→知识链路。 */
    private Long learningNodeId;

    /** 用户接受的知识点（编辑后）。 */
    private List<Item> concepts;

    /** 用户接受的关系（编辑后）。 */
    private List<Relation> relations;

    @Data
    public static class Item {
        private String name;
        private String type;
        private String description;
    }

    @Data
    public static class Relation {
        private String source;
        private String target;
        private String relation;
        private String description;
    }
}
