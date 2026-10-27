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
        List<CodeTestCase> cases) {

    public List<CodeTestCase> effectiveCases() {
        if (submitAll) {
            return cases;
        }
        return cases.stream().filter(c -> CodeTestCase.VIS_PUBLIC.equals(c.getVisibility())).toList();
    }
}
