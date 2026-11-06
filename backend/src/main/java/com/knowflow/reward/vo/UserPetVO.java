package com.knowflow.reward.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户学习宠物响应投影。
 *
 * <p>UserPetService.toVO 逐字段复制实体，不计算额外统计或脱敏，也不将 null 转为 0。 字段空值沿用实体状态，创建路径的默认值不构成对历史数据的非空承诺。
 */
@Data
public class UserPetVO {
  /** 宠物记录主键 ID，来自实体继承的自增主键，不是用户 ID。 */
  private Long id;

  /** 来自实体的宠物所属用户 ID，逻辑关联 sys_user.id；由服务端认证身份指定，DDL 要求非空。 */
  private Long userId;

  /** 来自实体的宠物显示名称；创建为“小鹰”，更新直接保存请求名称，不裁剪空格。 */
  private String name;

  /** 来自实体的成长等级，创建为 1；单次经验结算达到 maxExp 时只增加一级，不循环升级。 */
  private Integer level;

  /** 来自实体的心情文本；创建及喂食写入“开心”，当前没有封闭枚举或状态转换校验。 */
  private String mood;

  /** 来自实体的体力点数；创建为 80，喂食和玩耍将计算结果限制到 0–100，并非数据库 CHECK 约束。 */
  private Integer energy;

  /** 来自实体的当前等级经验点数；喂食、玩耍或专注累加，达到 maxExp 后清零，超出阈值的经验不结转。 */
  private Integer exp;

  /** 来自实体的升级所需经验点数，创建为 100；升级后按原阈值乘 1.5 并向下取整。 */
  private Integer maxExp;

  /** 来自实体的头像标识，创建为 owl；更新接受非空白字符串，服务端未限制标识集合或要求 URL 格式。 */
  private String avatar;

  /** 来自实体的累计获授专注时长，单位分钟；按 focusGain/grantFocus 的分钟增量累加，不从番茄数换算。 */
  private Integer totalFocusMinutes;

  /** 来自实体的累计获授番茄钟数量，单位个；按调用方传入的 pomodoros 增量累加，不由分钟数推算。 */
  private Integer totalPomodoros;

  /** 宠物记录创建时间，直接取实体 createTime；LocalDateTime 不携带时区偏移。 */
  private LocalDateTime createTime;

  /** 宠物记录更新时间，直接取实体 updateTime；LocalDateTime 不携带时区偏移。 */
  private LocalDateTime updateTime;
}
