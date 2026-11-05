package com.knowflow.interview.contract;

/** 按所有者隔离的近期活动投影，保留现有三条记录窗口。 */
public interface RecentInterviewActivityQuery {
    /** 仅读取指定认证所有者最近三条记录。 */
    int boundedCount(long ownerId);
}
