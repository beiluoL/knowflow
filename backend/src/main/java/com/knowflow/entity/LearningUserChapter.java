package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_user_chapter")
/** 用户章节完成记录实体，标记用户已完成的章节。 */
public class LearningUserChapter extends BaseEntity {

    private Long userId;

    private Long pathId;

    private Long chapterId;

    private LocalDateTime completeTime;

    /** 视频观看进度百分比（0-100），L-FORM-01 达标后允许完成章节 */
    private java.math.BigDecimal videoProgress;
}
