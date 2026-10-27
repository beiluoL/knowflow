package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 章节学习笔记保存 DTO。新建与编辑共用，chapterId/pathId 可空。
 */
@Data
public class LearningNoteDTO {
    @jakarta.validation.constraints.Size(max = 200, message = "标题不能超过200字")
    @NotBlank(message = "笔记标题不能为空")
    private String title;
    /** 笔记正文（Markdown），可空 */
    @jakarta.validation.constraints.Size(max = 2000000, message = "正文不能超过两百万字符")
    private String content;
    private Long revision;
    @jakarta.validation.constraints.Size(max = 2000)
    private String tags;
    @jakarta.validation.constraints.Size(max = 500)
    private String sourceTitle;
    private String status;
    @jakarta.validation.constraints.Size(max = 500)
    private String legacyKey;
    /** 关联章节ID（可空，表示通用笔记） */
    private Long chapterId;
    /** 关联学习路径ID（可空） */
    private Long pathId;
    private Long folderId;
    private Boolean favorite;
    private Long sourceDocId;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean folderSpecified;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean sourceDocSpecified;
    public void setFolderId(Long id){folderId=id;folderSpecified=true;}
    public void setSourceDocId(Long id){sourceDocId=id;sourceDocSpecified=true;}
}
