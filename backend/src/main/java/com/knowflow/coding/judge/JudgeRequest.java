package com.knowflow.coding.judge;

import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeTestCase;

import java.util.List;

/** 一次判题请求（不可变）。RUN 只跑公开用例；SUBMIT 跑全部用例。 */
public record JudgeRequest(
        Long userId,
        Long questionId,
        String language,
        String sourceCode,
        boolean submitAll,
        CodeProblem problem,
        List<CodeTestCase> cases,
        String requestId) {

    /** 兼容原考试入口；工作台显式传入冻结任务编号供Runner去重。 */
    public JudgeRequest(Long userId, Long questionId, String language, String sourceCode, boolean submitAll,
                        CodeProblem problem, List<CodeTestCase> cases) {
        this(userId, questionId, language, sourceCode, submitAll, problem, cases, null);
    }

    public List<CodeTestCase> effectiveCases() {
        if (submitAll) {
            return cases;
        }
        return cases.stream().filter(c -> CodeTestCase.VIS_PUBLIC.equals(c.getVisibility())).toList();
    }
}
