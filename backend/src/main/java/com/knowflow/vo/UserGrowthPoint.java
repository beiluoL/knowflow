package com.knowflow.vo;

import lombok.Data;

/**
 * 用户增长趋势的单个数据点（按天），用于后台概览的增长曲线展示。
 */
@Data
public class UserGrowthPoint {

    /** 日期标签，如「周一」或「7/20」。 */
    private String day;

    /** 当日新增用户数。 */
    private Long newUsers;

    /** 截至当日结束的累计用户数。 */
    private Long totalUsers;
}
