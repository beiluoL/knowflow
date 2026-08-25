package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.HabitDTO;
import com.knowflow.entity.Habit;
import com.knowflow.entity.HabitCheckIn;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.HabitCheckInMapper;
import com.knowflow.mapper.HabitMapper;
import com.knowflow.service.HabitService;
import com.knowflow.vo.HabitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 习惯打卡业务服务实现：打卡幂等累加、连续天数统计与进度可视化数据组装。
 */
@Service
@RequiredArgsConstructor
public class HabitServiceImpl extends ServiceImpl<HabitMapper, Habit> implements HabitService {

    private final HabitCheckInMapper habitCheckInMapper;

    @Override
    public List<HabitVO> listHabits(Long userId) {
        List<Habit> habits = this.list(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .eq(Habit::getActive, 1)
                .orderByAsc(Habit::getSortOrder)
                .orderByAsc(Habit::getId));
        List<HabitVO> result = new ArrayList<>();
        for (Habit h : habits) {
            result.add(buildVO(h, userId));
        }
        return result;
    }

    @Override
    public Long createHabit(Long userId, HabitDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("习惯名称不能为空");
        }
        Habit h = new Habit();
        h.setUserId(userId);
        h.setName(dto.getName().trim());
        h.setDescription(dto.getDescription());
        h.setIcon(dto.getIcon() != null ? dto.getIcon() : "repeat");
        h.setColor(dto.getColor() != null ? dto.getColor() : "var(--kb-primary)");
        h.setFrequency(dto.getFrequency() != null ? dto.getFrequency() : "daily");
        h.setTargetCount(dto.getTargetCount() != null && dto.getTargetCount() > 0 ? dto.getTargetCount() : 1);
        h.setReminderTime(dto.getReminderTime());
        h.setStartDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now());
        h.setActive(1);
        h.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        this.save(h);
        return h.getId();
    }

    @Override
    public void updateHabit(Long userId, Long id, HabitDTO dto) {
        Habit h = owned(userId, id);
        if (dto.getName() != null) h.setName(dto.getName().trim());
        if (dto.getDescription() != null) h.setDescription(dto.getDescription());
        if (dto.getIcon() != null) h.setIcon(dto.getIcon());
        if (dto.getColor() != null) h.setColor(dto.getColor());
        if (dto.getFrequency() != null) h.setFrequency(dto.getFrequency());
        if (dto.getTargetCount() != null) h.setTargetCount(dto.getTargetCount());
        if (dto.getReminderTime() != null) h.setReminderTime(dto.getReminderTime());
        if (dto.getStartDate() != null) h.setStartDate(dto.getStartDate());
        if (dto.getActive() != null) h.setActive(dto.getActive());
        if (dto.getSortOrder() != null) h.setSortOrder(dto.getSortOrder());
        this.updateById(h);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHabit(Long userId, Long id) {
        owned(userId, id);
        // 逻辑删除习惯
        Habit h = this.getById(id);
        h.setActive(0);
        this.updateById(h);
        // 打卡记录逻辑删除
        List<HabitCheckIn> records = habitCheckInMapper.selectList(new LambdaQueryWrapper<HabitCheckIn>()
                .eq(HabitCheckIn::getHabitId, id));
        for (HabitCheckIn r : records) {
            r.setDeleted(1);
            habitCheckInMapper.updateById(r);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HabitVO checkIn(Long userId, Long id) {
        Habit h = owned(userId, id);
        LocalDate today = LocalDate.now();
        HabitCheckIn record = getByDate(userId, id, today);
        if (record == null) {
            record = new HabitCheckIn();
            record.setUserId(userId);
            record.setHabitId(id);
            record.setCheckDate(today);
            record.setCount(1);
            record.setNote(null);
            habitCheckInMapper.insert(record);
        } else {
            record.setCount(record.getCount() + 1);
            habitCheckInMapper.updateById(record);
        }
        return buildVO(h, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HabitVO undoCheckIn(Long userId, Long id) {
        Habit h = owned(userId, id);
        LocalDate today = LocalDate.now();
        HabitCheckIn record = getByDate(userId, id, today);
        if (record == null || record.getCount() <= 0) {
            return buildVO(h, userId);
        }
        if (record.getCount() <= 1) {
            record.setCount(0);
            habitCheckInMapper.updateById(record);
        } else {
            record.setCount(record.getCount() - 1);
            habitCheckInMapper.updateById(record);
        }
        return buildVO(h, userId);
    }

    @Override
    public HabitVO getHabit(Long userId, Long id) {
        Habit h = owned(userId, id);
        return buildVO(h, userId);
    }

    // ===== 内部工具 =====

    private Habit owned(Long userId, Long id) {
        Habit h = this.getById(id);
        if (h == null || h.getDeleted() != null && h.getDeleted() == 1 || !userId.equals(h.getUserId())) {
            throw new BusinessException("习惯不存在");
        }
        return h;
    }

    private HabitCheckIn getByDate(Long userId, Long habitId, LocalDate date) {
        return habitCheckInMapper.selectOne(new LambdaQueryWrapper<HabitCheckIn>()
                .eq(HabitCheckIn::getUserId, userId)
                .eq(HabitCheckIn::getHabitId, habitId)
                .eq(HabitCheckIn::getCheckDate, date)
                .last("LIMIT 1"));
    }

    /** 获取某习惯全部打卡记录，构建 日期->次数 映射。 */
    private Map<LocalDate, Integer> loadCountMap(Long habitId) {
        List<HabitCheckIn> records = habitCheckInMapper.selectList(new LambdaQueryWrapper<HabitCheckIn>()
                .eq(HabitCheckIn::getHabitId, habitId));
        Map<LocalDate, Integer> map = new HashMap<>();
        for (HabitCheckIn r : records) {
            if (r.getCheckDate() != null && r.getCount() != null) {
                map.merge(r.getCheckDate(), r.getCount(), Integer::sum);
            }
        }
        return map;
    }

    private HabitVO buildVO(Habit h, Long userId) {
        HabitVO vo = new HabitVO();
        vo.setId(h.getId());
        vo.setName(h.getName());
        vo.setDescription(h.getDescription());
        vo.setIcon(h.getIcon());
        vo.setColor(h.getColor());
        vo.setFrequency(h.getFrequency());
        vo.setTargetCount(h.getTargetCount());
        vo.setReminderTime(h.getReminderTime());
        vo.setStartDate(h.getStartDate());
        vo.setActive(h.getActive());
        vo.setSortOrder(h.getSortOrder());

        int target = h.getTargetCount() != null && h.getTargetCount() > 0 ? h.getTargetCount() : 1;
        boolean weekly = "weekly".equalsIgnoreCase(h.getFrequency());
        LocalDate today = LocalDate.now();
        Map<LocalDate, Integer> countMap = loadCountMap(h.getId());

        // 今日打卡
        int todayCount = countMap.getOrDefault(today, 0);
        vo.setTodayCount(todayCount);
        vo.setCompletedToday(todayCount >= target);

        // 连续 / 最佳 / 累计
        if (weekly) {
            int[] wk = computeWeeklyStreak(countMap, target, today);
            vo.setStreak(wk[0]);
            vo.setBestStreak(wk[1]);
            vo.setTotalDays(wk[2]);
        } else {
            int[] dk = computeDailyStreak(countMap, target, today);
            vo.setStreak(dk[0]);
            vo.setBestStreak(dk[1]);
            vo.setTotalDays(dk[2]);
        }

        // 近 7 天 / 近 30 天进度
        vo.setWeekly(buildRange(countMap, target, today.minusDays(6), today));
        vo.setMonthly(buildRange(countMap, target, today.minusDays(29), today));
        return vo;
    }

    /** 日频连续天数：从今日/昨日回溯连续达标日。同时算最佳与累计。 */
    private int[] computeDailyStreak(Map<LocalDate, Integer> map, int target, LocalDate today) {
        // 当前连续：今日达标从今日起，否则昨日达标从昨日起，否则 0
        int streak = 0;
        LocalDate cursor;
        if (countFor(map, today) >= target) {
            streak = 1;
            cursor = today.minusDays(1);
        } else if (countFor(map, today.minusDays(1)) >= target) {
            cursor = today.minusDays(1);
        } else {
            cursor = null;
        }
        while (cursor != null && countFor(map, cursor) >= target) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        // 最佳连续 + 累计达标天数：遍历全部达标日，按日期排序逐日判断连续
        List<LocalDate> doneDays = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : map.entrySet()) {
            if (e.getValue() >= target) doneDays.add(e.getKey());
        }
        doneDays.sort(LocalDate::compareTo);
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : doneDays) {
            if (prev != null && d.equals(prev.plusDays(1))) {
                run++;
            } else {
                run = 1;
            }
            if (run > best) best = run;
            prev = d;
        }
        return new int[]{streak, best, doneDays.size()};
    }

    /** 周频连续：以周一为周起点，统计本周累计是否达标，并回溯连续达标周。 */
    private int[] computeWeeklyStreak(Map<LocalDate, Integer> map, int target, LocalDate today) {
        LocalDate thisWeekStart = today.with(DayOfWeek.MONDAY);
        LocalDate thisWeekEnd = thisWeekStart.plusDays(6);
        // 当前连续：本周达标从本周起，否则上周达标从上周起
        int streak = 0;
        LocalDate weekStart;
        if (sumWeek(map, thisWeekStart, thisWeekEnd) >= target) {
            streak = 1;
            weekStart = thisWeekStart.minusWeeks(1);
        } else if (sumWeek(map, thisWeekStart.minusWeeks(1), thisWeekEnd.minusWeeks(1)) >= target) {
            weekStart = thisWeekStart.minusWeeks(1);
        } else {
            weekStart = null;
        }
        while (weekStart != null && sumWeek(map, weekStart, weekStart.plusDays(6)) >= target) {
            streak++;
            weekStart = weekStart.minusWeeks(1);
        }

        // 最佳连续周 + 累计达标天数：从最早记录周遍历到本周
        LocalDate earliest = map.keySet().stream().min(LocalDate::compareTo).orElse(today);
        LocalDate firstWeek = earliest.with(DayOfWeek.MONDAY);
        int best = 0;
        int run = 0;
        int totalDays = 0;
        LocalDate w = firstWeek;
        // 逐周累计
        while (!w.isAfter(thisWeekStart)) {
            int sum = sumWeek(map, w, w.plusDays(6));
            if (sum >= target) {
                run++;
                totalDays++;
                if (run > best) best = run;
            } else {
                run = 0;
            }
            w = w.plusWeeks(1);
        }
        return new int[]{streak, best, totalDays};
    }

    private int sumWeek(Map<LocalDate, Integer> map, LocalDate start, LocalDate end) {
        int sum = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            sum += countFor(map, d);
        }
        return sum;
    }

    private int countFor(Map<LocalDate, Integer> map, LocalDate date) {
        return map.getOrDefault(date, 0);
    }

    private List<HabitVO.DayProgress> buildRange(Map<LocalDate, Integer> map, int target, LocalDate from, LocalDate to) {
        List<HabitVO.DayProgress> list = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            HabitVO.DayProgress p = new HabitVO.DayProgress();
            p.setDate(d.toString());
            int c = countFor(map, d);
            p.setCount(c);
            p.setCompleted(c >= target);
            list.add(p);
        }
        return list;
    }
}
