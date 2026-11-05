package com.knowflow.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷快照头：一次模板实例化 / 智能组卷 / AI 出卷的结果。
 * 题目快照见 {@link ExamPaperQuestion}，与题库后续改动隔离。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_paper")
public class ExamPaper extends BaseEntity {

    private String name;
    private String domain;
    private String topics;

    /** 试卷来源：TEMPLATE（模板）/ SMART（智能）/ AI（模型生成）。 */
    private String source;
    private String templateKey;

    /** 策略：RANDOM（随机）/ FREQUENCY（频次）/ BALANCED（均衡）/ GRADIENT（梯度）/ MISTAKE（错题）/ WEAK（薄弱项）/ SCENARIO（场景）。 */
    private String strategy;

    private Long seedValue;
    private Integer durationMinutes;
    private Integer totalScore;
    private Integer passScore;

    /** 模式：PRACTICE（练习）/ EXAM（考试）。 */
    private String mode;

    private Integer shuffleQuestion;
    private Integer shuffleOption;
    private Integer allowPause;
    private Integer allowChange;

    /** 组卷输入完整快照 JSON */
    private String configJson;
    /** 题库版本/指纹 */
    private String bankVersion;
    /** AI 出卷元数据 JSON */
    private String aiMeta;
    /** 审核状态：APPROVED（已批准）/ PENDING_REVIEW（待审核）。 */
    private String reviewStatus;
    /** NULL 系统公共卷；AI 个人卷归属用户 */
    private Long ownerUserId;

    private Integer status;
}
