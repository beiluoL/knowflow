package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.ChallengeSubmitDTO;
import com.knowflow.entity.CodeChallenge;
import com.knowflow.entity.CodeChallengeLevel;
import com.knowflow.entity.CodeChallengeLevelRecord;
import com.knowflow.entity.CodeChallengeRecord;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CodeChallengeLevelMapper;
import com.knowflow.mapper.CodeChallengeLevelRecordMapper;
import com.knowflow.mapper.CodeChallengeMapper;
import com.knowflow.mapper.CodeChallengeRecordMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.CodeChallengeService;
import com.knowflow.vo.ChallengeDetailVO;
import com.knowflow.vo.ChallengeLevelVO;
import com.knowflow.vo.ChallengeRankVO;
import com.knowflow.vo.ChallengeStatsVO;
import com.knowflow.vo.ChallengeSubmitResultVO;
import com.knowflow.vo.ChallengeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 编程挑战业务实现：判题结果由前端执行测试用例后上报（与代码题库一致），
 * 后端负责通关判定、星级计算、积分发放、关卡解锁与排行榜聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeChallengeServiceImpl extends ServiceImpl<CodeChallengeMapper, CodeChallenge>
        implements CodeChallengeService {

    private final CodeChallengeLevelMapper levelMapper;
    private final CodeChallengeRecordMapper recordMapper;
    private final CodeChallengeLevelRecordMapper levelRecordMapper;
    private final SysUserMapper userMapper;

    /** 星级计算阈值：通关提交次数 ≤ 该值时获得的星级（1 次通过 = 3 星） */
    private static final int STAR_THRESHOLD_THREE = 1;
    /** 星级计算阈值：通关提交次数 ≤ 该值时获得 2 星；其余（≥4 次）获得 1 星 */
    private static final int STAR_THRESHOLD_TWO = 3;

    /** 积分发放比例（相对关卡满分）：3 星满分 */
    private static final float POINTS_RATIO_THREE_STAR = 1.0f;
    /** 积分发放比例（相对关卡满分）：2 星 80% */
    private static final float POINTS_RATIO_TWO_STAR = 0.8f;
    /** 积分发放比例（相对关卡满分）：1 星 60% */
    private static final float POINTS_RATIO_ONE_STAR = 0.6f;

    /** 排行榜聚合数组下标：{积分, 星级, 通关数} */
    private static final int AGG_IDX_POINTS = 0;
    private static final int AGG_IDX_STARS = 1;
    private static final int AGG_IDX_CLEARED = 2;

    @Override
    public List<ChallengeVO> listChallenges(Long userId) {
        List<CodeChallenge> challenges = list(new LambdaQueryWrapper<CodeChallenge>()
                .eq(CodeChallenge::getStatus, 1)
                .orderByAsc(CodeChallenge::getSortOrder)
                .orderByAsc(CodeChallenge::getId));
        if (challenges.isEmpty()) {
            return new ArrayList<>();
        }

        // 当前用户各赛道进度
        Map<Long, CodeChallengeRecord> myRecords = new HashMap<>();
        if (userId != null) {
            List<CodeChallengeRecord> records = recordMapper.selectList(
                    new LambdaQueryWrapper<CodeChallengeRecord>().eq(CodeChallengeRecord::getUserId, userId));
            myRecords = records.stream()
                    .collect(Collectors.toMap(CodeChallengeRecord::getChallengeId, Function.identity(), (a, b) -> a));
        }

        // 各赛道参与人数
        List<CodeChallengeRecord> allRecords = recordMapper.selectList(
                new LambdaQueryWrapper<CodeChallengeRecord>().select(CodeChallengeRecord::getChallengeId));
        Map<Long, Long> playerCounts = allRecords.stream()
                .collect(Collectors.groupingBy(CodeChallengeRecord::getChallengeId, Collectors.counting()));

        List<ChallengeVO> result = new ArrayList<>();
        for (CodeChallenge c : challenges) {
            ChallengeVO vo = new ChallengeVO();
            vo.setId(c.getId());
            vo.setTitle(c.getTitle());
            vo.setDescription(c.getDescription());
            vo.setLanguage(c.getLanguage());
            vo.setDifficulty(c.getDifficulty());
            vo.setIcon(c.getIcon());
            vo.setThemeColor(c.getThemeColor());
            vo.setTags(c.getTags());
            vo.setLevelCount(c.getLevelCount());
            vo.setTotalPoints(c.getTotalPoints());
            vo.setPlayerCount(playerCounts.getOrDefault(c.getId(), 0L).intValue());

            CodeChallengeRecord r = myRecords.get(c.getId());
            vo.setJoined(r != null);
            vo.setClearedLevels(r == null ? 0 : nvl(r.getClearedLevels()));
            vo.setEarnedPoints(r == null ? 0 : nvl(r.getTotalPoints()));
            vo.setEarnedStars(r == null ? 0 : nvl(r.getTotalStars()));
            vo.setCompleted(r != null && r.getStatus() != null && r.getStatus() == 1);
            int levelCount = nvl(c.getLevelCount());
            vo.setProgressPercent(levelCount == 0 ? 0
                    : Math.min(100, Math.round(vo.getClearedLevels() * 100f / levelCount)));
            result.add(vo);
        }
        return result;
    }

    @Override
    public ChallengeDetailVO getDetail(Long challengeId, Long userId) {
        CodeChallenge challenge = getById(challengeId);
        if (challenge == null || challenge.getStatus() == null || challenge.getStatus() != 1) {
            throw new BusinessException("挑战不存在或未发布");
        }

        List<CodeChallengeLevel> levels = levelMapper.selectList(new LambdaQueryWrapper<CodeChallengeLevel>()
                .eq(CodeChallengeLevel::getChallengeId, challengeId)
                .eq(CodeChallengeLevel::getStatus, 1)
                .orderByAsc(CodeChallengeLevel::getLevelNo));

        // 当前用户各关卡记录
        Map<Long, CodeChallengeLevelRecord> levelRecords = new HashMap<>();
        CodeChallengeRecord myRecord = null;
        if (userId != null) {
            myRecord = recordMapper.selectOne(new LambdaQueryWrapper<CodeChallengeRecord>()
                    .eq(CodeChallengeRecord::getUserId, userId)
                    .eq(CodeChallengeRecord::getChallengeId, challengeId));
            List<CodeChallengeLevelRecord> lrs = levelRecordMapper.selectList(
                    new LambdaQueryWrapper<CodeChallengeLevelRecord>()
                            .eq(CodeChallengeLevelRecord::getUserId, userId)
                            .eq(CodeChallengeLevelRecord::getChallengeId, challengeId));
            levelRecords = lrs.stream()
                    .collect(Collectors.toMap(CodeChallengeLevelRecord::getLevelId, Function.identity(), (a, b) -> a));
        }

        ChallengeDetailVO vo = new ChallengeDetailVO();
        vo.setId(challenge.getId());
        vo.setTitle(challenge.getTitle());
        vo.setDescription(challenge.getDescription());
        vo.setLanguage(challenge.getLanguage());
        vo.setDifficulty(challenge.getDifficulty());
        vo.setIcon(challenge.getIcon());
        vo.setThemeColor(challenge.getThemeColor());
        vo.setTags(challenge.getTags());
        vo.setLevelCount(challenge.getLevelCount());
        vo.setTotalPoints(challenge.getTotalPoints());
        vo.setJoined(myRecord != null);
        vo.setClearedLevels(myRecord == null ? 0 : nvl(myRecord.getClearedLevels()));
        vo.setCurrentLevel(myRecord == null ? 1 : nvl(myRecord.getCurrentLevel(), 1));
        vo.setEarnedPoints(myRecord == null ? 0 : nvl(myRecord.getTotalPoints()));
        vo.setEarnedStars(myRecord == null ? 0 : nvl(myRecord.getTotalStars()));
        vo.setCompleted(myRecord != null && myRecord.getStatus() != null && myRecord.getStatus() == 1);

        // 逐关组装：上一关通关才解锁下一关（第一关始终解锁）
        boolean prevPassed = true;
        List<ChallengeLevelVO> levelVOs = new ArrayList<>();
        for (CodeChallengeLevel level : levels) {
            ChallengeLevelVO lv = new ChallengeLevelVO();
            lv.setId(level.getId());
            lv.setLevelNo(level.getLevelNo());
            lv.setTitle(level.getTitle());
            lv.setDescription(level.getDescription());
            lv.setDifficulty(level.getDifficulty());
            lv.setLanguage(level.getLanguage());
            lv.setHint(level.getHint());
            lv.setExampleInput(level.getExampleInput());
            lv.setExampleOutput(level.getExampleOutput());
            lv.setCodeTemplate(level.getCodeTemplate());
            lv.setTestCases(level.getTestCases());
            lv.setPoints(level.getPoints());

            CodeChallengeLevelRecord lr = levelRecords.get(level.getId());
            boolean passed = lr != null && lr.getPassed() != null && lr.getPassed() == 1;
            lv.setPassed(passed);
            lv.setStars(lr == null ? 0 : nvl(lr.getStars()));
            lv.setAttempts(lr == null ? 0 : nvl(lr.getAttempts()));
            lv.setPointsEarned(lr == null ? 0 : nvl(lr.getPointsEarned()));
            lv.setLastCode(lr == null ? null : lr.getLastCode());
            lv.setLocked(!prevPassed);
            prevPassed = passed;
            levelVOs.add(lv);
        }
        vo.setLevels(levelVOs);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChallengeSubmitResultVO submitLevel(Long challengeId, Long levelId, ChallengeSubmitDTO dto, Long userId) {
        CodeChallenge challenge = getById(challengeId);
        if (challenge == null || challenge.getStatus() == null || challenge.getStatus() != 1) {
            throw new BusinessException("挑战不存在或未发布");
        }
        CodeChallengeLevel level = levelMapper.selectById(levelId);
        if (level == null || !challengeId.equals(level.getChallengeId())
                || level.getStatus() == null || level.getStatus() != 1) {
            throw new BusinessException("关卡不存在或未发布");
        }

        // 解锁校验：非第一关需要上一关已通关
        if (level.getLevelNo() != null && level.getLevelNo() > 1) {
            CodeChallengeLevel prev = levelMapper.selectOne(new LambdaQueryWrapper<CodeChallengeLevel>()
                    .eq(CodeChallengeLevel::getChallengeId, challengeId)
                    .eq(CodeChallengeLevel::getLevelNo, level.getLevelNo() - 1));
            if (prev != null) {
                Long passedCount = levelRecordMapper.selectCount(new LambdaQueryWrapper<CodeChallengeLevelRecord>()
                        .eq(CodeChallengeLevelRecord::getUserId, userId)
                        .eq(CodeChallengeLevelRecord::getLevelId, prev.getId())
                        .eq(CodeChallengeLevelRecord::getPassed, 1));
                if (passedCount == 0) {
                    throw new BusinessException("请先通关上一关卡");
                }
            }
        }

        int total = nvl(dto.getTotal());
        int passCount = nvl(dto.getPassCount());
        boolean passedNow = total > 0 && passCount >= total;

        // 赛道进度记录（首次提交时创建）
        CodeChallengeRecord record = recordMapper.selectOne(new LambdaQueryWrapper<CodeChallengeRecord>()
                .eq(CodeChallengeRecord::getUserId, userId)
                .eq(CodeChallengeRecord::getChallengeId, challengeId));
        if (record == null) {
            record = new CodeChallengeRecord();
            record.setUserId(userId);
            record.setChallengeId(challengeId);
            record.setClearedLevels(0);
            record.setCurrentLevel(1);
            record.setTotalPoints(0);
            record.setTotalStars(0);
            record.setStatus(0);
            record.setStartTime(LocalDateTime.now());
            recordMapper.insert(record);
        }

        // 关卡记录（首次提交时创建）
        CodeChallengeLevelRecord levelRecord = levelRecordMapper.selectOne(
                new LambdaQueryWrapper<CodeChallengeLevelRecord>()
                        .eq(CodeChallengeLevelRecord::getUserId, userId)
                        .eq(CodeChallengeLevelRecord::getLevelId, levelId));
        boolean firstRecord = levelRecord == null;
        if (firstRecord) {
            levelRecord = new CodeChallengeLevelRecord();
            levelRecord.setUserId(userId);
            levelRecord.setChallengeId(challengeId);
            levelRecord.setLevelId(levelId);
            levelRecord.setLevelNo(level.getLevelNo());
            levelRecord.setPassed(0);
            levelRecord.setStars(0);
            levelRecord.setAttempts(0);
            levelRecord.setPointsEarned(0);
        }
        boolean alreadyPassed = levelRecord.getPassed() != null && levelRecord.getPassed() == 1;
        int attempts = nvl(levelRecord.getAttempts()) + 1;
        levelRecord.setAttempts(attempts);
        levelRecord.setLastCode(dto.getCode());

        ChallengeSubmitResultVO result = new ChallengeSubmitResultVO();
        result.setPassed(passedNow);
        result.setPassCount(passCount);
        result.setTotal(total);
        result.setFirstPass(false);
        result.setStars(nvl(levelRecord.getStars()));
        result.setPointsEarned(0);
        result.setUnlockedNext(false);
        result.setChallengeCompleted(false);

        boolean firstPass = passedNow && !alreadyPassed;
        if (firstPass) {
            // 星级：提交次数越少星级越高（首次即过 3 星，2~3 次 2 星，≥4 次 1 星）
            int stars = attempts <= STAR_THRESHOLD_THREE ? 3
                    : (attempts <= STAR_THRESHOLD_TWO ? 2 : 1);
            // 积分：星级决定发放比例（3 星满分、2 星 80%、1 星 60%）
            int fullPoints = nvl(level.getPoints());
            int pointsEarned = stars == 3 ? Math.round(fullPoints * POINTS_RATIO_THREE_STAR)
                    : stars == 2 ? Math.round(fullPoints * POINTS_RATIO_TWO_STAR)
                    : Math.round(fullPoints * POINTS_RATIO_ONE_STAR);

            levelRecord.setPassed(1);
            levelRecord.setStars(stars);
            levelRecord.setPointsEarned(pointsEarned);
            levelRecord.setFinishTime(LocalDateTime.now());

            // 更新赛道进度：通关数 / 积分 / 星星 / 当前关卡 / 是否全通
            int cleared = nvl(record.getClearedLevels()) + 1;
            record.setClearedLevels(cleared);
            record.setTotalPoints(nvl(record.getTotalPoints()) + pointsEarned);
            record.setTotalStars(nvl(record.getTotalStars()) + stars);
            int levelCount = nvl(challenge.getLevelCount());
            boolean challengeCompleted = levelCount > 0 && cleared >= levelCount;
            if (challengeCompleted) {
                record.setStatus(1);
                record.setFinishTime(LocalDateTime.now());
                record.setCurrentLevel(level.getLevelNo());
            } else if (level.getLevelNo() != null) {
                record.setCurrentLevel(Math.max(nvl(record.getCurrentLevel(), 1), level.getLevelNo() + 1));
            }

            // 积分同步累加到用户经验值（游戏化联动）
            SysUser user = userMapper.selectById(userId);
            if (user != null) {
                SysUser update = new SysUser();
                update.setId(userId);
                update.setExp(nvl(user.getExp()) + pointsEarned);
                userMapper.updateById(update);
            }

            result.setFirstPass(true);
            result.setStars(stars);
            result.setPointsEarned(pointsEarned);
            result.setUnlockedNext(!challengeCompleted);
            result.setNextLevelNo(challengeCompleted ? null : level.getLevelNo() + 1);
            result.setChallengeCompleted(challengeCompleted);
        }

        if (firstRecord) {
            levelRecordMapper.insert(levelRecord);
        } else {
            levelRecordMapper.updateById(levelRecord);
        }
        recordMapper.updateById(record);

        result.setAttempts(attempts);
        result.setTotalStars(nvl(record.getTotalStars()));
        result.setTotalPoints(nvl(record.getTotalPoints()));
        result.setClearedLevels(nvl(record.getClearedLevels()));
        log.info("挑战提交：userId={}, challengeId={}, levelId={}, passed={}, firstPass={}, stars={}",
                userId, challengeId, levelId, passedNow, result.getFirstPass(), result.getStars());
        return result;
    }

    @Override
    public List<ChallengeRankVO> leaderboard(Long challengeId, Integer limit) {
        int size = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        LambdaQueryWrapper<CodeChallengeRecord> wrapper = new LambdaQueryWrapper<>();
        if (challengeId != null) {
            wrapper.eq(CodeChallengeRecord::getChallengeId, challengeId);
        }
        List<CodeChallengeRecord> records = recordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        // 按用户聚合（总榜时跨赛道累加）：{积分, 星级, 通关数}
        Map<Long, int[]> agg = new HashMap<>();
        for (CodeChallengeRecord r : records) {
            int[] v = agg.computeIfAbsent(r.getUserId(), k -> new int[3]);
            v[AGG_IDX_POINTS] += nvl(r.getTotalPoints());
            v[AGG_IDX_STARS] += nvl(r.getTotalStars());
            v[AGG_IDX_CLEARED] += nvl(r.getClearedLevels());
        }

        List<Map.Entry<Long, int[]>> sorted = agg.entrySet().stream()
                .filter(e -> e.getValue()[AGG_IDX_POINTS] > 0 || e.getValue()[AGG_IDX_CLEARED] > 0)
                .sorted(Comparator
                        .comparingInt((Map.Entry<Long, int[]> e) -> e.getValue()[AGG_IDX_POINTS]).reversed()
                        .thenComparing(Comparator.comparingInt((Map.Entry<Long, int[]> e) -> e.getValue()[AGG_IDX_STARS]).reversed()))
                .limit(size)
                .collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> userIds = sorted.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<ChallengeRankVO> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Long, int[]> e : sorted) {
            ChallengeRankVO vo = new ChallengeRankVO();
            vo.setRank(rank++);
            vo.setUserId(e.getKey());
            SysUser u = users.get(e.getKey());
            vo.setNickname(u == null ? "未知用户" : (u.getNickname() == null || u.getNickname().isEmpty()
                    ? u.getUsername() : u.getNickname()));
            vo.setAvatar(u == null ? null : u.getAvatar());
            vo.setTotalPoints(e.getValue()[AGG_IDX_POINTS]);
            vo.setTotalStars(e.getValue()[AGG_IDX_STARS]);
            vo.setClearedLevels(e.getValue()[AGG_IDX_CLEARED]);
            result.add(vo);
        }
        return result;
    }

    @Override
    public ChallengeStatsVO getMyStats(Long userId) {
        List<CodeChallengeRecord> myRecords = recordMapper.selectList(
                new LambdaQueryWrapper<CodeChallengeRecord>().eq(CodeChallengeRecord::getUserId, userId));

        ChallengeStatsVO vo = new ChallengeStatsVO();
        vo.setJoinedChallenges(myRecords.size());
        vo.setCompletedChallenges((int) myRecords.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1).count());
        vo.setClearedLevels(myRecords.stream().mapToInt(r -> nvl(r.getClearedLevels())).sum());
        vo.setTotalPoints(myRecords.stream().mapToInt(r -> nvl(r.getTotalPoints())).sum());
        vo.setTotalStars(myRecords.stream().mapToInt(r -> nvl(r.getTotalStars())).sum());

        // 总榜名次：按用户累计积分倒序
        if (vo.getTotalPoints() > 0) {
            List<CodeChallengeRecord> all = recordMapper.selectList(null);
            Map<Long, Integer> pointsByUser = new HashMap<>();
            for (CodeChallengeRecord r : all) {
                pointsByUser.merge(r.getUserId(), nvl(r.getTotalPoints()), Integer::sum);
            }
            int myPoints = vo.getTotalPoints();
            long higher = pointsByUser.values().stream().filter(p -> p > myPoints).count();
            vo.setMyRank((int) higher + 1);
        }
        return vo;
    }

    /**
     * 将可能为 {@code null} 的整型字段转为基本类型，避免 NPE 与空值累加异常。
     *
     * @param v 可能为 null 的 Integer 字段
     * @return 非 null 时返回原值，null 时返回 0
     */
    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 将可能为 {@code null} 的整型字段转为基本类型，并指定 null 时的兜底默认值。
     *
     * @param v   可能为 null 的 Integer 字段
     * @param def v 为 null 时返回的默认值
     * @return 非 null 时返回原值，null 时返回 {@code def}
     */
    private static int nvl(Integer v, int def) {
        return v == null ? def : v;
    }
}
