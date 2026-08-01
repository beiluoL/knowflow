package com.knowflow.vo;

import com.knowflow.entity.FocusSession;
import lombok.Data;

import java.util.List;
import java.util.Map;

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
