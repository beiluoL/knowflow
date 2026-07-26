package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象，封装通知类型、内容及其关联业务信息。
 */
@Data
public class NotificationVO {

    private Long id;

    private Long userId;

    /** 通知类型编码 */
    private String type;

    private String title;

    private String content;

    /** 是否已读，0-未读 1-已读 */
    private Integer isRead;

    /** 关联业务ID */
    private Long relatedId;

    /** 关联业务类型 */
    private String relatedType;

    private LocalDateTime createTime;
}
