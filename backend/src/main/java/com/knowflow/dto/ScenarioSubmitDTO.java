package com.knowflow.dto;

import lombok.Data;

/**
 * 场景练习提交请求（Architecture-4.0-4）。
 */
@Data
public class ScenarioSubmitDTO {

    /** 用户作答（开放式答案，必填，服务端做确定性关键词覆盖评分） */
    private String answer;

    /** 作答耗时秒数（可选，仅作学习行为元数据记录） */
    private Integer durationSeconds;
}
