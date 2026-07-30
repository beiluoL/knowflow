package com.knowflow.vo;

import lombok.Data;

/**
 * 用户答题统计：累计作答、答对与正确率。
 */
@Data
public class QuizStatsVO {

    /** 累计作答题数 */
    private Integer total;

    /** 累计答对题数 */
    private Integer correct;

    /** 累计答错题数 */
    private Integer wrong;

    /** 正确率（0-100） */
    private Integer accuracy;
}
