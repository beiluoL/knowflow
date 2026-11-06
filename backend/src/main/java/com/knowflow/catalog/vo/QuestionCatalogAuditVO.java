package com.knowflow.catalog.vo;

import java.time.LocalDateTime;

/**
 * 审计流水的接口响应对象。
 *
 * <p>与实体分离：实体承载表结构，VO 只承载对外字段。字段名沿用重构前的 JSON 键， 前端无需改动。
 */
public record QuestionCatalogAuditVO(
    long id,
    long actorId,
    String sourceType,
    long sourceQuestionId,
    String action,
    String beforeState,
    String afterState,
    LocalDateTime createdAt) {}
