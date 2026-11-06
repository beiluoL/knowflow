package com.knowflow.reward.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户成就解锁记录：user_achievement 表的一行表示「某用户已解锁某成就」的事实与解锁时间。
 *
 * <p>一用户一成就只允许一条，由唯一索引 idx_ua_user_ach(user_id, achievement_id) 保证。该索引不含 deleted，而本类既不继承
 * BaseEntity、deleted 也没有 {@code @TableLogic}，所以「删除」只是把普通列置
 * 1，唯一键仍被占用；解锁插入（AchievementServiceImpl#getMyAchievements）未捕获
 * DuplicateKeyException，重复或并发解锁会抛唯一键冲突并回滚整个请求。
 *
 * <p>本表没有 update_time，实体因此不声明更新时间；解锁记录被视为一次性的历史事实，不提供更新语义。
 */
@Data
@TableName("user_achievement")
public class UserAchievement {

  /** 主键，数据库自增；未标注 @TableId，由 MyBatis-Plus 按 id 字段名识别。 */
  private Long id;

  /** 解锁用户 ID，逻辑外键 sys_user.id，DDL NOT NULL；是「我的成就」查询的过滤列，并参与唯一键。 */
  private Long userId;

  /** 成就模板 ID，逻辑外键 achievement.id，DDL NOT NULL；与 userId 组成唯一键，解锁判定据此判断是否已解锁。 */
  private Long achievementId;

  /**
   * 解锁时间，数据库默认 CURRENT_TIMESTAMP；实体无自动填充，插入不赋值时由数据库兜底。
   *
   * <p>前端「最近解锁」时间线与相对时间文案直接读取该字段；模板被删除时对应行不会被展示（按 achievementId 找不到定义会跳过），时间线仍以本字段倒序取前 10 条。
   */
  private java.time.LocalDateTime createTime;

  /** 逻辑删除标记，默认 0；未标注 @TableLogic，查询不会自动过滤，删除后唯一键仍阻止再次解锁。 */
  private Integer deleted;
}
