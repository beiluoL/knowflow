package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 章节学习笔记保存 DTO。新建与编辑共用，chapterId/pathId 可空。
 */
@Data
public class LearningNoteDTO {
    @NotBlank(message = "笔记标题不能为空")
    private String title;
    /** 笔记正文（Markdown），可空 */
    private String content;
    /** 关联章节ID（可空，表示通用笔记） */
    private Long chapterId;
    /** 关联学习路径ID（可空） */
    private Long pathId;
}
