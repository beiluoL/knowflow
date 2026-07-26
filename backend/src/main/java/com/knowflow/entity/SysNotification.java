package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notification")
/** 系统通知实体，向用户推送各类业务提醒。 */
public class SysNotification extends BaseEntity {

    private Long userId;

    /** 通知类型，如系统 / 互动 / 学习提醒。 */
    private String type;

    private String title;

    private String content;

    /** 是否已读，0 未读 / 1 已读。 */
    private Integer isRead;

    /** 关联业务 ID，如帖子或评论 ID。 */
    private Long relatedId;

    /** 关联业务类型。 */
    private String relatedType;
}
