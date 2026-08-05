package com.knowflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入规则模板视图对象：返回模板元数据及内容，并附带派生统计信息。
 */
@Data
public class ImportTemplateVO implements Serializable {

    private Long id;
    private Long userId;
    private String name;
    private String type;
    private String description;
    /** 模板内容 JSON（原样返回，由前端解析渲染） */
    private String content;
    private Integer enabled;
    private Integer isDefault;
    private Integer isPreset;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 字段结构数量（从 content.fieldSchema 解析，便于列表展示） */
    private Integer fieldCount;
    /** 校验规则数量（从 content.validation 解析） */
    private Integer validationCount;
    /** 抽取规则摘要（如「H2 · 单篇 ≤12」），便于列表快速预览 */
    private String ruleSummary;
}
