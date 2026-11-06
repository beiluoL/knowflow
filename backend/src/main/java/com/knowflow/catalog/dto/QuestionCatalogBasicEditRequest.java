package com.knowflow.catalog.dto;

import java.util.List;

/**
 * 基础编辑请求。
 *
 * <p>字段与重构前 `QuestionCatalogService.BasicEdit` 完全一致，JSON 键名不变，前端无需改动。 三个 `expected*`
 * 字段是并发控制的期望值：编辑令牌、来源版本号、更新时间，任一不匹配即 409。
 *
 * <p>作为请求 DTO 独立成类，避免传输结构寄生在 Service 内部类型上。
 */
public record QuestionCatalogBasicEditRequest(
    String title,
    Integer difficulty,
    String tags,
    Integer expectedSourceVersion,
    String expectedUpdatedAt,
    Long categoryId,
    String topicCode,
    List<Long> knowledgePointIds,
    String expectedEditToken) {}
