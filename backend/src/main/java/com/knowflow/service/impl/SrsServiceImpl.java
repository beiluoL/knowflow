package com.knowflow.service.impl;

import com.knowflow.service.SrsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * SM-2 间隔重复算法统一实现。
 * <p>
 * 唯一的 SM-2 业务实现入口：学习中心闪卡与工作台复习卡的复习调度均收敛到此处，
 * 通过 passQuality 参数适配两类卡片的评分语义（学习闪卡 0~5 及格 3，工作台卡 0~3 及格 2）。
 */
@Service
public class SrsServiceImpl implements SrsService {

    /** 首次复习间隔（天） */
    private static final int FIRST_INTERVAL = 1;
    /** 第二次复习间隔（天） */
    private static final int SECOND_INTERVAL = 6;

    @Override
    public SrsResult grade(int quality, int passQuality, SrsState state, LocalDateTime now) {
        // 评分夹取到标准 SM-2 五分制 [0,5]
        int q = Math.max(0, Math.min(5, quality));
        boolean lapsed = q < passQuality;

        int repetitions = state == null ? 0 : Math.max(0, state.getRepetitions());
        int interval = state == null ? 0 : Math.max(0, state.getIntervalDay());
        int ef = (state == null || state.getEaseFactor() <= 0) ? DEFAULT_EF : state.getEaseFactor();
        int lapseCount = state == null ? 0 : Math.max(0, state.getLapseCount());

        // SM-2 核心：EF' = EF + (0.1 - (5-q)(0.08 + (5-q)*0.02))，下限 1.3
        double efDouble = ef / 100.0 + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        ef = (int) Math.round(Math.max(MIN_EF / 100.0, efDouble) * 100);

        if (lapsed) {
            // 遗忘：连续答对归零，间隔重置为 1 天后重新学习
            repetitions = 0;
            interval = FIRST_INTERVAL;
            lapseCount++;
        } else {
            repetitions++;
            if (repetitions == 1) {
                interval = FIRST_INTERVAL;
            } else if (repetitions == 2) {
                interval = SECOND_INTERVAL;
            } else {
                // 下限保护：历史/种子数据可能 interval=0，避免算出 0 天间隔
                interval = (int) Math.round(Math.max(1, interval * (ef / 100.0)));
            }
        }

        SrsResult result = new SrsResult();
        result.setRepetitions(repetitions);
        result.setIntervalDay(interval);
        result.setEaseFactor(ef);
        result.setLapseCount(lapseCount);
        result.setLapsed(lapsed);
        result.setNextReviewAt(now.plusDays(interval));
        return result;
    }
}
