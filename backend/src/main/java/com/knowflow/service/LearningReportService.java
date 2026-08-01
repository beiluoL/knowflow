package com.knowflow.service;

import com.knowflow.vo.LearningReportVO;

/** 学习报告业务服务接口：聚合用户周期内学习数据。 */
public interface LearningReportService {

    /**
     * 获取学习报告。
     *
     * @param userId 用户 ID
     * @param period 周期：week（最近7天）/ month（最近30天）/ all（不限）
     * @return 学习报告聚合数据
     */
    LearningReportVO getReport(Long userId, String period);
}
