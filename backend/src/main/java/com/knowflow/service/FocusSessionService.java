package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.FocusSessionEndDTO;
import com.knowflow.entity.FocusSession;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.FocusSessionMapper;
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


@org.springframework.stereotype.Service

@RequiredArgsConstructor
@Slf4j
public class FocusSessionService {

    private final FocusSessionMapper focusSessionMapper;
    private final UserPetService userPetService;

    private static final String DEFAULT_MODE = "POMODORO";

    public FocusSession start(String mode) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = new FocusSession();
        session.setUserId(userId);
        session.setMode(mode != null && !mode.isBlank() ? mode : DEFAULT_MODE);
        session.setStartTime(LocalDateTime.now());
        session.setDistractionCount(0);
        session.setCompletedPomodoros(0);
        session.setDurationMin(0);
        focusSessionMapper.insert(session);
        return session;
    }

    /** 原子结束会话并累积宠物奖励；任一步失败均回滚。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public FocusSession end(Long id,
                                    FocusSessionEndDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        focusSessionMapper.lockSession(id);
        FocusSession session = focusSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权操作该会话");
        }
        if (session.getEndTime() != null) return session;
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
        if (focusSessionMapper.updateById(session) != 1) throw new BusinessException(409, "会话状态已变化");

        int minutes = dto.getDurationMin() != null ? dto.getDurationMin() : 0;
        if (minutes >= 1) {
            int pomodoros = dto.getCompletedPomodoros() != null ? dto.getCompletedPomodoros() : 0;
            userPetService.grantFocus(userId, minutes, pomodoros);
        }
        return session;
    }

    public List<FocusSession> today() {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<FocusSession> list = focusSessionMapper.selectList(new LambdaQueryWrapper<FocusSession>()
                .eq(FocusSession::getUserId, userId)
                .ge(FocusSession::getStartTime, startOfDay)
                .orderByDesc(FocusSession::getStartTime));
        return list;
    }

    public FocusStatsVO stats(Integer days) {
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

        return vo;
    }

    public FocusSession getById(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = focusSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权查看该会话");
        }
        return session;
    }

    public void delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        FocusSession session = focusSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权删除该会话");
        }
        focusSessionMapper.deleteById(id);
        return;
    }

}
