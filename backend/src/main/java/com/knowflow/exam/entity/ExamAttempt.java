package com.knowflow.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 考试尝试：状态机 + 服务端可信截止时间。
 *
 * <p>状态迁移（仅允许合法迁移）：
 * IN_PROGRESS → GRADING（交卷，含待 AI 评分）→ GRADED
 *             → GRADED（交卷且全部客观题已判完）
 * IN_PROGRESS → CANCELLED（用户取消，未交卷）
 * 评分失败：GRADING → GRADING_FAILED（可重试 AI 评分后回到 GRADED）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_attempt")
public class ExamAttempt extends BaseEntity {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_GRADING = "GRADING";
    public static final String STATUS_GRADED = "GRADED";
    public static final String STATUS_GRADING_FAILED = "GRADING_FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 全部为客观题，规则判分完成 */
    public static final String GRADING_NONE = "NONE";
    /** 存在待 AI 评分的主观题 */
    public static final String GRADING_PENDING_AI = "PENDING_AI";
    /** AI 评分完成 */
    public static final String GRADING_AI_GRADED = "AI_GRADED";
    /** AI 评分失败（可重试），未评分题不按 0 分计 */
    public static final String GRADING_AI_FAILED = "AI_FAILED";
    /** AI 评卷进行中（条件认领态，防止并发重复评卷/重复错题与事件） */
    public static final String GRADING_AI_RUNNING = "AI_RUNNING";
    /** 需要人工复核 */
    public static final String GRADING_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    /** Phase 3G：存在等待代码判题网关出分的 CODE 题（score=NULL，不计 0，可重试判题） */
    public static final String GRADING_PENDING_JUDGE = "PENDING_JUDGE";

    private Long paperId;
    private Long userId;
    private String mode;
    private String status;

    private LocalDateTime startTime;
    /** 服务端截止时间 = startTime + durationMinutes */
    private LocalDateTime serverDeadline;
    private LocalDateTime submitTime;
    private Integer durationSeconds;

    /** 草稿 JSON：{ "paperQuestionId": "userAnswer" } */
    private String draft;
    /** 标记待检查题号 JSON：[ordinal...] */
    private String marked;

    private Integer totalScore;
    /** 已评分题满分合计（未评分题不纳入） */
    private Integer maxScore;
    private Integer paperMaxScore;
    private Integer correctCount;
    private Integer gradedCount;
    private Integer questionCount;

    /** NONE / PENDING_AI / AI_GRADED / AI_FAILED / REVIEW_REQUIRED */
    private String gradingStatus;
    private String aiMeta;
    private String clientVersion;
}
