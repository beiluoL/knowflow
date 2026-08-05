package com.knowflow.vo;

import lombok.Data;

/**
 * 工作台总览数据：四模块闭环的概览指标，驱动首页看板。
 */
@Data
public class WorkbenchOverviewVO {

    /** 收集箱总数 */
    private Long captureTotal;

    /** 待整理（INBOX）条目数 */
    private Long captureInbox;

    /** 已标星条目数 */
    private Long captureStarred;

    /** 康奈尔笔记总数 */
    private Long noteTotal;

    /** 待复习卡片数（含已逾期与今日到期） */
    private Long reviewDue;

    /** 累计复习次数 */
    private Long reviewCount;

    /** 记忆宫殿数 */
    private Long palaceTotal;

    /** 宫殿位点数 */
    private Long lociTotal;

    /** 费曼故事总数 */
    private Long storyTotal;

    /** 待分享（DRAFT/DONE 未发布）故事数 */
    private Long storyDraft;

    /** 近 7 天复习次数（遗忘曲线概览） */
    private Long reviewLast7d;
}
