package com.knowflow.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 题目目录变更审计记录，对应 Catalog 自有表 {@code question_catalog_audit}。
 *
 * <p>该表只有 `created_at`，没有 `create_time` / `update_time` / `deleted` 三列， 也没有逻辑删除语义，因此**不能**继承项目通用
 * BaseEntity——继承会引入表中不存在的列， 导致 MyBatis-Plus 生成错误 SQL。字段与列全部在此显式声明。
 *
 * <p>审计内容只保存「谁、对哪个来源的哪个题、做了什么、前后状态摘要」， 不保存答案正文、测试用例或用户代码，避免审计表成为敏感数据副本。
 */
@Data
@TableName("question_catalog_audit")
public class QuestionCatalogAudit {

  /** 自增主键；显式声明 AUTO，避免 MP 回填时误用雪花 ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /** 操作者用户 ID，来自登录态，不接受调用方传入。 */
  private Long actorId;

  /** 来源类型：QUIZ / SCENARIO / LEGACY_CODE / CODE_PROBLEM。 */
  private String sourceType;

  /** 来源表主键（不是目录主键）。 */
  private Long sourceQuestionId;

  /** 动作名：EDIT_BASIC / PUBLISH / UNPUBLISH / REVIEW 等。 */
  private String action;

  /** 变更前状态摘要，创建类动作为 null。 */
  private String beforeState;

  /** 变更后状态摘要。 */
  private String afterState;

  /** 落库时间，由数据库 CURRENT_TIMESTAMP 生成。 */
  private LocalDateTime createdAt;
}
