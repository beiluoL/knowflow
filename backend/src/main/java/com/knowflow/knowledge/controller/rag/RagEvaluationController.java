package com.knowflow.knowledge.controller.rag;

import com.knowflow.common.Result;
import com.knowflow.knowledge.service.rag.RagEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员离线检索评测入口。 */
@RestController
@RequestMapping("/api/admin/rag/evaluation")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RagEvaluationController {
  private final RagEvaluationService evaluation;

  @GetMapping
  public Result<RagEvaluationService.Report> evaluate() {
    return Result.success(evaluation.evaluate());
  }
}
