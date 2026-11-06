package com.knowflow.catalog.service;

import com.knowflow.catalog.entity.QuestionCatalogAudit;
import com.knowflow.catalog.mapper.QuestionCatalogAuditMapper;
import com.knowflow.catalog.vo.QuestionCatalogAuditVO;
import com.knowflow.common.SecurityUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 题目变更审计。
 *
 * <p>与题目变更共用同一数据源与事务：审计写入失败必须让业务变更一并回滚， 因此不捕获异常、不做异步、不单独开事务。
 *
 * <p>只记录来源身份、动作与前后状态摘要，不记录答案正文、测试用例或用户代码。
 *
 * <p>持久化全部经由 {@link QuestionCatalogAuditMapper}，本类不再持有 JDBC 或 SQL。
 */
@Service
@RequiredArgsConstructor
public class QuestionMutationAudit {

  private final QuestionCatalogAuditMapper mapper;

  /**
   * 写入一条变更流水。
   *
   * @param source 来源类型，与目录身份前缀一致
   * @param id 来源表主键
   * @param action 动作名，如 EDIT_BASIC / PUBLISH / REVIEW
   * @param before 变更前状态摘要，创建时为 null
   * @param after 变更后状态摘要
   */
  public void record(String source, Long id, String action, String before, String after) {
    QuestionCatalogAudit row = new QuestionCatalogAudit();
    row.setActorId(SecurityUtils.getCurrentUserId());
    row.setSourceType(source);
    row.setSourceQuestionId(id);
    row.setAction(action);
    row.setBeforeState(before);
    row.setAfterState(after);
    mapper.insert(row);
  }

  /** 审计总条数。 */
  public long count() {
    return mapper.count();
  }

  /**
   * 按写入倒序读取一页审计，并转换为响应视图。
   *
   * @param size 每页条数（上层已校验 1–100）
   * @param offset 偏移量
   */
  public List<QuestionCatalogAuditVO> page(int size, long offset) {
    return mapper.page(size, offset).stream().map(QuestionMutationAudit::toVo).toList();
  }

  private static QuestionCatalogAuditVO toVo(QuestionCatalogAudit row) {
    return new QuestionCatalogAuditVO(
        row.getId() == null ? 0L : row.getId(),
        row.getActorId() == null ? 0L : row.getActorId(),
        row.getSourceType(),
        row.getSourceQuestionId() == null ? 0L : row.getSourceQuestionId(),
        row.getAction(),
        row.getBeforeState(),
        row.getAfterState(),
        row.getCreatedAt());
  }
}
