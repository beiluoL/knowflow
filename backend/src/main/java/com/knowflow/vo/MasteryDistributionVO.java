package com.knowflow.vo;

import lombok.Data;

/**
 * 掌握分布看板视图对象，汇总闪卡难度分布、待复习/已复习情况与错题掌握情况。
 */
@Data
public class MasteryDistributionVO {

    /** 闪卡总数。 */
    private Integer flashcardTotal;

    /** 简单难度闪卡数。 */
    private Integer flashcardDiffEasy;

    /** 中等难度闪卡数。 */
    private Integer flashcardDiffMedium;

    /** 困难难度闪卡数。 */
    private Integer flashcardDiffHard;

    /** 当前待复习闪卡数（未排期或已过复习时间）。 */
    private Integer flashcardDue;

    /** 已复习过（reviewCount>0）的闪卡数。 */
    private Integer flashcardReviewed;

    /** 已掌握错题数。 */
    private Integer mistakeMastered;

    /** 未掌握错题数。 */
    private Integer mistakePending;
}
