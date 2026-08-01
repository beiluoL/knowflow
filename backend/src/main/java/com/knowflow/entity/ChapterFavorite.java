package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 章节收藏实体。用户与章节多对多关系，同一章节仅可收藏一次（唯一索引保证）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chapter_favorite")
public class ChapterFavorite extends BaseEntity {
    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;
    /** 章节ID（逻辑外键 learning_chapter.id） */
    private Long chapterId;
}
