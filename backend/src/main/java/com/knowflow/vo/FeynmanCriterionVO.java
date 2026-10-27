package com.knowflow.vo;

import lombok.Data;

/**
 * 费曼自检准则（评分最小单元，Architecture-4.0-7）。
 */
@Data
public class FeynmanCriterionVO {

    /** 准则稳定 id，如 accuracy-1；提交时以其为勾选依据 */
    private String id;

    /** 准则描述（作者可逐条核验的「可观察」自检项） */
    private String text;
}
