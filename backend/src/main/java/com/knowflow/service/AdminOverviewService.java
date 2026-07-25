package com.knowflow.service;

import com.knowflow.vo.AdminOverviewVO;

/**
 * 管理员概览统计 Service。
 */
public interface AdminOverviewService {

    /**
     * 获取管理员概览统计数据。
     *
     * @return 概览统计 VO
     */
    AdminOverviewVO getOverview();
}
