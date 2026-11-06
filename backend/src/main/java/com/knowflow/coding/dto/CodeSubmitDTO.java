package com.knowflow.coding.dto;

import lombok.Data;

/** 浏览器上报的提交数据；服务端不执行代码。 */
@Data
public class CodeSubmitDTO {
    /** 提交的源代码文本。 */
    private String code;
    /** 编程语言标识。 */
    private String language;
    /** 已执行用例数；null 保持历史默认值 0。 */
    private Integer total;
    /** 成功用例数；null 保持历史默认值 0。 */
    private Integer passCount;
}
