package com.knowflow.exam.contract;

/** 按所有者隔离的近期活动投影，保留现有三条记录窗口。 */
public interface RecentExamActivityQuery {
    /** 仅读取指定认证所有者最近三条记录。 */
    java.util.List<String> statuses(long ownerId);
}
