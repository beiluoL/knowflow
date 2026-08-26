package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划时段块 VO：对应早/午/晚三档（morning / afternoon / evening）。
 */
@Data
public class PlanBlockVO {

    /** 时段：morning / afternoon / evening。 */
    private String timeSlot;

    /** 时段展示名称，例如「上午 🌅」。 */
    private String label;

    /** 时段建议起始时间 HH:mm。 */
    private String startTime;

    /** 时段建议结束时间 HH:mm。 */
    private String endTime;

    /** 该时段下的执行项。 */
    private List<PlanItemVO> items = new ArrayList<>();
}
