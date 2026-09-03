package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 费曼评分维度（Accuracy/Clarity/Completeness/Structure，Architecture-4.0-7）。
 */
@Data
public class FeynmanDimensionVO {

    /** 维度 key：ACCURACY / CLARITY / COMPLETENESS / STRUCTURE */
    private String key;

    /** 维度中文名，如「准确性」 */
    private String name;

    /** 该维自检准则清单（固定 4 条，评分 = 勾选数 / 4） */
    private List<FeynmanCriterionVO> items;
}
