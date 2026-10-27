package com.knowflow.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟面试会话（Phase 3H）。
 *
 * <p>基础模式（BASIC）是无 AI 的完整闭环：题目快照 + 手工录入 + 预置追问树 + 量表自评。
 * AI 动态追问模式（预留）不改变本表归属与快照语义。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_session")
public class MockInterviewSession extends BaseEntity {

    private Long userId;

    /** 方向编码：见 InterviewTracks。 */
    private String track;

    /** BASIC（预置题基础模式）；AI 模式预留。 */
    private String mode;

    /** IN_PROGRESS / FINISHED。 */
    private String status;

    private Integer questionCount;

    /** 题目快照 JSON（题干/参考要点/追问树）。 */
    private String questionsJson;

    /** 作答快照 JSON。 */
    private String answersJson;

    /** 自评均分 1-5。 */
    private BigDecimal avgSelfScore;

    /** 服务端按 started/finished 计算的实际用时。 */
    private Integer durationSec;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public static final String MODE_BASIC = "BASIC";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_FINISHED = "FINISHED";
}
