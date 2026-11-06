package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.Achievement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成就模板 Mapper：achievement 表的 MyBatis-Plus 基础 CRUD，无自定义 SQL。
 *
 * <p>生产查询入口是 AchievementServiceImpl#getMyAchievements，按 status=1 取启用模板并以 sort_order、id 升序；模板由
 * data.sql 种子写入，当前没有管理端 CRUD 接口。
 *
 * <p>实体 Achievement 不继承 BaseEntity，deleted 也没有 {@code @TableLogic}：BaseMapper 查询不会自动追加
 * deleted=0；code 上的唯一索引 idx_ach_code 同样不含 deleted。
 */
@Mapper
public interface AchievementMapper extends BaseMapper<Achievement> {}
