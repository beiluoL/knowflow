package com.knowflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户视图对象，返回用户基本资料及学习概况。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String email;

    private String nickname;

    private String avatar;

    /** 角色编码，如 admin、user */
    private String role;

    private BigDecimal totalStudyHours;

    private Integer readDocsCount;

    private Integer streakDays;

    private Integer favoriteCount;

    /** 用户等级 */
    private Integer level;

    /** 经验值 */
    private Integer exp;

    /** 精力值（能量） */
    private Integer energy;

    private LocalDateTime createTime;
}
