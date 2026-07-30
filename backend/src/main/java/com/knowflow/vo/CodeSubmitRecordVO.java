package com.knowflow.vo;

import lombok.Data;

/**
 * 代码提交记录 VO：供前端查看提交历史。
 */
@Data
public class CodeSubmitRecordVO {
    /** 提交记录 ID */
    private Long id;
    /** 题目 ID */
    private Long questionId;
    /** 提交的代码 */
    private String code;
    /** 编程语言 */
    private String language;
    /** 总测试用例数 */
    private Integer total;
    /** 通过用例数 */
    private Integer passCount;
    /** 是否完全通过 */
    private Boolean passed;
    /** 运行时错误信息 */
    private String errorMsg;
    /** 提交时间 */
    private String createTime;
}
