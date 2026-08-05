package com.knowflow.vo;

import com.knowflow.entity.WbReviewCard;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 复习卡片返回 VO：在实体基础上补充人类可读的调度信息（下次复习文案、难度系数小数）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WbReviewCardVO extends WbReviewCard {

    /** SM-2 难度系数（小数形式，easeFactor/100） */
    private Double easeFactorDecimal;

    /** 距离下次复习的描述，如「今天」「3 天后」「已逾期 2 天」 */
    private String nextReviewHint;
}
