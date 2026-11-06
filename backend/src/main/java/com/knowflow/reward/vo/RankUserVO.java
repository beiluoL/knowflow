package com.knowflow.reward.vo;

import lombok.Data;

/**
 * 全局排行榜条目 VO：GET /api/ranking 按 sys_user.exp 降序取 Top N（1-100），由 RankController 逐条组装。
 *
 * <p>数据来自 RewardMemberAccess.Member 对 sys_user 的字段投影，不是 wallet 表；因此 exp / level
 * 与钱包余额是两套口径，不相加也不互相校验。
 *
 * <p>nickname 与 avatar 直接透传用户资料，服务端未做脱敏；本 VO 不含当前登录用户标识，前端需自行判断是否为自己。
 */
@Data
public class RankUserVO {

  /** 名次，从 1 开始按返回顺序递增；exp 相同没有并列规则，先后取决于数据库返回顺序。 */
  private Integer rank;

  /** 用户 ID，取 sys_user.id 投影。 */
  private Long userId;

  /** 展示昵称：nickname 非空时用它，否则回退 username；两列都为空时才可能为 null。 */
  private String nickname;

  /** 头像 URL，直接透传 sys_user.avatar，未脱敏；可能为 null。 */
  private String avatar;

  /** 用户等级，取 sys_user.level；不是 WalletVO.level（后者由 LevelService 按 wallet.xp 派生）。 */
  private Integer level;

  /** 总经验值，取 sys_user.exp，也是排行榜排序键；成就解锁只累加 sys_user.exp，不写 wallet.xp。 */
  private Integer exp;

  /** 连续打卡天数，取 sys_user.streak_days；由 CheckInServiceImpl#checkIn 在打卡成功时同步。 */
  private Integer streakDays;

  /** 阅读文档数，取 sys_user.read_docs_count；由身份模块维护，排行榜接口只读不重算。 */
  private Integer readDocsCount;
}
