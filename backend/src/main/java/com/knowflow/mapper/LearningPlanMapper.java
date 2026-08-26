package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningPlan;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 学习计划 Mapper。
 * <p>
 * 由于 uk_lp_user_date(user_id, plan_date, deleted) 包含逻辑删除列，
 * force 重生成场景下必须先物理删除旧行，否则逻辑删除后重生成会命中唯一索引冲突。
 */
@Mapper
public interface LearningPlanMapper extends BaseMapper<LearningPlan> {

    /**
     * 按用户 + 日期范围物理删除计划（force=true 时使用）。
     *
     * @param userId    用户ID
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 受影响行数
     */
    @Delete("DELETE FROM learning_plan WHERE user_id = #{userId} AND plan_date >= #{startDate} AND plan_date <= #{endDate}")
    int physicalDeleteByDateRange(@Param("userId") Long userId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    /**
     * 按用户 + 单日物理删除计划。
     *
     * @param userId 用户ID
     * @param date   日期
     * @return 受影响行数
     */
    @Delete("DELETE FROM learning_plan WHERE user_id = #{userId} AND plan_date = #{date}")
    int physicalDeleteByDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}
