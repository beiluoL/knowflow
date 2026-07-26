package com.knowflow.vo;

import lombok.Data;

/**
 * 平台内容健康度指标，基于真实业务数据计算（文档发布率、分类覆盖率等）。
 */
@Data
public class HealthMetric {

    /** 指标名称，如「文档发布率」。 */
    private String label;

    /** 健康度百分比，取值范围 0~100。 */
    private Integer value;

    /** 等级：good / warn / bad，前端据此映射颜色。 */
    private String level;

    /** 补充说明文字。 */
    private String detail;

    /** 图标名称（lucide 图标 key）。 */
    private String icon;
}
