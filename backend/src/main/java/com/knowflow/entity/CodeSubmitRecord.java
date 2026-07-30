package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 代码提交记录实体：每次判题结果持久化，支持提交历史查询。
 */
@Data
@TableName("code_submit_record")
public class CodeSubmitRecord {

    /** 主键 ID */
    private Long id;

    /** 用户 ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 题目 ID（逻辑外键 code_question.id） */
    private Long questionId;

    /** 提交的代码文本 */
    private String code;

    /** 编程语言（如 javascript、python） */
    private String language;

    /** 总测试用例数 */
    private Integer total;

    /** 通过测试用例数 */
    private Integer passCount;

    /** 是否完全通过：1=通过，0=未通过 */
    private Integer passed;

    /** 运行时错误信息 */
    private String errorMsg;

    /** 提交时间 */
    private java.time.LocalDateTime createTime;

    /** 逻辑删除标志：0=正常，1=已删除 */
    private Integer deleted;
}
