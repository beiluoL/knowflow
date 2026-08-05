package com.knowflow.dto;

import lombok.Data;

import java.util.List;

/**
 * Obsidian 目录一键导入（四模块）请求参数。
 * <p>
 * 用户传入本地目录/文件路径（绝对或相对），系统自动扫描 Markdown 结构，
 * 并按所选模块生成：知识库、学习路径、闪卡、题库。
 */
@Data
public class ObsidianImportDTO {

    /** 本地路径：绝对路径（如 /Users/x/docs/Java集合）或相对路径（基于 relativeTo 基准解析）。 */
    private String path;

    /**
     * 相对路径基准（可选）。当 path 为相对路径时，后端以此基准解析为绝对路径
     * （如服务根目录 uploads/xxx）。为空表示 path 已是绝对路径或由 JVM 工作目录解析。
     */
    private String relativeTo;

    /**
     * 文件选择导入模式：显式指定要导入的单个或多个文件绝对路径。
     * 非空时，仅导入这些文件（不再递归整目录），适合用户手动挑选若干文档/图片。
     * 与 path/relativeTo 互斥：指定 filePaths 时忽略 path。
     */
    private List<String> filePaths;

    /**
     * 目标知识库 ID（顶级分类）。必填，四模块的知识库部分导入到此知识库。
     * 若为空，后端会自动创建一个以目录名为名的新知识库。
     */
    private Long targetCategoryId;

    /**
     * 需要生成的模块列表（单选/多选）：
     * <ul>
     *   <li>knowledge：知识库（始终优先执行，其他模块依赖其产出的文档）</li>
     *   <li>path：学习路径（按目录层级建章节并关联文档）</li>
     *   <li>flashcard：闪卡（按文档标题/段落提炼问答）</li>
     *   <li>quiz：题库（按文档生成选择/判断/简答题）</li>
     * </ul>
     * 默认 ["knowledge","path","flashcard","quiz"]（一次性全部生成）。
     */
    private List<String> modules;

    /** 是否按目录创建子分类（知识库导入选项）。默认 true。 */
    private Boolean createSubCategories = true;

    /** 是否自动生成标签（知识库导入选项）。默认 true。 */
    private Boolean autoTags = true;

    /** 是否启用增量去重（同 sourcePath+contentHash 跳过）。默认 true。 */
    private Boolean incremental = true;

    /** 单篇正文最大字符数。默认 50000。 */
    private Integer maxContentChars = 50000;

    /** 学习路径标题；为空则用目录名。 */
    private String pathTitle;

    /** 学习路径难度；默认 INTERMEDIATE。 */
    private String level = "INTERMEDIATE";

    /** 闪卡生成所用的规则模板 ID（import_template.id），为空则用内置默认规则。 */
    private Long flashcardTemplateId;

    /** 题库生成所用的规则模板 ID（import_template.id），为空则用内置默认规则。 */
    private Long quizTemplateId;
}
