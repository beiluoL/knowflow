package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习小组实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_group")
public class StudyGroup extends BaseEntity {

    /** 小组名称 */
    private String name;

    /** 小组描述 */
    private String description;

    /** 小组图标 */
    private String icon;

    /** 小组颜色 */
    private String color;

    /** 小组类型：PUBLIC-公开，PRIVATE-私有 */
    private String type;

    /** 创建者ID */
    private Long ownerId;

    /** 成员数量 */
    private Integer memberCount;

    /** 小组公告 */
    private String announcement;

    /** 关联的学习计划ID */
    private Long learningPlanId;
}