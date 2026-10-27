package com.knowflow.coding.vo;

import lombok.Data;

import java.util.List;

/** Phase 3G 算法题库 VO/DTO 集合（不对外暴露隐藏用例与参考解答，管理端视图单独标注 full=true）。 */
public final class CodingVos {

    private CodingVos() {
    }

    @Data
    public static class TopicVO {
        private String code;
        private String name;
        private String parentCode;
        private Integer level;
        private Integer sortOrder;
    }

    @Data
    public static class ProblemListItem {
        private Long id;
        private String title;
        private Integer difficulty;
        private String topicCode;
        private String topicName;
        private String tags;
        /** NONE / ATTEMPTED / SOLVED（当前用户） */
        private String myStatus;
        private Integer submissionCount;
    }

    @Data
    public static class TestCaseView {
        private Integer orderNo;
        private String input;
        private String expectedOutput;
        private Integer weight;
    }

    @Data
    public static class ProblemDetail {
        private Long id;
        private String title;
        private String content;
        private Integer difficulty;
        private String topicCode;
        private String topicName;
        private String tags;
        // code_problem 公开部分
        private String supportedLanguages;
        private String functionSignature;
        private String starterCode;
        private String constraints;
        private String examples;
        private Integer timeLimitMs;
        private Integer memoryLimitMb;
        private String judgeMode;
        private List<TestCaseView> publicCases;
        // 当前用户草稿
        private String draftCode;
        // 题解：仅本人已 ACCEPTED 后返回
        private String solutionExplanation;
        private String complexityAnalysis;
        private String followUps;
        private String commonMistakes;
        private boolean solved;
        // 判题服务状态
        private boolean judgeExecutionEnabled;
        private String judgeMode2;
    }

    @Data
    public static class CaseResultView {
        private boolean hidden;
        private Integer orderNo;
        private String input;
        private String expected;
        private String actual;
        private boolean passed;
        private Integer timeMs;
        private String status;
    }

    @Data
    public static class SubmissionVO {
        private Long id;
        private Long questionId;
        private String language;
        private String sourceCode;
        private String submissionType;
        private String judgeStatus;
        private Integer score;
        private Integer passedCount;
        private Integer totalCount;
        private Integer executionTimeMs;
        private String compileOutput;
        private List<CaseResultView> caseResults;
        private String createTime;
        private String contextType;
    }

    /** Phase 3H 两次提交代码对比（确定性行级 LCS，不依赖 AI）。 */
    @Data
    public static class SubmissionDiffVO {
        /** 对比基准（较早的提交）。 */
        private DiffRef from;
        /** 对比目标（较新的提交）。 */
        private DiffRef to;
        /** 有序差异行：EQUAL/ADDED/REMOVED。 */
        private List<DiffLine> lines;
        /** ADDED + REMOVED 行数（变化规模）。 */
        private int changedLines;
        /** 任一侧代码超过安全行数上限被截断时为 true。 */
        private boolean truncated;

        /** 提交元信息：只回传持久化的真实判题指标，AI/展示层不得臆造运行时间或内存。 */
        @Data
        public static class DiffRef {
            private Long id;
            private String judgeStatus;
            private Integer score;
            private Integer passedCount;
            private Integer totalCount;
            private Integer executionTimeMs;
            private Integer memoryUsedKb;
            private String createTime;
        }

        @Data
        public static class DiffLine {
            /** EQUAL / ADDED / REMOVED。 */
            private String type;
            /** from 侧行号（1 起）；ADDED 行为 null。 */
            private Integer oldLine;
            /** to 侧行号（1 起）；REMOVED 行为 null。 */
            private Integer newLine;
            private String text;

            public DiffLine() {
            }

            public DiffLine(String type, Integer oldLine, Integer newLine, String text) {
                this.type = type;
                this.oldLine = oldLine;
                this.newLine = newLine;
                this.text = text;
            }
        }
    }

    @Data
    public static class SubmitResult {
        private Long submissionId;
        private String judgeStatus;
        private Integer score;
        private Integer passedCount;
        private Integer totalCount;
        private String compileOutput;
        private List<CaseResultView> caseResults;
        private boolean executionEnabled;
        private String message;
    }

