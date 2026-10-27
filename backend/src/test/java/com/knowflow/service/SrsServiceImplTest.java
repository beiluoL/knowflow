package com.knowflow.service;

import com.knowflow.service.impl.SrsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一 SRS 服务（SM-2）单元测试。
 * 验证：通过序列 1/6/interval×EF、遗忘重置、EF 更新公式、EF 下限、quality 夹取。
 */
class SrsServiceImplTest {

    private final SrsService srs = new SrsServiceImpl();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 30, 10, 0);

    private SrsService.SrsState newState() {
        return new SrsService.SrsState();
    }

    @Test
    @DisplayName("通过序列：第1次 interval=1，第2次 interval=6，之后 interval×EF")
    void passSequence() {
        // 第一次答对（q=5）
        SrsService.SrsResult r1 = srs.grade(5, 3, newState(), now);
        assertEquals(1, r1.getRepetitions());
        assertEquals(1, r1.getIntervalDay());
        assertFalse(r1.isLapsed());
        assertEquals(now.plusDays(1), r1.getNextReviewAt());

        // 第二次答对（q=5）
        SrsService.SrsState s2 = newState();
        s2.setRepetitions(r1.getRepetitions());
        s2.setIntervalDay(r1.getIntervalDay());
        s2.setEaseFactor(r1.getEaseFactor());
        SrsService.SrsResult r2 = srs.grade(5, 3, s2, now);
        assertEquals(2, r2.getRepetitions());
        assertEquals(6, r2.getIntervalDay());

        // 第三次答对：EF 先按 q=5 更新（250→260→270→280），再用新 EF 算间隔：6 × 2.8 = 16.8 → 17
        SrsService.SrsState s3 = newState();
        s3.setRepetitions(r2.getRepetitions());
        s3.setIntervalDay(r2.getIntervalDay());
        s3.setEaseFactor(r2.getEaseFactor());
        SrsService.SrsResult r3 = srs.grade(5, 3, s3, now);
        assertEquals(3, r3.getRepetitions());
        assertEquals(17, r3.getIntervalDay());
        assertEquals(280, r3.getEaseFactor());
    }

    @Test
    @DisplayName("遗忘（quality < passQuality）：repetitions 归零、interval=1、lapseCount+1")
    void lapseResets() {
        SrsService.SrsState state = newState();
        state.setRepetitions(3);
        state.setIntervalDay(10);
        state.setEaseFactor(250);
        state.setLapseCount(1);

        SrsService.SrsResult r = srs.grade(1, 3, state, now);
        assertTrue(r.isLapsed());
        assertEquals(0, r.getRepetitions());
        assertEquals(1, r.getIntervalDay());
        assertEquals(2, r.getLapseCount());
        assertEquals(now.plusDays(1), r.getNextReviewAt());
    }

    @Test
    @DisplayName("EF 更新公式：q=4 中性（EF 不变）、q=0 大幅下降但受下限 1.3 保护")
    void easeFactorFormula() {
        // q=4：0.1 - (1)(0.08+0.02) = 0 → EF 不变
        SrsService.SrsState s4 = newState();
        s4.setEaseFactor(250);
        SrsService.SrsResult r4 = srs.grade(4, 3, s4, now);
        assertEquals(250, r4.getEaseFactor());

        // 反复 q=0 遗忘，EF 收敛到下限 130
        SrsService.SrsState s0 = newState();
        s0.setEaseFactor(250);
        for (int i = 0; i < 5; i++) {
            SrsService.SrsResult r = srs.grade(0, 3, s0, now);
            s0.setEaseFactor(r.getEaseFactor());
        }
        assertEquals(SrsService.MIN_EF, s0.getEaseFactor());
    }

    @Test
    @DisplayName("quality 夹取到 [0,5]：负数按 0、大于 5 按 5 处理")
    void qualityClamp() {
        SrsService.SrsState state = newState();
        SrsService.SrsResult rLow = srs.grade(-10, 3, state, now);
        assertTrue(rLow.isLapsed());
        assertEquals(0, rLow.getRepetitions());

        SrsService.SrsResult rHigh = srs.grade(99, 3, state, now);
        assertFalse(rHigh.isLapsed());
        assertEquals(1, rHigh.getRepetitions());
    }

    @Test
    @DisplayName("easeFactor 缺省（0 或未设置）按默认 250 处理")
    void defaultEaseFactor() {
        SrsService.SrsState state = newState(); // easeFactor 默认 0
        SrsService.SrsResult r = srs.grade(4, 3, state, now);
        assertEquals(SrsService.DEFAULT_EF, r.getEaseFactor());
    }

    @Test
    @DisplayName("边界保护：interval=0 且 repetitions>=3（历史/种子脏数据）时间隔至少为 1 天")
    void zeroIntervalGuard() {
        SrsService.SrsState state = newState();
        state.setRepetitions(3);
        state.setIntervalDay(0); // 脏数据
        state.setEaseFactor(250);
        SrsService.SrsResult r = srs.grade(4, 3, state, now);
        assertFalse(r.isLapsed());
        assertTrue(r.getIntervalDay() >= 1, "interval 不应为 0");
        assertTrue(r.getNextReviewAt().isAfter(now));
    }
}
