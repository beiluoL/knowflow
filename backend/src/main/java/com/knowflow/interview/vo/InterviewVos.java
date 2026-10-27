package com.knowflow.interview.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Phase 3H 模拟面试基础模式 DTO/VO 集合。 */
public final class InterviewVos {

    private InterviewVos() {
    }

    @Data
    public static class StartRequest {
        /** 方向编码（白名单校验）。 */
        private String track;
        /** 题目数量 1-5，默认 3。 */
        private Integer questionCount;
        /** 可选：只能指定当前可访问方向的既有题目，不接收自造题干。 */
        private String questionId;
    }

    @Data
    public static class FollowupAnswer {
        /** 追问内容（取自会话快照的追问；服务端校验属于该题）。 */
        private String q;
        private String a;
    }

    @Data
    public static class AnswerRequest {
        /** 会话快照内的题目 id。 */
        private String questionId;
        /** 手工作答正文（语音识别不可用时的统一通道）。 */
        private String answer;
        /** 一层追问的手工作答（最多 5 条）。 */
        private List<FollowupAnswer> followups;
        /** 自评 1-5（标准评分量表）。 */
        private Integer selfScore;
        /** 自评备注/待改进点。 */
        private String selfComment;
    }

    @Data
    public static class TrackVO {
        private String code;
        private String name;
        private String description;
        private int questionPoolSize;
    }

    @Data
    public static class RubricVO {
        private String level1;
        private String level3;
        private String level5;
    }

    @Data
    public static class QuestionVO {
        private String id;
        private String title;
        private List<String> referencePoints;
        private List<String> followups;
        private RubricVO rubric;
    }

    @Data
    public static class FollowupAnswerVO {
        private String q;
        private String a;
    }

    @Data
    public static class AnswerVO {
        private String questionId;
        private String title;
        private String answer;
        private List<FollowupAnswerVO> followups;
        private Integer selfScore;
        private String selfComment;
    }

    @Data
    public static class SessionVO {
        private Long id;
        private String track;
        private String trackName;
        private String mode;
        /** 是否存在V2状态，用于基础模式历史也能恢复正确页面。 */
        private boolean v2;
        private String status;
        private Integer questionCount;
        private List<QuestionVO> questions;
        private List<AnswerVO> answers;
        private BigDecimal avgSelfScore;
        private Integer durationSec;
        private String startedAt;
        private String finishedAt;
        /** 已作答题目数（用于进度展示）。 */
        private int answeredCount;
    }

    @Data
    public static class SessionSummaryVO {
        private Long id;
        private String track;
        private String trackName;
        private String status;
        private Integer questionCount;
        private BigDecimal avgSelfScore;
        private Integer durationSec;
        private String startedAt;
        private String finishedAt;
    }
}
