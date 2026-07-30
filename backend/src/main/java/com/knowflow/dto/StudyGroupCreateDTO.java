package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建学习小组请求参数
 */
@Data
public class StudyGroupCreateDTO {

    @NotBlank(message = "小组名称不能为空")
    private String name;

    private String description;

    private String icon;

    private String color;

    /** 小组类型：PUBLIC-公开，PRIVATE-私有 */
    private String type;

    /** 关联的学习计划ID */
    private Long learningPlanId;
}