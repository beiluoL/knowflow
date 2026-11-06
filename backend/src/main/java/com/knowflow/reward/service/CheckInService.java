package com.knowflow.reward.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.reward.entity.UserCheckIn;
import com.knowflow.reward.vo.CheckInResultVO;
import com.knowflow.reward.vo.CheckInStatusVO;

/**
 * 每日打卡业务契约，由 CheckInServiceImpl 实现；继承 IService 以复用 user_check_in 的通用 CRUD。
 *
 * <p>自然日按服务端本地时区的 LocalDate.now() 划分，打卡行以 (user_id, check_date) 唯一；Controller 的 userId 一律来自
 * SecurityUtils。
 *
 * <p>打卡展示奖励写在本表 reward_exp / reward_energy，钱包侧的 CHECK_IN 奖励由学习事件经 RewardEngine 独立入账，两者不抵扣。
 */
public interface CheckInService extends IService<UserCheckIn> {

  /**
   * 执行今日打卡；当日已有记录时返回 alreadyChecked=true 且奖励为 0，不重复插入、不重复发奖。
   *
   * <p>写副作用：新增打卡行、记录 CHECK_IN 学习事件（可能触发钱包奖励）、同步 sys_user.streak_days；实现层事务回滚不保证撤回独立事务里已提交的事件与奖励。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得
   * @return 本次打卡结果，含连续天数与展示奖励（经验 / 精力）
   */
  CheckInResultVO checkIn(Long userId);

  /**
   * 查询打卡状态：今日是否已打卡、连续天数、累计打卡天数与本月已打卡日期号。
   *
   * <p>只读无事务；未打卡但昨日有记录时仍展示昨日的连续天数，本月无记录返回空列表而不是 null。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得
   * @return 打卡状态 VO
   */
  CheckInStatusVO getStatus(Long userId);
}
