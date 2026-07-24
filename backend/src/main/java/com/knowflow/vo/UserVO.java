package com.knowflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String email;

    private String nickname;

    private String avatar;

    private String role;

    private BigDecimal totalStudyHours;

    private Integer readDocsCount;

    private Integer streakDays;

    private Integer favoriteCount;

    private Integer level;

    private Integer exp;

    private Integer energy;

    private LocalDateTime createTime;
}
