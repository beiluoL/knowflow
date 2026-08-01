package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.WeeklyReport;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 学习周报 Mapper。
 */
@Mapper
public interface WeeklyReportMapper extends BaseMapper<WeeklyReport> {

    /**
     * 按用户与周一日期物理删除记录。
     * 因唯一索引 uk_report_user_week 包含 deleted 列，@TableLogic 逻辑删除会在重复
     * 生成同周记录时产生两行 deleted=1 的相同键值冲突，故重新生成场景改用物理删除。
     *
     * @param userId    用户ID
     * @param weekStart 周一日期
     * @return 受影响行数
     */
    @Delete("DELETE FROM weekly_report WHERE user_id = #{userId} AND week_start = #{weekStart}")
    int physicalDeleteByUserWeek(@Param("userId") Long userId, @Param("weekStart") java.time.LocalDate weekStart);
}
