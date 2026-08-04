package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Obsidian 目录一键导入（四模块）结果。
 */
@Data
public class ObsidianImportResultVO {

    /** 源路径（解析后的绝对路径）。 */
    private String absolutePath;

    /** 新建/复用的知识库 ID。 */
    private Long categoryId;

    /** 新建/复用的知识库名称。 */
    private String categoryName;

    /** 导入的文档数（知识库模块）。 */
    private int docCount;

    /** 迁移的图片数。 */
    private int imageCount;

    /** 学习路径 ID（若生成）。 */
    private Long learningPathId;

    /** 学习路径章节数（若生成）。 */
    private int chapterCount;

    /** 生成的闪卡数（若生成）。 */
    private int flashcardCount;

    /** 生成的题库数（若生成）。 */
    private int quizCount;

    /** 实际生成的模块列表。 */
    private List<String> generatedModules = new ArrayList<>();

    /** 提示信息（如未配置 AI 时的降级说明）。 */
    private String message;
}
