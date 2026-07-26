package com.knowflow.vo;

import lombok.Data;

/**
 * 平台最近活动流中的单条记录，聚合社区发帖与用户注册等真实动态。
 */
@Data
public class RecentActivity {

    private Long id;

    /** 触发活动的用户昵称或用户名。 */
    private String userName;

    /** 活动动作描述，如「发布了帖子《xxx》」。 */
    private String action;

    /** 相对时间描述，如「3 分钟前」。 */
    private String time;

    /** 活动类型，前端据此映射图标与配色：post / register。 */
    private String type;
}
