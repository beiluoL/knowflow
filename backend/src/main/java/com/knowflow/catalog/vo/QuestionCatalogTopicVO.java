package com.knowflow.catalog.vo;

/**
 * 筛选面板中的知识点条目。
 *
 * <p>字段名与重构前 `QuestionCatalogService.Topic` 一致，JSON 结构不变： `code` / `name` / `parentCode` /
 * `questionCount`。
 *
 * @param code 知识点编码，目录筛选的 topicCode 取值
 * @param name 展示名
 * @param parentCode 上级编码，顶级为 null
 * @param questionCount 该编码下的题目数量（来自题库视图聚合）
 */
public record QuestionCatalogTopicVO(
    String code, String name, String parentCode, long questionCount) {}