    @Data
    public static class DraftRequest {
        private String language;
        private String sourceCode;
    }

    @Data
    public static class SubmitRequest {
        private String language;
        private String sourceCode;
        /** RUN 公开用例 / SUBMIT 全部用例，默认 SUBMIT */
        private String type;
    }

    @Data
    public static class SaveCaseRequest {
        private String input;
        private String expectedOutput;
        private String visibility;
        private Integer weight;
        private Integer orderNo;
    }

    /** 管理端：CODE 题整体保存（题目 + 扩展 + 用例）。 */
    @Data
    public static class SaveProblemRequest {
        private Long id;
        private String title;
        private String content;
        private Integer difficulty;
        private String topicCode;
        private String tags;
        private String canonicalKey;
        private String supportedLanguages;
        private String functionSignature;
        private String starterCode;
        private String constraints;
        private String examples;
        private Integer timeLimitMs;
        private Integer memoryLimitMb;
        private String judgeMode;
        private String checker;
        private Double toleranceValue;
        private String referenceSolution;
        private String solutionExplanation;
        private String complexityAnalysis;
        private String followUps;
        private String commonMistakes;
        /** 保存后状态：0 草稿 / 1 直接发布（仍需 reviewStatus=APPROVED 才对用户可见） */
        private Integer status;
        private List<SaveCaseRequest> cases;
    }

    @Data
    public static class AdminProblemDetail {
        /** full=true：含隐藏用例与参考解答，仅 ADMIN */
        private boolean full = true;
        private Long id;
        private String title;
        private String content;
        private Integer difficulty;
        private String topicCode;
        private String tags;
        private String canonicalKey;
        private Integer status;
        private String reviewStatus;
        private Integer version;
        private Long creatorId;
        private String problemJson;
        private List<SaveCaseRequest> cases;
    }

    @Data
    public static class ReviewRequest {
        /** APPROVED / REJECTED */
        private String decision;
        private String comment;
    }

    /** Phase 3H：代码 AI 辅助请求（点评 / 解释 / 分级提示）。 */
    @Data
    public static class AiAssistRequest {
        /** 指定提交；为空时使用该题最近一次提交（含草稿代码仅作 AI 输入，不会触发判题）。 */
        private Long submissionId;
        /** review 代码点评（默认）/ explain 代码解释 / hint 分级提示。 */
        private String kind;
        /** hint 等级 1-5：1 理解题意 … 5 完整题解。 */
        private Integer hintLevel;
    }

    /**
     * Phase 3H：代码 AI 辅助结果。
     *
     * <p>mode=AI 时 content 为模型生成（前端必须标注“AI 建议”）；mode=BASIC 时 content 为预置引导，
     * 并携带编译/测试结果与（通过后可见的）标准题解。judgeStatus 始终原样回显最近提交，
     * AI 点评永远不会改变 Accepted / Wrong Answer 结论；本接口也绝不触发判题。
     */
    @Data
    public static class AiAssistVO {
        private Long questionId;
        private Long submissionId;
        private String kind;
        private Integer hintLevel;
        /** AI / BASIC（降级或关闭）。 */
        private String mode;
        /** BASIC 时的失败分类枚举名（AI_UNCONFIGURED / AI_TIMEOUT …），可空。 */
        private String failureKind;
        /** 主要文本（AI 生成或预置引导）。 */
        private String content;
        /** 以下字段为基础模式事实数据，两种模式都可能回显。 */
        private String judgeStatus;
        private Integer score;
        private Integer passedCount;
        private Integer totalCount;
        private String compileOutput;
        private Integer executionTimeMs;
        /** 标准题解/复杂度/常见错误：仅本人已 ACCEPTED 后返回。 */
        private String solutionExplanation;
        private String complexityAnalysis;
        private String commonMistakes;
        /** 判题服务是否启用（纯展示；禁用时不会被 AI 失败间接触发）。 */
        private boolean judgeExecutionEnabled;
    }
}
