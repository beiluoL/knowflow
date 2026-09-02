package com.knowflow.service;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一 SRS（间隔重复）服务 —— SM-2 算法全系统唯一业务实现入口。
 * <p>
 * 收敛目标：学习中心闪卡（learning_flashcard）与知识库工作台复习卡（wb_review_card）
 * 的复习调度全部走本服务，不再各自维护一份 SM-2 逻辑；两张表保留原有数据结构，
 * 仅将「评分 → 间隔/难度系数」的计算收敛到此处（Phase 0 Service 收敛，不删表）。
 */
public interface SrsService {

    /** 默认难度系数 2.5（×100 存储，与 wb_review_card.ease_factor 同约定） */
    int DEFAULT_EF = 250;

    /** 难度系数下限 1.3（×100 存储） */
    int MIN_EF = 130;

    /**
     * 卡片当前 SM-2 状态（与具体业务表解耦，由调用方从各自实体映射而来）。
     */
    @Data
    class SrsState {
        /** 连续答对次数（答错归零） */
        private int repetitions;
        /** 当前复习间隔（天） */
        private int intervalDay;
        /** 难度系数 ×100（默认 250 即 2.5） */
        private int easeFactor;
        /** 累计遗忘次数 */
        private int lapseCount;
    }

    /**
     * SM-2 计算结果（由调用方落库并写复习日志/事件）。
     */
    @Data
    class SrsResult {
        /** 连续答对次数（答错归零） */
        private int repetitions;
        /** 新复习间隔（天） */
        private int intervalDay;
        /** 新难度系数 ×100 */
        private int easeFactor;
        /** 累计遗忘次数 */
        private int lapseCount;
        /** 本次是否判定为遗忘（quality &lt; passQuality） */
        private boolean lapsed;
        /** 下次应复习时间 = now + intervalDay */
        private LocalDateTime nextReviewAt;
    }

    /**
     * 执行一次复习评分（标准 SM-2）。
     *
     * <pre>
     * EF' = EF + (0.1 - (5-q)(0.08 + (5-q)*0.02))，下限 1.3
     * 遗忘（quality &lt; passQuality）：repetitions=0，interval=1，lapseCount+1
     * 通过：repetitions+1；第1次 interval=1，第2次 interval=6，之后 interval × EF
     * </pre>
     *
     * @param quality     评分 0~5（标准 SM-2 五分制，内部自动夹取到 [0,5]）
     * @param passQuality 及格阈值：quality &lt; passQuality 视为遗忘
     *                    （学习中心闪卡=3，工作台复习卡=2，由调用方按领域传入）
     * @param state       卡片当前 SM-2 状态（新卡传 {@code new SrsState()}，easeFactor=0 时按 250 处理）
     * @param now         评分时刻（可注入便于测试）
     * @return 计算后的 SM-2 状态
     */
    SrsResult grade(int quality, int passQuality, SrsState state, LocalDateTime now);
}
