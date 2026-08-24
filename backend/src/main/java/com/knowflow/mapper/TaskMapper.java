package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Task;
import com.knowflow.vo.CalendarEventVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务表数据访问层，基于 MyBatis-Plus 提供任务的增删改查。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 按时间区间查询日历事件：返回与 [start, end] 时间窗重叠的定时事件，
     * 以及 scheduled_date 落在该窗日期范围内的全天事件。绝不全表扫描。
     */
    @Select("""
        <script>
        SELECT t.id, t.list_id, t.parent_id, t.title, t.notes, t.status, t.someday,
               t.important, t.urgent, t.stage, t.scheduled_date, t.due_date,
               t.start_time, t.end_time,
               tl.name AS list_name, tl.color AS list_color
        FROM task t
        LEFT JOIN task_list tl ON t.list_id = tl.id
        WHERE t.deleted = 0
          AND t.user_id = #{userId}
          AND (
            (t.start_time IS NOT NULL
                AND t.start_time &lt; #{end}
                AND (t.end_time IS NULL OR t.end_time &gt; #{start}))
            OR
            (t.start_time IS NULL AND t.scheduled_date IS NOT NULL
                AND t.scheduled_date &gt;= #{startDate} AND t.scheduled_date &lt;= #{endDate})
          )
          <if test="status != null">
            AND t.status = #{status}
          </if>
          <if test="listId != null">
            AND t.list_id = #{listId}
          </if>
        ORDER BY t.start_time ASC, t.scheduled_date ASC, t.id ASC
        </script>
        """)
    List<CalendarEventVO> selectCalendarRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") Integer status,
            @Param("listId") Long listId);
}
