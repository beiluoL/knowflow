package com.knowflow.reward;

import org.springframework.stereotype.Service;

/**
 * 等级计算（exp → level），全项目唯一公式（§46-48）。
 * 公式与既有口径保持一致：level = exp / 100 + 1（原 UserServiceImpl.stats 推导公式迁移至此，
 * 消除「stats 一套、wallet 一套」的分裂，§277）。
 */
@Service
public class LevelService {

    /** exp → 等级 */
    public int levelOf(int xp) {
        return xp / 100 + 1;
    }

    /** 达到下一级所需的累计 exp 阈值（如 xp=3200 → 3300） */
    public int nextLevelExp(int xp) {
        return levelOf(xp) * 100;
    }

    /** 当前等级内进度（0-100），用于前端进度条 */
    public int progressInLevel(int xp) {
        int level = levelOf(xp);
        int floor = (level - 1) * 100;
        return Math.min(100, Math.max(0, xp - floor));
    }
}
