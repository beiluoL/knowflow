package com.knowflow.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 导入规则模板创建/更新请求体。
 * <p>content 为模板 JSON 字符串，由前端规则模板编辑器生成，结构包含
 * fieldSchema / rules / validation / style / sourceBinding 五部分。</p>
 */
@Data
public class ImportTemplateDTO implements Serializable {

    /** 模板名称（必填） */
    private String name;

    /** 模板类型：FLASHCARD / QUIZ / PATH（必填） */
    private String type;

    /** 模板描述 */
    private String description;

    /** 模板内容 JSON 字符串（必填） */
    private String content;

    /** 是否启用，默认 1 */
    private Integer enabled;
}
