package com.knowflow.productivity.vo;

import com.knowflow.productivity.entity.FocusSession;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 承载专注时长及会话统计的响应数据。
 */
@Data
public class FocusStatsVO {

    private Integer todayMinutes;

    private Integer todayPomodoros;

    private Integer todaySessions;

    private Integer weekMinutes;

    private Double avgQuality;

    private Map<String, Integer> modeBreakdown;

    private List<Integer> hourlyHeatmap;

    private List<FocusSession> recentList;
}
