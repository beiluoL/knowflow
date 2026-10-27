package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节学习笔记 VO。
 */
@Data
public class LearningNoteVO {
    private Long id;
    private Long userId;
    private Long chapterId;
    private Long pathId;
    private String title;
    /** 笔记正文（Markdown） */
    private String content;
    /** 工作台组织字段，仍与正文共享revision。 */
    private Long folderId;
    private Boolean favorite;
    private Long sourceDocId;

    /** 递增修订号；每次修改必须提交读取到的版本。 */
    private Long revision;
    /** GENERAL 普通笔记 / CHAPTER 章节笔记。 */
    private String sourceType;
    private String contentFormat;
    private String status;
    private String tags;
    /** 旧来源标题，仅作提示，不代表文档访问授权。 */
    private String sourceTitle;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
