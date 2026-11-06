package com.knowflow.coding.vo;

import lombok.Data;

/** 提交结果及题目累计计数。 */
@Data
public class CodeSubmitResultVO {
    /** 上报的用例是否全部通过。 */
    private Boolean passed;
    /** 上报的用例总数。 */
    private Integer total;
    /** 上报的成功用例数。 */
    private Integer passCount;
    /** 累计提交次数。 */
    private Integer submitCount;
    /** 累计全部通过的提交次数。 */
    private Integer passTotal;
}
