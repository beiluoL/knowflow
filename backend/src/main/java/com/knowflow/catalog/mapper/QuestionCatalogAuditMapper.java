package com.knowflow.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.catalog.entity.QuestionCatalogAudit;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 题目目录审计表的持久化映射，只访问 Catalog 自有表 {@code question_catalog_audit}。
 *
 * <p>写入走 {@code BaseMapper#insert}：`created_at` 由表默认值 CURRENT_TIMESTAMP 生成， 与业务写入同一事务、同一时钟来源；实体为
 * null 的字段不会被拼进 INSERT。
 *
 * <p>读取只开放「总数 + 按主键倒序分页」两个方法，不暴露 update / delete， 审计记录一经写入不可修改。
 *
 * <p>查询列全部显式声明并用别名对齐实体字段，不依赖 `map-underscore-to-camel-case` 的全局配置，换环境后映射仍然确定。
 */
@Mapper
public interface QuestionCatalogAuditMapper extends BaseMapper<QuestionCatalogAudit> {

  /** 审计总条数。 */
  @Select("SELECT COUNT(*) FROM question_catalog_audit")
  long count();

  /**
   * 按写入倒序分页读取；`id DESC` 与插入顺序一致，保证分页稳定。
   *
   * @param size 每页条数，由上层校验 1–100
   * @param offset 偏移量
   */
  @Select(
      """
      SELECT id,
             actor_id AS actorId,
             source_type AS sourceType,
             source_question_id AS sourceQuestionId,
             action,
             before_state AS beforeState,
             after_state AS afterState,
             created_at AS createdAt
      FROM question_catalog_audit
      ORDER BY id DESC
      LIMIT #{size} OFFSET #{offset}
      """)
  List<QuestionCatalogAudit> page(@Param("size") int size, @Param("offset") long offset);
}
