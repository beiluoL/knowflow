package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
/** 系统用户实体，承载账号、角色与学习统计等数据。 */
public class SysUser extends BaseEntity {

    private String username;

    private String email;

    private String password;

    private String nickname;

    private String avatar;

    /** 用户角色，如 user / admin。 */
    private String role;

    private BigDecimal totalStudyHours;

    private Integer readDocsCount;

    /** 连续学习天数（打卡 streak）。 */
    private Integer streakDays;

    private Integer favoriteCount;

    private Integer level;

    /** 经验值。 */
    private Integer exp;

    /** 精力值。 */
    private Integer energy;
}
