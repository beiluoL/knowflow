package com.knowflow.vo;

import lombok.Data;

/**
 * 费曼单维评分结果（Architecture-4.0-7）。
 */
@Data
public class FeynmanDimensionResultVO {

    /** 维度 key：ACCURACY / CLARITY / COMPLETENESS / STRUCTURE */
    private String key;

    /** 维度中文名 */
    private String name;

    /** 勾选准则数 */
    private int checked;

    /** 该维总准则数（恒 4） */
    private int total;

    /** 该维得分 0-100（round(checked/total*100)） */
    private int score;
}
