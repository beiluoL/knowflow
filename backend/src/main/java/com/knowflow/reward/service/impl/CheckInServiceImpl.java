package com.knowflow.reward.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.identity.contract.RewardMemberAccess;
import com.knowflow.learning.contract.LearningEventRecorder;
import com.knowflow.learning.contract.LearningEventType;
import com.knowflow.reward.entity.UserCheckIn;
import com.knowflow.reward.mapper.UserCheckInMapper;
import com.knowflow.reward.service.CheckInService;
import com.knowflow.reward.vo.CheckInResultVO;
import com.knowflow.reward.vo.CheckInStatusVO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每日打卡实现：按服务端自然日记录一行 user_check_in，计算连续天数、展示奖励并同步 sys_user.streak_days。
 *
 * <p>两条奖励口径互不抵扣：本表 rewardExp = 基础 10 + 连续天数里程碑加成、rewardEnergy 固定 5，只用于打卡结果展示；钱包侧由 CHECK_IN 学习事件经
 * RewardRule（10 XP / 2 Coin，每日 1 次上限）单独入账。
 *
 * <p>自然日与逻辑删除：check_date 由 LocalDate.now() 取服务端本地时区；实体继承 BaseEntity，deleted 由 @TableLogic
 * 自动过滤，但唯一索引 uk_uci_user_date(user_id, check_date) 不含 deleted，软删行仍占位。
 */
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl extends ServiceImpl<UserCheckInMapper, UserCheckIn>
    implements CheckInService {

  private final RewardMemberAccess sysUserMapper;
  private final LearningEventRecorder learningEventService;

  /** 每日基础奖励经验值，写入 user_check_in.reward_exp，仅作展示，不进入 wallet.xp。 */
  private static final int BASE_EXP = 10;

  /** 每日基础奖励精力值，固定 5，不随连续天数加成；当前没有对应的精力余额表。 */
  private static final int BASE_ENERGY = 5;

  /**
   * 执行今日打卡；当日已有记录时直接返回既有状态，不重复计数、不重复发奖。
   *
   * <p>执行顺序：查当日记录做幂等判定 → 查昨日记录推导连续天数 → 计算展示奖励并插入打卡行 → 记录 CHECK_IN 学习事件 → 同步 sys_user.streak_days。
   *
   * <p>事务与并发：rollbackFor = Exception.class，打卡行、事件记录调用与 streak
   * 同步在调用方事务内；并发幂等只能靠「先查后插」与唯一键，本方法没有行锁，两个并发请求同时判定「今日未打卡」时后插入者会撞唯一键，且未捕获 DuplicateKeyException。
   *
   * <p>跨事务边界：LearningEventRecorder#record 是 REQUIRES_NEW 独立事务并自行吞掉异常，其内部的 RewardEngine
   * 发奖也独立提交；因此打卡事务后续失败回滚，不会撤回已提交的学习事件与钱包奖励。
   *
   * <p>连续天数：昨日有记录则 +1，否则重置为 1；里程碑奖励按 7/14/30/100 精确匹配，使当日 rewardExp 分别为 100/200/500/1000，超过 100
   * 天回落到基础 10。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得
   * @return 打卡结果；alreadyChecked=true 表示今日此前已打过卡，此时 rewardExp / rewardEnergy 均为 0
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public CheckInResultVO checkIn(Long userId) {
    LocalDate today = LocalDate.now();
    CheckInResultVO vo = new CheckInResultVO();

    // 阶段一：幂等判定。今日记录存在就直接返回，避免二次插入与二次发奖。
    UserCheckIn todayRecord = getByDate(userId, today);
    if (todayRecord != null) {
      // 幂等：今日已打卡，返回既有状态，不重复计数与发奖
      vo.setCheckedToday(true);
      vo.setAlreadyChecked(true);
      vo.setContinuousDays(todayRecord.getContinuousDays());
      vo.setRewardExp(0);
      vo.setRewardEnergy(0);
      return vo;
    }

    // 阶段二：连续天数只由昨日记录决定，中间断签即重置为 1，不回补历史。
    // 连续天数：昨日已打卡则续接，否则从 1 重新开始
    UserCheckIn yesterdayRecord = getByDate(userId, today.minusDays(1));
    int continuous =
        (yesterdayRecord != null && yesterdayRecord.getContinuousDays() != null)
            ? yesterdayRecord.getContinuousDays() + 1
            : 1;

    int rewardExp = BASE_EXP + milestoneBonus(continuous);

    // 阶段三：先落打卡行再记录事件，事件携带 continuousDays / rewardExp / rewardEnergy 供下游消费。
    UserCheckIn record = new UserCheckIn();
    record.setUserId(userId);
    record.setCheckDate(today);
    record.setContinuousDays(continuous);
    record.setRewardExp(rewardExp);
    record.setRewardEnergy(BASE_ENERGY);
    this.save(record);

    // Learning Event System（Phase 1）：每日签到事件（仅真实新增签到记录时触发）
    learningEventService.record(
        userId,
        LearningEventType.CHECK_IN,
        "CHECK_IN",
        null,
        Map.of("continuousDays", continuous, "rewardExp", rewardExp, "rewardEnergy", BASE_ENERGY));

    // 同步 sys_user.streak_days（用户统计以该字段展示连续学习天数）
    sysUserMapper.syncStreak(userId, continuous);

    vo.setCheckedToday(true);
    vo.setAlreadyChecked(false);
    vo.setContinuousDays(continuous);
    vo.setRewardExp(rewardExp);
    vo.setRewardEnergy(BASE_ENERGY);
    return vo;
  }

  /**
   * 查询打卡状态：今日是否已打卡、连续天数、累计天数与本月打卡日历，只读无事务。
   *
   * <p>执行顺序：查当日记录 → 未打卡则回看昨日以保留 streak 展示 → 统计累计行数 → 取当月记录并生成日期号列表。
   *
   * <p>口径：今日已打卡取今日 continuousDays；未打卡但昨日有记录仍显示昨日值；两者都无则为 0。totalDays 是 user_check_in
   * 的行数（@TableLogic 自动过滤软删行），monthCheckedDays 是当月日期号去重升序列表，空月返回空列表而不是 null。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得
   * @return 打卡状态 VO；自然月边界与 today 一样使用服务端本地时区
   */
  @Override
  public CheckInStatusVO getStatus(Long userId) {
    LocalDate today = LocalDate.now();
    CheckInStatusVO vo = new CheckInStatusVO();

    // 阶段一：今日状态与连续天数。
    UserCheckIn todayRecord = getByDate(userId, today);
    boolean checkedToday = todayRecord != null;
    vo.setCheckedToday(checkedToday);

    // 连续天数：今日已打卡取今日记录；否则若昨日打卡则 streak 仍存活，取昨日记录；再否则为 0
    if (checkedToday) {
      vo.setContinuousDays(todayRecord.getContinuousDays());
    } else {
      UserCheckIn yesterdayRecord = getByDate(userId, today.minusDays(1));
      vo.setContinuousDays(
          yesterdayRecord != null && yesterdayRecord.getContinuousDays() != null
              ? yesterdayRecord.getContinuousDays()
              : 0);
    }

    // 阶段二：累计天数按有效打卡行计数，不含软删行。
    long total =
        this.count(new LambdaQueryWrapper<UserCheckIn>().eq(UserCheckIn::getUserId, userId));
    vo.setTotalDays((int) total);

    // 本月打卡日历：取当月已打卡日期号
    // 阶段三：月历区间为当月 1 日至月末，按日期号去重升序，供前端日历直接渲染。
    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
    List<UserCheckIn> monthRecords =
        this.list(
            new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .ge(UserCheckIn::getCheckDate, monthStart)
                .le(UserCheckIn::getCheckDate, monthEnd));
    List<Integer> days =
        monthRecords.stream()
            .map(r -> r.getCheckDate().getDayOfMonth())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    vo.setMonthCheckedDays(days.isEmpty() ? new ArrayList<>() : days);
    return vo;
  }

  /**
   * 连续天数里程碑加成：7/14/30/100 天分别额外 90/190/490/990，与基础 10 相加后当日展示奖励为 100/200/500/1000。
   *
   * <p>精确匹配而非区间判断，第 8 天或第 101 天都没有加成；本函数不改体力奖励。
   */
  private int milestoneBonus(int continuous) {
    return switch (continuous) {
      case 7 -> 90;
      case 14 -> 190;
      case 30 -> 490;
      case 100 -> 990;
      default -> 0;
    };
  }

  /** 按用户与自然日取唯一打卡行；uk_uci_user_date 保证最多一行，故 LIMIT 1 不需要额外排序。 */
  private UserCheckIn getByDate(Long userId, LocalDate date) {
    return this.getOne(
        new LambdaQueryWrapper<UserCheckIn>()
            .eq(UserCheckIn::getUserId, userId)
            .eq(UserCheckIn::getCheckDate, date)
            .last("LIMIT 1"));
  }
}
