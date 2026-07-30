package com.knowflow.dto;

import lombok.Data;

/**
 * 编程挑战关卡提交入参：前端在浏览器端执行测试用例后上报结果，
 * 后端负责判定通关、计算星级与积分并处理关卡解锁（与代码题库判题模式一致）。
 */
@Data
public class ChallengeSubmitDTO {

    /** 提交的代码内容（保存为该关卡最近一次代码） */
    private String code;

    /** 测试用例总数 */
    private Integer total;

    /** 通过的用例数 */
    private Integer passCount;

    /** 本次做题耗时（秒），可选，仅用于展示 */
    private Integer durationSeconds;
}
