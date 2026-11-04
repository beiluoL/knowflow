package com.knowflow.entity;

import com.knowflow.common.persistence.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 章节收藏实体。用户与章节多对多关系，同一用户的收藏切换由服务事务串行化；存量数据库唯一索引仍待历史数据检查与迁移。
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
