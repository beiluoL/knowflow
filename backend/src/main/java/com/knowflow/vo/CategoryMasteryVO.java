package com.knowflow.vo;

import lombok.Data;

/**
 * 知识维度掌握度 VO：按分类统计答题/阅读/闪卡的正确率，标记薄弱项。
 */
@Data
public class CategoryMasteryVO {

    /** 分类名称 */
    private String category;
    /** 总题数 */
    private int total;
    /** 正确数 */
    private int correct;
    /** 正确率（0-100） */
    private int rate;
    /** 是否薄弱（正确率 < 60%） */
    private boolean weak;
}
