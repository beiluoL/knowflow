package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.common.LearningEventType;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.UserCheckIn;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.UserCheckInMapper;
import com.knowflow.service.CheckInService;
import com.knowflow.service.LearningEventService;
import com.knowflow.vo.CheckInResultVO;
import com.knowflow.vo.CheckInStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 每日打卡业务服务实现：连续天数计算、奖励发放与 streak 同步。 */
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl extends ServiceImpl<UserCheckInMapper, UserCheckIn> implements CheckInService {

    private final SysUserMapper sysUserMapper;
    private final LearningEventService learningEventService;

    /** 每日基础奖励经验值。 */
    private static final int BASE_EXP = 10;
    /** 每日基础奖励精力值。 */
    private static final int BASE_ENERGY = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckInResultVO checkIn(Long userId) {
        LocalDate today = LocalDate.now();
        CheckInResultVO vo = new CheckInResultVO();

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

        // 连续天数：昨日已打卡则续接，否则从 1 重新开始
        UserCheckIn yesterdayRecord = getByDate(userId, today.minusDays(1));
        int continuous = (yesterdayRecord != null && yesterdayRecord.getContinuousDays() != null)
                ? yesterdayRecord.getContinuousDays() + 1 : 1;

        int rewardExp = BASE_EXP + milestoneBonus(continuous);

        UserCheckIn record = new UserCheckIn();
        record.setUserId(userId);
        record.setCheckDate(today);
        record.setContinuousDays(continuous);
        record.setRewardExp(rewardExp);
        record.setRewardEnergy(BASE_ENERGY);
        this.save(record);

        // Learning Event System（Phase 1）：每日签到事件（仅真实新增签到记录时触发）
        learningEventService.record(userId, LearningEventType.CHECK_IN, "CHECK_IN", null,
                Map.of("continuousDays", continuous, "rewardExp", rewardExp, "rewardEnergy", BASE_ENERGY));

        // 同步 sys_user.streak_days（用户统计以该字段展示连续学习天数）
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setStreakDays(continuous);
            sysUserMapper.updateById(user);
        }

        vo.setCheckedToday(true);
        vo.setAlreadyChecked(false);
        vo.setContinuousDays(continuous);
        vo.setRewardExp(rewardExp);
        vo.setRewardEnergy(BASE_ENERGY);
        return vo;
    }

    @Override
    public CheckInStatusVO getStatus(Long userId) {
        LocalDate today = LocalDate.now();
        CheckInStatusVO vo = new CheckInStatusVO();

        UserCheckIn todayRecord = getByDate(userId, today);
        boolean checkedToday = todayRecord != null;
        vo.setCheckedToday(checkedToday);

        // 连续天数：今日已打卡取今日记录；否则若昨日打卡则 streak 仍存活，取昨日记录；再否则为 0
        if (checkedToday) {
            vo.setContinuousDays(todayRecord.getContinuousDays());
        } else {
            UserCheckIn yesterdayRecord = getByDate(userId, today.minusDays(1));
            vo.setContinuousDays(yesterdayRecord != null && yesterdayRecord.getContinuousDays() != null
                    ? yesterdayRecord.getContinuousDays() : 0);
        }

        long total = this.count(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId));
        vo.setTotalDays((int) total);

        // 本月打卡日历：取当月已打卡日期号
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        List<UserCheckIn> monthRecords = this.list(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .ge(UserCheckIn::getCheckDate, monthStart)
                .le(UserCheckIn::getCheckDate, monthEnd));
        List<Integer> days = monthRecords.stream()
                .map(r -> r.getCheckDate().getDayOfMonth())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        vo.setMonthCheckedDays(days.isEmpty() ? new ArrayList<>() : days);
        return vo;
    }

    /** 里程碑奖励：连续 7/14/30/100 天额外加成。 */
    private int milestoneBonus(int continuous) {
        return switch (continuous) {
            case 7 -> 90;
            case 14 -> 190;
            case 30 -> 490;
            case 100 -> 990;
            default -> 0;
        };
    }

    private UserCheckIn getByDate(Long userId, LocalDate date) {
        return this.getOne(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .eq(UserCheckIn::getCheckDate, date)
                .last("LIMIT 1"));
    }
}
