package com.knowflow.exam.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 考试系统 VO 集合。 */
public final class ExamVos {

    private ExamVos() {
    }

    @Data
    public static class TemplateVO {
        private Long id;
        private String templateKey;
        private String name;
        private String description;
        private String domain;
        private List<String> topics;
        private Integer durationMinutes;
        private Integer totalScore;
        private Integer passScore;
        private String mode;
        private String version;
        private Map<String, Integer> typeDistribution;
        private String multiPolicy;
        /** 题库是否足以生成该模板 */
        private Boolean available;
        /** 不足时的说明 */
        private String shortage;
    }

    @Data
    public static class PaperVO {
        private Long id;
        private String name;
        private String domain;
        private List<String> topics;
        private String source;
        private String templateKey;
        private String strategy;
        private Long seedValue;
        private Integer durationMinutes;
        private Integer totalScore;
        private Integer passScore;
        private String mode;
        private Integer shuffleQuestion;
        private Integer shuffleOption;
        private Integer allowPause;
        private Integer allowChange;
        private String reviewStatus;
        private Long ownerUserId;
        private Integer questionCount;
        private String createdAt;
        /** AI 出卷元数据（脱敏，仅展示模型/时间） */
        private Map<String, Object> aiInfo;
        /** Phase 3H：AI 出卷失败/质量不足时是否自动降级为题库规则组卷。 */
        private Boolean aiFallback;
        /** 降级原因（AI 失败分类或“候选题校验不足”），用于前端明确标注，非 null 仅当 aiFallback=true。 */
        private String fallbackReason;
    }

    /** 答题页题目（不含答案与解析）。 */
    @Data
    public static class TakeQuestionVO {
        private Long paperQuestionId;
        private Integer ordinal;
        private String title;
        private String content;
        private String questionType;
        private List<String> options;
        private Integer score;
        private Integer difficulty;
        private String topic;
        /** Phase 3G：CODE 题的公开作答信息（starterCode/签名/公开用例等），快照生成时已剔除参考解与隐藏用例。 */
        private Map<String, Object> codeProblem;
    }

    @Data
    public static class AttemptVO {
        private Long attemptId;
        private Long paperId;
        private String paperName;
        private String mode;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime serverDeadline;
        /** 服务器当前时间，前端倒计时以此校准 */
        private LocalDateTime serverNow;
        private Integer durationMinutes;
        private Integer totalScore;
        private Integer passScore;
        private Integer questionCount;
        private String gradingStatus;
        private Long draftRevision;
        private Map<Long, String> draft;
        private List<Integer> marked;
        private List<TakeQuestionVO> questions;
        private Boolean deadlineExpired;
    }

    @Data
    public static class AttemptListItemVO {
        private Long attemptId;
        private Long paperId;
        private String paperName;
        private String mode;
        private String status;
        private String gradingStatus;
        private Integer totalScore;
        /** 已评分部分满分；PENDING_AI 时不等于整卷满分 */
        private Integer maxScore;
        private Integer paperMaxScore;
        private Integer correctCount;
        private Integer gradedCount;
        private Integer questionCount;
        private LocalDateTime startTime;
        private LocalDateTime submitTime;
        private Integer durationSeconds;
        private Integer passScore;
        private Boolean passed;
    }

    @Data
    public static class RubricDimension {
        private String key;
        private String name;
        private Integer weight;
        private Integer score;
        private Integer maxScore;
    }

    @Data
    public static class ResultItemVO {
        private Long paperQuestionId;
        private Integer ordinal;
        private String topic;
        private String title;
        private String content;
        private String questionType;
        private List<String> options;
        private Integer score;
        private Integer maxScore;
        private Integer difficulty;
        private String userAnswer;
        /** 机器可读标准答案：选择题为索引（"1" / "0,2"），判断题为 true/false，填空为主观文本 */
        private String correctAnswer;
        /** 面向展示的标准答案文本（选项文本 / 正确 / 错误） */
        private String correctAnswerText;
        private String explanation;
        private Boolean correct;
        private String gradeType;
        private Boolean graded;
        // AI 评分证据
        private List<RubricDimension> rubric;
        private List<String> hitPoints;
        private List<String> missingPoints;
        private List<String> errors;
        private List<String> suggestions;
        private String aiModel;
        private Integer confidence;
        private String aiGradeNote;
    }

    @Data
    public static class GroupAnalysis {
        private String key;
        private String name;
        private Integer count;
        private Integer gradedCount;
        private Integer score;
        private Integer maxScore;
        private Integer correctCount;
        /** 0-100，样本不足为 null（证据不足，不虚构掌握度） */
        private Integer rate;
    }

    @Data
    public static class ResultVO {
        private Long attemptId;
        private Long paperId;
        private String paperName;
        private String mode;
        private String status;
        private String gradingStatus;
        private Integer totalScore;
        private Integer maxScore;
        private Integer paperMaxScore;
        private Integer correctCount;
        private Integer gradedCount;
        private Integer questionCount;
        private Boolean passed;
        private Integer passScore;
        private LocalDateTime submitTime;
        private Integer durationSeconds;
        private Boolean deadlineExpired;
        private Boolean aiAvailable;
        private String aiMessage;
        private List<ResultItemVO> items;
        private List<GroupAnalysis> typeAnalysis;
        private List<GroupAnalysis> topicAnalysis;
    }

    @Data
    public static class BankAvailabilityVO {
        private Integer total;
        private Map<String, Integer> byType;
        private Map<String, Integer> byTopic;
        /** 给定条件下的可抽取数量与缺口（按题型） */
        private Map<String, Integer> requested;
        private Map<String, Integer> available;
        private Boolean sufficient;
        private String message;
    }

    @Data
    public static class ExamMistakeVO {
        private Long id;
        private String question;
        private String wrongAnswer;
        private String correctAnswer;
        private String topic;
        private Integer difficulty;
        private Integer reviewCount;
        private Boolean mastered;
        private LocalDateTime lastReviewTime;
        private LocalDateTime createTime;
        private Long questionId;
    }

    @Data
    public static class DashboardVO {
        private Integer attemptCount;
        private Integer gradedPaperCount;
        private Integer avgRate;
        private Integer mistakeCount;
        private Integer pendingMistakeCount;
        private List<AttemptListItemVO> recentAttempts;
        private List<ExamMistakeVO> recentMistakes;
        /** 基于真实答题记录的薄弱知识点（样本不足返回空，不伪造） */
        private List<GroupAnalysis> weakTopics;
        private List<TemplateVO> templates;
    }

    @Data
    public static class AiGradeResultVO {
        private Long attemptId;
        private String status;
        private String gradingStatus;
        private Integer gradedAi;
        private Integer pending;
        private Boolean aiAvailable;
        private String message;
    }
}
