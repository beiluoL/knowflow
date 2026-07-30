package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习小组成员实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_group_member")
public class StudyGroupMember extends BaseEntity {

    /** 小组ID */
    private Long groupId;

    /** 用户ID */
    private Long userId;

    /** 成员角色：OWNER-创建者，ADMIN-管理员，MEMBER-普通成员 */
    private String role;

    /** 邀请人ID */
    private Long invitedBy;

    /** 最后已读消息ID */
    private Long lastReadMessageId;
}