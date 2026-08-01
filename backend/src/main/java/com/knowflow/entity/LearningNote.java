package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 章节学习笔记实体。
 * 关联用户与章节（chapter_id 可空，表示通用笔记），正文支持 Markdown。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_note")
public class LearningNote extends BaseEntity {
    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;
    /** 关联章节ID（可空，表示通用笔记） */
    private Long chapterId;
    /** 关联学习路径ID（便于按路径聚合） */
    private Long pathId;
    /** 笔记标题 */
    private String title;
    /** 笔记正文（Markdown） */
    private String content;
}
