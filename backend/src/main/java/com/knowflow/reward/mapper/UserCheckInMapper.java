package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.UserCheckIn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日打卡记录 Mapper：user_check_in 表的基础 CRUD，无自定义 SQL。
 *
 * <p>CheckInServiceImpl 继承 ServiceImpl 使用本接口，靠 save / getOne / count / list
 * 完成当日幂等判定、连续天数推导、累计天数与本月的日历查询；查询都显式带 userId 条件。
 *
 * <p>唯一索引 uk_uci_user_date(user_id, check_date) 不含 deleted；实体继承 BaseEntity，deleted 由
 * {@code @TableLogic} 自动过滤，软删行仍会占用唯一键，当前没有复活软删行的写入逻辑。并发同日打卡没有行锁，也未捕获 DuplicateKeyException。
 */
@Mapper
public interface UserCheckInMapper extends BaseMapper<UserCheckIn> {}
