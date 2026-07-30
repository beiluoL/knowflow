package com.knowflow.vo;

import lombok.Data;

/**
 * 全局排行榜条目 VO：按经验值排名。
 */
@Data
public class RankUserVO {

    /** 排名序号（从 1 开始） */
    private Integer rank;
    /** 用户 ID */
    private Long userId;
    /** 用户昵称 */
    private String nickname;
    /** 用户头像 URL */
    private String avatar;
    /** 用户等级 */
    private Integer level;
    /** 总经验值 */
    private Integer exp;
    /** 连续打卡天数 */
    private Integer streakDays;
    /** 阅读文档数 */
    private Integer readDocsCount;
}
