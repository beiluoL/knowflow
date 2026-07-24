package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String email;

    private String password;

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
}
