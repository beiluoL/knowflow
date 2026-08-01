package com.knowflow.service;

import com.knowflow.vo.AchievementPageVO;

/**
 * 成就/勋章业务服务接口：成就列表（含进度/解锁状态）、概览统计、自动解锁判定。
 */
public interface AchievementService {

    /**
     * 获取当前用户的成就页面数据：成就列表（含解锁状态与进度）、统计概览、最近解锁时间线。
     * 自动检查未解锁成就的进度，满足条件时自动解锁并发放经验奖励。
     */
    AchievementPageVO getMyAchievements(Long userId);

    /**
     * 轻量触发成就检查（不返回页面数据），适用于前端页面级触发刷新进度。
     */
    void triggerAchievementCheck(Long userId);
}
