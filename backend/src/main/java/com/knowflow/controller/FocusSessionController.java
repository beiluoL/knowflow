package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.FocusSessionEndDTO;
import com.knowflow.entity.FocusSession;
import com.knowflow.entity.UserPet;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.FocusSessionMapper;
import com.knowflow.mapper.UserPetMapper;
import com.knowflow.vo.FocusStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "专注会话接口")
@RestController
@RequestMapping("/api/focus/sessions")
@RequiredArgsConstructor
@Slf4j
public class FocusSessionController {

    private final FocusSessionMapper focusSessionMapper;
    private final UserPetMapper userPetMapper;

    private static final String DEFAULT_MODE = "POMODORO";

    @Operation(summary = "开始专注会话")
    @PostMapping("/start")
    public Result<FocusSession> start(@RequestParam(required = false) String mode) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = new FocusSession();
        session.setUserId(userId);
        session.setMode(mode != null && !mode.isBlank() ? mode : DEFAULT_MODE);
        session.setStartTime(LocalDateTime.now());
        session.setDistractionCount(0);
        session.setCompletedPomodoros(0);
        session.setDurationMin(0);
        focusSessionMapper.insert(session);
        return Result.success(session);
    }

    @Operation(summary = "结束专注会话")
    @PostMapping("/{id}/end")
    public Result<FocusSession> end(@PathVariable Long id,
                                    @Valid @RequestBody FocusSessionEndDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = focusSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("无权操作该会话");
        }
        session.setEndTime(LocalDateTime.now());
        session.setDurationMin(dto.getDurationMin());
        if (dto.getDistractionCount() != null) {
            session.setDistractionCount(dto.getDistractionCount());
        }
        if (dto.getCompletedPomodoros() != null) {
            session.setCompletedPomodoros(dto.getCompletedPomodoros());
        }
        if (dto.getAssociatedTaskId() != null) {
            session.setAssociatedTaskId(dto.getAssociatedTaskId());
        }
        if (dto.getQualityRating() != null) {
            session.setQualityRating(dto.getQualityRating());
        }
        if (dto.getNote() != null) {
            session.setNote(dto.getNote());
        }
        focusSessionMapper.updateById(session);

        int minutes = dto.getDurationMin() != null ? dto.getDurationMin() : 0;
        if (minutes >= 1) {
            int pomodoros = dto.getCompletedPomodoros() != null ? dto.getCompletedPomodoros() : 0;
            grantPetFocus(userId, minutes, pomodoros);
        }
        return Result.success(session);
    }

    @Operation(summary = "获取今日会话列表")
    @GetMapping("/today")
    public Result<List<FocusSession>> today() {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<FocusSession> list = focusSessionMapper.selectList(new LambdaQueryWrapper<FocusSession>()
                .eq(FocusSession::getUserId, userId)
                .ge(FocusSession::getStartTime, startOfDay)
                .orderByDesc(FocusSession::getStartTime));
        return Result.success(list);
    }

    @Operation(summary = "获取专注统计")
    @GetMapping("/stats")
    public Result<FocusStatsVO> stats(@RequestParam(defaultValue = "7") Integer days) {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime daysAgo = LocalDate.now().minusDays(days - 1L).atStartOfDay();

        List<FocusSession> recentSessions = focusSessionMapper.selectList(new LambdaQueryWrapper<FocusSession>()
                .eq(FocusSession::getUserId, userId)
                .ge(FocusSession::getStartTime, daysAgo)
                .le(FocusSession::getStartTime, endOfDay)
                .orderByDesc(FocusSession::getStartTime));

        FocusStatsVO vo = new FocusStatsVO();

        int todayMinutes = 0;
        int todayPomodoros = 0;
        int todaySessions = 0;
        int weekMinutes = 0;
        int totalQualitySum = 0;
        int totalQualityCount = 0;
        Map<String, Integer> modeBreakdown = new HashMap<>();
        List<Integer> hourlyHeatmap = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) {
            hourlyHeatmap.add(0);
        }

        for (FocusSession s : recentSessions) {
            Integer dur = s.getDurationMin() != null ? s.getDurationMin() : 0;
            boolean isToday = !s.getStartTime().isBefore(startOfDay) && !s.getStartTime().isAfter(endOfDay);

            if (isToday) {
                todayMinutes += dur;
                todaySessions++;
                if (s.getCompletedPomodoros() != null) {
                    todayPomodoros += s.getCompletedPomodoros();
                }
                if (s.getStartTime() != null && dur > 0) {
                    int hour = s.getStartTime().getHour();
                    hourlyHeatmap.set(hour, hourlyHeatmap.get(hour) + dur);
                }
            }

            weekMinutes += dur;

            if (s.getQualityRating() != null) {
                totalQualitySum += s.getQualityRating();
                totalQualityCount++;
            }

            String mode = s.getMode() != null ? s.getMode() : DEFAULT_MODE;
            modeBreakdown.put(mode, modeBreakdown.getOrDefault(mode, 0) + dur);
        }

        vo.setTodayMinutes(todayMinutes);
        vo.setTodayPomodoros(todayPomodoros);
        vo.setTodaySessions(todaySessions);
        vo.setWeekMinutes(weekMinutes);
        if (totalQualityCount > 0) {
            BigDecimal avg = BigDecimal.valueOf(totalQualitySum)
                    .divide(BigDecimal.valueOf(totalQualityCount), 1, RoundingMode.HALF_UP);
            vo.setAvgQuality(avg.doubleValue());
        } else {
            vo.setAvgQuality(null);
        }
        vo.setModeBreakdown(modeBreakdown);
        vo.setHourlyHeatmap(hourlyHeatmap);

        List<FocusSession> recentList = recentSessions.size() > 10
                ? recentSessions.subList(0, 10)
                : recentSessions;
        vo.setRecentList(recentList);

        return Result.success(vo);
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<FocusSession> getById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = focusSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("无权查看该会话");
        }
        return Result.success(session);
    }

    @Operation(summary = "删除会话（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = focusSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("无权删除该会话");
        }
        focusSessionMapper.deleteById(id);
        return Result.success();
    }

    private void grantPetFocus(Long userId, int minutes, int pomodoros) {
        try {
            UserPet pet = getOrCreatePet(userId);
            pet.setTotalFocusMinutes(pet.getTotalFocusMinutes() + minutes);
            pet.setTotalPomodoros(pet.getTotalPomodoros() + pomodoros);
            pet.setExp(pet.getExp() + minutes);
            handleLevelUp(pet);
            userPetMapper.updateById(pet);
        } catch (Exception e) {
            log.warn("专注会话结束时更新宠物经验失败，userId={}", userId, e);
        }
    }

    private UserPet getOrCreatePet(Long userId) {
        UserPet pet = userPetMapper.selectOne(new LambdaQueryWrapper<UserPet>()
                .eq(UserPet::getUserId, userId)
                .last("LIMIT 1"));
        if (pet != null) {
            return pet;
        }
        pet = new UserPet();
        pet.setUserId(userId);
        pet.setName("小鹰");
        pet.setLevel(1);
        pet.setMood("开心");
        pet.setEnergy(80);
        pet.setExp(0);
        pet.setMaxExp(100);
        pet.setAvatar("owl");
        pet.setTotalFocusMinutes(0);
        pet.setTotalPomodoros(0);
        userPetMapper.insert(pet);
        return pet;
    }

    private void handleLevelUp(UserPet pet) {
        if (pet.getExp() >= pet.getMaxExp()) {
            pet.setLevel(pet.getLevel() + 1);
            pet.setExp(0);
            pet.setMaxExp((int) Math.floor(pet.getMaxExp() * 1.5));
        }
    }
}
