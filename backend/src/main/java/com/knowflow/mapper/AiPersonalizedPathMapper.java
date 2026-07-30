package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.AiPersonalizedPath;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AI 个性化学习路径缓存 Mapper。 */
@Mapper
public interface AiPersonalizedPathMapper extends BaseMapper<AiPersonalizedPath> {

    /**
     * 按缓存维度物理删除记录。
     * 因唯一索引包含 deleted 列，@TableLogic 逻辑删除会在重复删除同维度记录时产生
     * 两行 deleted=1 的相同键值冲突，故重新生成场景改用物理删除彻底清除旧缓存。
     *
     * @param userId       用户ID
     * @param goal         学习目标
     * @param level        当前水平
     * @param dailyMinutes 每日学习时长
     * @return 受影响行数
     */
    @Delete("DELETE FROM ai_personalized_path WHERE user_id = #{userId} AND goal = #{goal} "
            + "AND level = #{level} AND daily_minutes = #{dailyMinutes}")
    int physicalDeleteByKey(@Param("userId") Long userId, @Param("goal") String goal,
                            @Param("level") String level, @Param("dailyMinutes") Integer dailyMinutes);

    /**
     * 按主键与归属用户物理删除单条缓存（用户删除自己的历史推荐时使用，避免逻辑删除累积）。
     *
     * @param id     缓存记录ID
     * @param userId 归属用户ID（权限校验）
     * @return 受影响行数
     */
    @Delete("DELETE FROM ai_personalized_path WHERE id = #{id} AND user_id = #{userId}")
    int physicalDeleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
