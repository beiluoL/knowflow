package com.knowflow.reward.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户学习宠物实体。 每个用户拥有一只陪伴宠物，通过喂食/玩耍/专注积累经验并升级。
 *
 * <p>映射 user_pet；主键、创建/更新时间、逻辑删除继承 BaseEntity，不声明乐观锁版本。 自有字段除 userId 外在当前 DDL
 * 中可空，默认值不等于非空约束；服务创建路径显式初始化，不能据此推断历史数据非空。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_pet")
public class UserPet extends BaseEntity {
  /** 宠物所属用户 ID，逻辑关联 sys_user.id；由服务端认证身份指定，DDL 要求非空。 */
  private Long userId;

  /** 宠物显示名称；创建为“小鹰”，更新直接保存请求名称，不裁剪空格。 */
  private String name;

  /** 成长等级，创建为 1；单次经验结算达到 maxExp 时只增加一级，不循环升级。 */
  private Integer level;

  /** 心情文本；创建及喂食写入“开心”，当前没有封闭枚举或状态转换校验。 */
  private String mood;

  /** 体力点数；创建为 80，喂食和玩耍将计算结果限制到 0–100，并非数据库 CHECK 约束。 */
  private Integer energy;

  /** 当前等级经验点数；喂食、玩耍或专注累加，达到 maxExp 后清零，超出阈值的经验不结转。 */
  private Integer exp;

  /** 升级所需经验点数，创建为 100；升级后按原阈值乘 1.5 并向下取整。 */
  private Integer maxExp;

  /** 头像标识，创建为 owl；更新接受非空白字符串，服务端未限制标识集合或要求 URL 格式。 */
  private String avatar;

  /** 累计获授专注时长，单位分钟；按 focusGain/grantFocus 的分钟增量累加，不从番茄数换算。 */
  private Integer totalFocusMinutes;

  /** 累计获授番茄钟数量，单位个；按调用方传入的 pomodoros 增量累加，不由分钟数推算。 */
  private Integer totalPomodoros;
}
