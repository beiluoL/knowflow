package com.knowflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景要点（解析 learning_scenario.key_points JSON 的单个元素）。
 * <p>
 * 要点是「覆盖评分」的最小单元：作答命中该要点任一个关键词即视为覆盖该要点。
 * keywords 只存服务端，绝不返回前端。
 */
@Data
public class ScenarioKeyPoint {

    /** 要点名称，如「GC 日志」；提交后向用户展示是否覆盖 */
    private String label;

    /** 命中关键词集合，如 ["gc 日志","gclog","gc log"]；任一命中即覆盖 */
    private List<String> keywords = new ArrayList<>();
}
