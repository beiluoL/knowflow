package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.UserCheckIn;
import com.knowflow.vo.CheckInResultVO;
import com.knowflow.vo.CheckInStatusVO;

/** 每日打卡业务服务接口。 */
public interface CheckInService extends IService<UserCheckIn> {

    /** 执行今日打卡（幂等：今日已打卡则返回已打卡状态，不重复计数）。 */
    CheckInResultVO checkIn(Long userId);

    /** 获取打卡状态：今日是否已打卡、连续天数、累计天数与本月打卡日历。 */
    CheckInStatusVO getStatus(Long userId);
}
