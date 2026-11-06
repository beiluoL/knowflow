package com.knowflow.reward.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 成就定义实体：管理端维护的成就模板，一个模板对应一条解锁条件与一份 EXP 奖励。
 *
 * <p>映射 achievement 表（backend/src/main/resources/schema.sql）。本类不继承 BaseEntity，自带
 * id/createTime/updateTime/deleted；deleted 未标注 {@code @TableLogic}，查询不会自动追加 deleted=0 条件，当前解锁流程只按
 * status = 1 取模板。若将来用 deleted 表示下线，查询需自行加条件。
 *
 * <p>category 与 conditionType 在库中都是自由字符串，Java 侧没有封闭枚举约束，取值集合见 schema 列注释。
 */
@Data
@TableName("achievement")
public class Achievement {

  /** 主键，数据库自增；未标注 @TableId，由 MyBatis-Plus 按 id 字段名识别。 */
  private Long id;

  /**
   * 成就编码，英文标识（如 READ_1ST_DOC），对应唯一索引 idx_ach_code。
   *
   * <p>DDL 为 VARCHAR(50) NOT NULL，是成就的业务唯一键；同一条码重复插入会直接撞唯一索引，不做业务层查重。
   */
  private String code;

  /** 成就名称，DDL NOT NULL、VARCHAR(100)，原样展示在成就卡与解锁时间线。 */
  private String name;

  /** 成就描述，可空、VARCHAR(500)，仅用于前端成就卡说明文案，不参与解锁判定。 */
  private String description;

  /**
   * 图标名，DDL 默认 'trophy'、VARCHAR(50)，取值需与前端 Icon 组件支持的图标名一致。
   *
   * <p>AchievementServiceImpl 组装 VO 时对 null 回退为 "trophy"，因此历史数据缺值不会导致空图标。
   */
  private String icon;

  /**
   * 成就分类，DDL NOT NULL、VARCHAR(20)，建索引 idx_ach_category。
   *
   * <p>当前仅原样透传给 VO 供前端分组展示，Java 侧不做取值校验，也不是解锁条件的一部分。
   */
  private String category;

  /**
   * 解锁条件类型，DDL NOT NULL、VARCHAR(30)，决定 computeCurrent 统计哪张表的累计量。
   *
   * <p>代码实际支持：STREAK_DAYS（sys_user.streak_days）、CHECKIN_DAYS（打卡行数）、READ_DOCS（去重阅读文档数）、
   * FAVORITE_DOC（去重收藏数）、COMPLETE_PATH（有进度的路径数）、REVIEW_FLASHCARD（闪卡数）、
   * MISTAKE_MASTERED（已掌握错题数）。schema 列注释另列了 CODE_EXERCISE / NOTE_CREATED / MISTAKE_CLEARED /
   * CATEGORY_ALL，但代码没有对应分支、data.sql 种子也未使用；写入这些值会统计为 0，该成就永远不会自动解锁。
   */
  private String conditionType;

  /** 解锁阈值（达标所需数量），DDL NOT NULL；<= 0 时进度百分比固定为 0 且不触发解锁（代码以 target > 0 为前提）。 */
  private Integer conditionValue;

  /** 排序值，默认 0，越小越靠前；列表查询按 sortOrder、id 依次升序。 */
  private Integer sortOrder;

  /**
   * 达成奖励 EXP，默认 0。
   *
   * <p>解锁时累加到 sys_user.exp（历史成长值字段），不写 wallet.xp，也不产生 reward_ledger 流水，因此成就奖励不计入钱包余额与等级计算。
   */
  private Integer rewardExp;

  /** 状态：0 禁用 / 1 启用，默认 1；解锁流程只查询 status = 1 的模板，禁用不会回收已解锁记录。 */
  private Integer status;

  /** 创建时间，数据库默认 CURRENT_TIMESTAMP；实体未配置自动填充，插入不赋值时由数据库默认值兜底。 */
  private java.time.LocalDateTime createTime;

  /** 更新时间，数据库默认 CURRENT_TIMESTAMP 且没有 ON UPDATE 子句；仅插入时生效，后续更新需业务显式赋值。 */
  private java.time.LocalDateTime updateTime;

  /** 逻辑删除标记，默认 0；本类未标注 @TableLogic，只是普通列，查询与唯一索引都不会自动忽略它。 */
  private Integer deleted;
}
