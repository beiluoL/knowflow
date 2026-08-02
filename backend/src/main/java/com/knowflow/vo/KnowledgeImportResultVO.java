package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库目录批量导入结果。
 * <p>返回导入总数、成功/跳过/失败计数，以及逐条明细日志（含错误原因）与新创建的分类列表。
 */
@Data
public class KnowledgeImportResultVO {

    /** 目标知识库ID。 */
    private Long targetCategoryId;

    /** 解析到的文档总数（.md/.markdown/.txt）。 */
    private int totalDocs;

    /** 成功导入数。 */
    private int successCount;

    /** 增量去重跳过数。 */
    private int skippedCount;

    /** 失败数。 */
    private int failedCount;

    /** 迁移的图片数量。 */
    private int imageCount;

    /** 新创建的子分类列表（名称，便于前端提示用户结构变化）。 */
    private List<String> createdCategories = new ArrayList<>();

    /** 逐条明细：成功项。 */
    private List<ItemLog> successItems = new ArrayList<>();

    /** 逐条明细：跳过项（增量去重命中）。 */
    private List<ItemLog> skippedItems = new ArrayList<>();

    /** 逐条明细：失败项（含错误原因）。 */
    private List<ItemLog> failedItems = new ArrayList<>();

    @Data
    public static class ItemLog {
        /** 相对路径（如 Notes/AI/ML.md）。 */
        private String path;
        /** 文档标题。 */
        private String title;
        /** 归属分类ID。 */
        private Long categoryId;
        /** 归属分类名称（含层级）。 */
        private String categoryName;
        /** 说明（跳过原因 / 失败原因 / 成功提示）。 */
        private String message;

        public static ItemLog of(String path, String title, Long categoryId, String categoryName, String message) {
            ItemLog log = new ItemLog();
            log.setPath(path);
            log.setTitle(title);
            log.setCategoryId(categoryId);
            log.setCategoryName(categoryName);
            log.setMessage(message);
            return log;
        }
    }
}
