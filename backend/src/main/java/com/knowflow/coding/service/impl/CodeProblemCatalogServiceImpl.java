package com.knowflow.coding.service.impl;

import com.knowflow.coding.contract.CodeProblemCatalogPort;
import com.knowflow.coding.service.CodeProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 算法题目录契约实现：把 {@link CodeProblemService} 的管理端能力适配为最小契约。
 *
 * <p>只转发 {@code adminDetail} 与 {@code publish} 两个动作，不复制题库逻辑，也不暴露实体或 Mapper。
 */
@Service
@RequiredArgsConstructor
public class CodeProblemCatalogServiceImpl implements CodeProblemCatalogPort {

  private final CodeProblemService problems;

  @Override
  public Object adminDetail(Long questionId) {
    return problems.adminDetail(questionId);
  }

  @Override
  public void publish(Long questionId, boolean published) {
    problems.publish(questionId, published);
  }
}
