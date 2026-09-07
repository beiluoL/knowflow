package com.knowflow.dto;

import lombok.Data;

/**
 * 第一性原理推导提交入参（Architecture-4.1-§14~17）。
 * <p>
 * 六个环节全部由用户填写，服务端只做「结构完整度」判定（写够了没），
 * 不做任何语义正确性判断——系统没有可靠的语义参照系（§16/§33 禁止伪造评分）。
 */
@Data
public class FirstPrinciplesSubmitDTO {

    /** 推导主题（必填，2~200 字符，如「为什么 JVM 要分代回收」） */
    private String topic;

    /** 1 已知事实：可验证的事实，不含类比与结论 */
    private String facts;

    /** 2 假设：默认成立但未经验证的东西 */
    private String assumptions;

    /** 3 约束：绕不过去的硬边界 */
    private String constraints;

    /** 4 底层机制：拆到不能再拆的基本原理 */
    private String mechanism;

    /** 5 因果关系：机制之间如何推出结果 */
    private String causality;

    /** 6 重新推导：从基本原理重新推出原结论 */
    private String derivation;

    /** 作答耗时（秒，可选） */
    private Integer durationSeconds;
}
