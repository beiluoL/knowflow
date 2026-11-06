package com.knowflow.catalog.dto;

import java.util.List;

/**
 * 批量发布/下架请求。
 *
 * <p>字段与重构前 `QuestionCatalogController.BatchStatus` 一致，JSON 键名不变。 身份列表要求 1–50 条，且只接受 QUIZ 与
 * CODE_PROBLEM 两种来源——其余来源需走各自的专业编辑器。
 */
public record QuestionCatalogBatchStatusRequest(List<String> catalogIds, boolean published) {}
