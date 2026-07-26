package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.SysNotification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统通知表数据访问层，基于 MyBatis-Plus 提供通知的增删改查。
 */
@Mapper
public interface SysNotificationMapper extends BaseMapper<SysNotification> {
}
