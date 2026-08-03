package com.knowflow.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 编程 Agent 模型监测仪表盘统计数据 VO。
 * <p>
 * 包含三层统计：
 * <ol>
 *   <li>{@link #summary}：总体汇总（总调用数、成功数、错误数、平均耗时、模型数）</li>
 *   <li>{@link #byModel}：按模型分组统计（每个模型的调用数、成功率、平均耗时、token）</li>
 *   <li>{@link #hourly}：按小时分组统计（用于绘制趋势曲线）</li>
 * </ol>
 */
@Data
public class AgentStatsVO {
    /** 汇总统计：totalCalls, successCalls, errorCalls, avgLatency, modelCount */
    private Map<String, Object> summary;
    /** 按模型分组：configId, provider, totalCalls, successCalls, avgLatency, maxLatency, totalTokens */
    private List<Map<String, Object>> byModel;
    /** 按小时分组：hour, calls, successCalls, errorCalls, avgLatency */
    private List<Map<String, Object>> hourly;
    /** 统计时间范围（小时数），如 24 表示最近24小时 */
    private Integer rangeHours;
}
