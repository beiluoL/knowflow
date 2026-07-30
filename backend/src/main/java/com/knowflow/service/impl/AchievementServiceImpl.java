package com.knowflow.service.impl;

import com.knowflow.entity.Achievement;
import com.knowflow.entity.UserAchievement;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.AchievementMapper;
import com.knowflow.mapper.UserAchievementMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.AchievementService;
import com.knowflow.vo.AchievementItemVO;
import com.knowflow.vo.AchievementPageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成就业务实现：从各用户数据表实时计算进度，满足条件自动解锁并发放 EXP 奖励。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AchievementPageVO getMyAchievements(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        List<Achievement> allDefs = achievementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Achievement>()
                        .eq(Achievement::getStatus, 1)
                        .orderByAsc(Achievement::getSortOrder)
                        .orderByAsc(Achievement::getId));

        List<UserAchievement> myUnlocks = userAchievementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId));
        Map<Long, UserAchievement> unlockMap = myUnlocks.stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua, (a, b) -> a));

        int totalAchievementExp = 0;
        int unlockedCnt = 0;
        List<AchievementItemVO> items = new ArrayList<>();

        for (Achievement def : allDefs) {
            AchievementItemVO item = buildItem(def);
            UserAchievement ua = unlockMap.get(def.getId());

            if (ua != null) {
                item.setUnlocked(true);
                item.setCurrent(def.getConditionValue());
                item.setPercent(100);
                item.setExp(def.getRewardExp() != null ? def.getRewardExp() : 0);
                item.setUnlockedTime(ua.getCreateTime() != null ? ua.getCreateTime().toString() : null);
                totalAchievementExp += item.getExp();
                unlockedCnt++;
            } else {
                int current = computeCurrent(userId, def.getConditionType());
                int target = def.getConditionValue();
                item.setUnlocked(false);
                item.setCurrent(current);
                item.setPercent(target > 0 ? Math.min(100, current * 100 / target) : 0);
                item.setExp(0);

                if (target > 0 && current >= target) {
                    UserAchievement newUa = new UserAchievement();
                    newUa.setUserId(userId);
                    newUa.setAchievementId(def.getId());
                    userAchievementMapper.insert(newUa);

                    int expGain = def.getRewardExp() != null ? def.getRewardExp() : 0;
                    SysUser update = new SysUser();
                    update.setId(userId);
                    update.setExp((user.getExp() != null ? user.getExp() : 0) + expGain);
                    sysUserMapper.updateById(update);
                    user.setExp(update.getExp());

                    item.setUnlocked(true);
                    item.setCurrent(target);
                    item.setPercent(100);
                    item.setExp(expGain);
                    item.setUnlockedTime(LocalDateTime.now().toString());
                    totalAchievementExp += expGain;
                    unlockedCnt++;
                    log.info("成就自动解锁：userId={}, name={}, expGain={}", userId, def.getName(), expGain);
                }
            }
            items.add(item);
        }

        AchievementPageVO vo = new AchievementPageVO();
        vo.setAchievements(items);
        vo.setTotalCount(items.size());
        vo.setUnlockedCount(unlockedCnt);
        vo.setTotalPercent(items.size() > 0 ? unlockedCnt * 100 / items.size() : 0);
        vo.setTotalAchievementExp(totalAchievementExp);

        List<UserAchievement> recent = myUnlocks.stream()
                .sorted(Comparator.comparing(UserAchievement::getCreateTime,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .limit(10).collect(Collectors.toList());
        Map<Long, Achievement> defMap = allDefs.stream()
                .collect(Collectors.toMap(Achievement::getId, d -> d, (a, b) -> a));

        List<AchievementPageVO.RecentUnlockVO> timeline = new ArrayList<>();
        for (UserAchievement ua : recent) {
            Achievement def = defMap.get(ua.getAchievementId());
            if (def == null) continue;
            AchievementPageVO.RecentUnlockVO rv = new AchievementPageVO.RecentUnlockVO();
            rv.setAchievementId(ua.getAchievementId());
            rv.setName(def.getName());
            rv.setDescription(def.getDescription());
            rv.setIcon(def.getIcon() != null ? def.getIcon() : "trophy");
            rv.setCategory(def.getCategory());
            rv.setExp(def.getRewardExp() != null ? def.getRewardExp() : 0);
            rv.setTimeAgo(formatTimeAgo(ua.getCreateTime()));
            timeline.add(rv);
        }
        vo.setRecentUnlocks(timeline);
        return vo;
    }

    private AchievementItemVO buildItem(Achievement def) {
        AchievementItemVO item = new AchievementItemVO();
        item.setId(def.getId());
        item.setCode(def.getCode());
        item.setName(def.getName());
        item.setDescription(def.getDescription());
        item.setIcon(def.getIcon() != null ? def.getIcon() : "trophy");
        item.setCategory(def.getCategory());
        item.setTarget(def.getConditionValue());
        item.setRewardExp(def.getRewardExp() != null ? def.getRewardExp() : 0);
        return item;
    }

    /** 根据条件类型查询用户在对应表中的当前累计值（JdbcTemplate COUNT 查询）。 */
    private int computeCurrent(Long userId, String type) {
        if (type == null || userId == null) return 0;
        switch (type) {
            case "STREAK_DAYS":
                return queryInt("SELECT streak_days FROM sys_user WHERE id = ?", userId);
            case "CHECKIN_DAYS":
                return queryInt("SELECT COUNT(*) FROM user_check_in WHERE user_id = ? AND deleted = 0", userId);
            case "READ_DOCS":
                return queryInt("SELECT COUNT(DISTINCT doc_id) FROM doc_read_progress WHERE user_id = ? AND deleted = 0", userId);
            case "FAVORITE_DOC":
                return queryInt("SELECT COUNT(DISTINCT doc_id) FROM doc_favorite WHERE user_id = ? AND deleted = 0", userId);
            case "COMPLETE_PATH":
                return queryInt("SELECT COUNT(*) FROM learning_user_path WHERE user_id = ? AND progress > 0 AND deleted = 0", userId);
            case "REVIEW_FLASHCARD":
                return queryInt("SELECT COUNT(*) FROM learning_flashcard WHERE user_id = ? AND deleted = 0", userId);
            case "MISTAKE_MASTERED":
                return queryInt("SELECT COUNT(*) FROM learning_mistake WHERE user_id = ? AND mastered = 1 AND deleted = 0", userId);
            default:
                return 0;
        }
    }

    /** 执行标量查询并转换 Integer 结果 */
    private int queryInt(String sql, Long userId) {
        Integer val = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return val != null ? val : 0;
    }

    /** 相对时间描述 */
    private String formatTimeAgo(LocalDateTime time) {
        if (time == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long seconds = ChronoUnit.SECONDS.between(time, now);
        if (seconds < 60) return "刚刚";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " 分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + " 小时前";
        long days = hours / 24;
        if (days < 7) return days + " 天前";
        if (days < 30) return (days / 7) + " 周前";
        long months = days / 30;
        return months + " 个月前";
    }
}
