package com.knowflow.vo;

import lombok.Data;

/**
 * 学习小组成员视图对象
 */
@Data
public class StudyGroupMemberVO {

    private Long id;

    private Long groupId;

    private Long userId;

    private String userName;

    private String userEmail;

    private String userAvatar;

    /** 成员角色：OWNER-创建者，ADMIN-管理员，MEMBER-普通成员 */
    private String role;

    private String invitedByName;

    private java.time.LocalDateTime joinTime;
}