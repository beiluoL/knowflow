package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.ChallengeSubmitDTO;
import com.knowflow.entity.CodeChallenge;
import com.knowflow.vo.ChallengeDetailVO;
import com.knowflow.vo.ChallengeRankVO;
import com.knowflow.vo.ChallengeStatsVO;
import com.knowflow.vo.ChallengeSubmitResultVO;
import com.knowflow.vo.ChallengeVO;

import java.util.List;

/**
 * 编程挑战（闯关游戏化）业务服务接口：赛道列表/详情、关卡提交判定（星级/积分/解锁）、排行榜与个人统计。
 */
public interface CodeChallengeService extends IService<CodeChallenge> {

    /**
     * 已发布赛道列表；userId 非空时附带当前用户进度。
     *
     * @param userId 当前用户 ID，可空（匿名访问）
     */
    List<ChallengeVO> listChallenges(Long userId);

    /**
     * 赛道详情（含全部关卡与用户各关状态）；userId 非空时计算解锁/星级状态。
     *
     * @param challengeId 赛道 ID
     * @param userId      当前用户 ID，可空（匿名时仅第一关解锁）
     */
    ChallengeDetailVO getDetail(Long challengeId, Long userId);

    /**
     * 提交关卡：前端执行测试用例后上报结果，后端判定通关并计算星级、积分、解锁下一关。
     * <p>星级规则（首通生效）：1 次通过 3 星；2-3 次通过 2 星；4 次及以上 1 星。
     * 积分规则：3 星得满分，2 星 80%，1 星 60%；同时同步累加至用户经验值。
     *
     * @param challengeId 赛道 ID
     * @param levelId     关卡 ID
     * @param dto         提交内容（代码 + 用例通过情况）
     * @param userId      当前用户 ID（必须登录）
     */
    ChallengeSubmitResultVO submitLevel(Long challengeId, Long levelId, ChallengeSubmitDTO dto, Long userId);

    /**
     * 排行榜：challengeId 非空返回该赛道榜，否则返回全部赛道积分总榜。
     *
     * @param challengeId 赛道 ID，可空
     * @param limit       返回条数上限（1-100）
     */
    List<ChallengeRankVO> leaderboard(Long challengeId, Integer limit);

    /** 当前用户挑战累计统计（参与赛道、通关数、积分、星星、总榜名次）。 */
    ChallengeStatsVO getMyStats(Long userId);
}
