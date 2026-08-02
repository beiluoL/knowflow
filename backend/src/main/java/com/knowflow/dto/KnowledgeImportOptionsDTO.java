package com.knowflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库目录批量导入选项。
 * <p>用于 Obsidian 仓库 / 本地目录批量导入时控制目录→分类映射、图片迁移、自动打标等行为。
 */
@Data
public class KnowledgeImportOptionsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标知识库（顶级分类）ID，目录结构将作为其子分类创建。 */
    private Long targetCategoryId;

    /**
     * 是否按目录层级自动创建子分类。
     * true（默认）：目录路径映射为子分类树（受系统 3 级深度限制，超出部分转为标签）；
     * false：所有文档直接挂在目标知识库下，目录路径仅作为标签。
     */
    private Boolean createSubCategories = true;

    /**
     * 是否自动生成标签。
     * true（默认）：从目录路径、文件名、正文关键词提取标签；
     * false：仅保留 Markdown front-matter 中声明的 tags。
     */
    private Boolean autoTags = true;

    /**
     * 是否启用 AI 智能打标（基于正文内容调用大模型生成标签）。
     * 默认 false：AI 调用较慢且消耗 token，按需开启；失败不影响导入。
     */
    private Boolean aiTags = false;

    /**
     * 是否启用增量去重（按 source_path + content_hash 判断已存在则跳过）。
     * 默认 true：重复导入同一目录时跳过未变更文件。
     */
    private Boolean incremental = true;

    /** 单篇文档正文抽取上限（字符数），超出截断；默认 50000。 */
    private Integer maxContentChars = 50000;
}
