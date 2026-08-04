package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入规则模板实体：用于驱动 Obsidian 目录一键导入时的闪卡/题库抽取逻辑。
 * <p>
 * 模板内容以 JSON 形式存储于 {@link #content}，结构由前端规则模板编辑器定义，包含：
 * 字段结构(fieldSchema)、抽取规则(rules)、校验规则(validation)、展示样式(style)、数据源绑定(sourceBinding)。
 * 预设模板(isPreset=1)对所有用户可见；用户自定义模板(isPreset=0)归属创建者。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("import_template")
public class ImportTemplate extends BaseEntity {

    /** 创建者用户 ID（sys_user.id），预设模板为 1（系统） */
    private Long userId;

    /** 模板名称 */
    private String name;

    /** 模板类型：FLASHCARD 闪卡 / QUIZ 题库 / PATH 学习路径 */
    private String type;

    /** 模板描述 */
    private String description;

    /** 模板内容（JSON 字符串）：字段结构/规则/校验/样式/数据源绑定 */
    private String content;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;

    /** 是否默认模板：1 默认 / 0 否（同类型仅一个默认） */
    private Integer isDefault;

    /** 是否预设模板：1 系统预设 / 0 用户自定义 */
    private Integer isPreset;
}
