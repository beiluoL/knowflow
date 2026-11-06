package com.knowflow.reward.vo;

import java.util.List;
import lombok.Data;

/**
 * 成就页面整体 VO：成就列表、概览统计与最近解锁时间线，由 AchievementServiceImpl#getMyAchievements 一次性组装。
 *
 * <p>GET /api/achievements 虽然返回本 VO，但该方法带自动解锁与 sys_user.exp 写入副作用，不是纯查询响应。
 *
 * <p>所有统计都以 status=1 的启用模板为分母，已解锁计数包含本次请求刚自动解锁的条目；时间线只含查询开始时已存在的解锁行。
 */
@Data
public class AchievementPageVO {

  /** 成就列表，按模板 sort_order、id 升序；无启用模板时为空列表。 */
  private List<AchievementItemVO> achievements;

  /** 已解锁成就数，包含本次请求刚自动解锁的条目。 */
  private Integer unlockedCount;

  /** 启用模板总数，等于 achievements.size()。 */
  private Integer totalCount;

  /** 总进度百分比，整数除法 {@code unlockedCount * 100 / totalCount}；模板数为 0 时显式给 0。 */
  private Integer totalPercent;

  /** 按已解锁模板 rewardExp 累加的展示值，含本次刚解锁；不来自 wallet.xp，也不来自 reward_ledger 流水。 */
  private Integer totalAchievementExp;

  /** 最近解锁时间线，最多 10 条，按解锁行 create_time 倒序；本次刚解锁的条目要下次请求才出现。 */
  private List<RecentUnlockVO> recentUnlocks;

  /** 时间线条目：模板展示字段加解锁行的相对时间文案，由本次查询开始时的 user_achievement 行生成。 */
  @Data
  public static class RecentUnlockVO {

    /** 成就模板 ID，取 user_achievement.achievement_id；模板已不在启用列表时该行会被跳过。 */
    private Long achievementId;

    /** 成就名称，取 achievement.name。 */
    private String name;

    /** 成就描述，取 achievement.description，可能为空。 */
    private String description;

    /** 图标名，取 achievement.icon；为 null 时回退 trophy。 */
    private String icon;

    /** 分类，取 achievement.category，语义同 AchievementItemVO#category。 */
    private String category;

    /** 模板定义的奖励经验值（null 按 0），不是用户实际到账的余额增量。 */
    private Integer exp;

    /**
     * 相对时间文案，由 formatTimeAgo 生成：null 时间返回空串；只有「刚刚 / N 分钟前 / N 小时前 / N 天前 / N 周前 / N 个月前」，没有「昨天」分支。
     */
    private String timeAgo;
  }
}
