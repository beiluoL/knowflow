package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.AgentCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模型调用日志数据访问层。
 * <p>
 * 除 BaseMapper 提供的基础 CRUD 外，额外定义聚合查询方法，
 * 供模型监测仪表盘按时间维度、按模型维度统计调用次数/平均响应时间/错误率。
 */
@Mapper
public interface AgentCallLogMapper extends BaseMapper<AgentCallLog> {

    /**
     * 按模型配置聚合统计：调用次数、成功次数、平均耗时、最大耗时、token 总量。
     * <p>
     * 仅统计指定用户在 startTime 之后的记录。
     *
     * @param userId    用户ID
     * @param startTime 统计起始时间
     * @return 每条记录对应一个模型的统计，字段：configId, provider, totalCalls, successCalls, avgLatency, maxLatency, totalTokens
     */
    @Select("SELECT config_id AS configId, provider, " +
            "       COUNT(*) AS totalCalls, " +
            "       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCalls, " +
            "       AVG(latency_ms) AS avgLatency, " +
            "       MAX(latency_ms) AS maxLatency, " +
            "       SUM(COALESCE(token_in, 0) + COALESCE(token_out, 0)) AS totalTokens " +
            "FROM agent_call_log " +
            "WHERE user_id = #{userId} AND create_time >= #{startTime} " +
            "GROUP BY config_id, provider " +
            "ORDER BY totalCalls DESC")
    List<Map<String, Object>> statByConfig(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /**
     * 按小时聚合统计：用于绘制响应时间趋势与调用次数趋势曲线。
     * <p>
     * 返回每小时一条记录，字段：hour (格式 yyyy-MM-dd HH:00), calls, successCalls, avgLatency, errorCalls
     *
     * @param userId    用户ID
     * @param startTime 统计起始时间
     * @return 每小时一条统计
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:00') AS hour, " +
            "       COUNT(*) AS calls, " +
            "       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCalls, " +
            "       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS errorCalls, " +
            "       AVG(latency_ms) AS avgLatency " +
            "FROM agent_call_log " +
            "WHERE user_id = #{userId} AND create_time >= #{startTime} " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d %H:00') " +
            "ORDER BY hour ASC")
    List<Map<String, Object>> statHourly(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /**
     * 汇总统计：总调用数、成功数、失败数、平均耗时、唯一模型数。
     */
    @Select("SELECT COUNT(*) AS totalCalls, " +
            "       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCalls, " +
            "       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS errorCalls, " +
            "       AVG(latency_ms) AS avgLatency, " +
            "       COUNT(DISTINCT config_id) AS modelCount " +
            "FROM agent_call_log " +
            "WHERE user_id = #{userId} AND create_time >= #{startTime}")
    Map<String, Object> summary(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);
}
