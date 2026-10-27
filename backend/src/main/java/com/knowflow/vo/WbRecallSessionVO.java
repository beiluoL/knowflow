package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主动回忆会话 VO：返回会话详情及三轮比对结果。
 */
@Data
public class WbRecallSessionVO {

    private Long id;
    private Long noteId;
    private Long cardId;
    private String title;
    private String sourceText;

    /** 原文来源类型：PASTE / NOTE */
    private String sourceType;
    /** 来源展示文案（笔记标题或「粘贴原文」） */
    private String sourceLabel;
    /** 归档标记：0 未归档 / 1 已归档 */
    private Integer archived;

    private String round1Draft;
    private String round2Draft;
    private String round3Draft;
    /** 用户确认的关键遗漏（JSON：{"1":["…"],"2":["…"]}） */
    private String gapJson;

    private String round1Text;
    private Integer round1Score;

    private String round2Text;
    private Integer round2Score;

    private String round3Text;
    private Integer round3Score;

    private Integer currentRound;
    private String status;

    private LocalDateTime round3DueTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 三轮分数序列（用于前端折线图），null 表示该轮未提交 */
    private List<Integer> scoreTrend;

    /** 逐轮进步百分比（相对上一轮），null 表示无对比基准 */
    private List<Integer> improvementPct;

    /** 服务端当前时间，供前端校准第三轮倒计时 */
    private LocalDateTime serverNow;

    /** 第三轮是否已到期且可提交 */
    private Boolean round3Available;
}
