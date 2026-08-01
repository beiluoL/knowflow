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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
