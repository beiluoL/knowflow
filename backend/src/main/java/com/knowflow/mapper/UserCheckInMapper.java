package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.UserCheckIn;
import org.apache.ibatis.annotations.Mapper;

/** 每日打卡记录表数据访问层。 */
@Mapper
public interface UserCheckInMapper extends BaseMapper<UserCheckIn> {
}
