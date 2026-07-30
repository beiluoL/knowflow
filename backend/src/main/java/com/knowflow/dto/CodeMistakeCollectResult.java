package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码错题归集结果（SC1-AI-03）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeMistakeCollectResult {

    /** 归集后错题记录 ID（幂等场景下为已有记录 ID） */
    private Long mistakeId;

    /** 提取出的错误类型，如 NameError / NullPointerException / 编译错误 */
    private String errorType;

    /** 错误摘要（首行） */
    private String errorSummary;

    /** 是否成功归集 */
    private boolean collected;

    /** 关联的知识库文档（按错误类型/关键词匹配，最多 3 条） */
    private List<RelatedDoc> relatedDocs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedDoc {
        private Long id;
        private String title;
        /** 文档内容片段（前 2 行） */
        private String snippet;
    }
}
