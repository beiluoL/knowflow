package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习小组视图对象
 */
@Data
public class StudyGroupVO {

    private Long id;

    private String name;

    private String description;

    private String icon;

    private String color;

    private String type;

    private Long ownerId;

    private String ownerName;

    private Integer memberCount;

    private String announcement;

    private Long learningPlanId;

    private LocalDateTime createTime;

    /** 未读消息数 */
    private Integer unreadCount;

    /** 当前用户角色（OWNER/ADMIN/MEMBER） */
    private String userRole;
}