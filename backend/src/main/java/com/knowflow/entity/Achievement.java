package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 成就定义实体：预定义的成就模板，编码唯一不可重复。
 * 管理端可增删启用/禁用，用户端根据条件自动解锁。
 */
@Data
@TableName("achievement")
public class Achievement {

    private Long id;
    /** 成就编码（英文标识，如 READ_1ST_DOC） */
    private String code;
    /** 成就名称 */
    private String name;
    /** 成就描述 */
    private String description;
    /** 成就图标名（Icon.vue 图标名） */
    private String icon;
    /** 分类：LEARNING / EXPLORATION / COMMUNITY / PERSISTENCE / SPECIAL */
    private String category;
    /** 条件类型 */
    private String conditionType;
    /** 条件阈值 */
    private Integer conditionValue;
    /** 排序值 */
    private Integer sortOrder;
    /** 达成奖励经验值 */
    private Integer rewardExp;
    /** 状态：0 禁用 / 1 启用 */
    private Integer status;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
}
