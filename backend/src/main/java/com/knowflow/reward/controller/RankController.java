package com.knowflow.reward.controller;

import com.knowflow.common.Result;
import com.knowflow.identity.contract.RewardMemberAccess.Member;
import com.knowflow.identity.contract.RewardMemberAccess;
import com.knowflow.reward.vo.RankUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局排行榜接口：按经验值 / 阅读量等维度返回 Top N 用户。
 */
@Tag(name = "排行榜接口")
@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankController {

    private final RewardMemberAccess sysUserMapper;

    /** 排行最少人数 */
    private static final int MIN_LIMIT = 1;
    /** 排行最多人数 */
    private static final int MAX_LIMIT = 100;

    @Operation(summary = "全局排行榜（按 exp 降序）")
    @GetMapping
    public Result<List<RankUserVO>> ranking(
            @RequestParam(defaultValue = "20") Integer limit) {
        int size = Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
        var users = sysUserMapper.ranking(size);
        List<RankUserVO> result = new ArrayList<>();
        int rank = 1;
        for (Member u : users) {
            RankUserVO vo = new RankUserVO();
            vo.setRank(rank++);
            vo.setUserId(u.getId());
            vo.setNickname(u.getNickname() != null && !u.getNickname().isEmpty()
                    ? u.getNickname() : u.getUsername());
            vo.setAvatar(u.getAvatar());
            vo.setLevel(u.getLevel());
            vo.setExp(u.getExp());
            vo.setStreakDays(u.getStreakDays());
            vo.setReadDocsCount(u.getReadDocsCount());
            result.add(vo);
        }
        return Result.success(result);
    }
}
