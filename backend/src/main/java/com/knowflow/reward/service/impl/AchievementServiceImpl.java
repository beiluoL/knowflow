package com.knowflow.reward.service.impl;

import com.knowflow.exception.BusinessException;
import com.knowflow.identity.contract.RewardMemberAccess;
import com.knowflow.identity.contract.RewardMemberAccess.Member;
import com.knowflow.reward.entity.Achievement;
import com.knowflow.reward.entity.UserAchievement;
import com.knowflow.reward.mapper.AchievementMapper;
import com.knowflow.reward.mapper.UserAchievementMapper;
import com.knowflow.reward.service.AchievementService;
import com.knowflow.reward.vo.AchievementItemVO;
import com.knowflow.reward.vo.AchievementPageVO;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成就业务实现：按 status=1 的成就模板实时计算用户进度，达标时自动解锁并把奖励经验写入 sys_user.exp。
 *
 * <p>「读接口带写副作用」：GET /api/achievements 与 POST /api/achievements/trigger 都调用 getMyAchievements，后者会插入
 * user_achievement 并更新用户经验，不是纯查询。
 *
 * <p>奖励口径：成就奖励只累加到 sys_user.exp（经 RewardMemberAccess#setLegacyExperience 绝对赋值），不写 wallet.xp、不产生
 * reward_ledger 流水，与钱包等级体系相互独立。
 *
 * <p>模板来源：achievement 表由 data.sql 种子写入，当前没有管理端 CRUD；进度统计用 JdbcTemplate 只读 COUNT / 标量查询，条件类型分支见
 * computeCurrent。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

  private final AchievementMapper achievementMapper;
  private final UserAchievementMapper userAchievementMapper;
  private final RewardMemberAccess sysUserMapper;
  private final JdbcTemplate jdbcTemplate;

  /**
   * 组装当前用户的成就页：逐模板进度与解锁状态、概览统计、最近解锁时间线。
   *
   * <p>执行顺序：
   *
   * <ol>
   *   <li>校验用户存在，加载 status=1 模板（sort_order / id 升序）与本人已有解锁行。
   *   <li>逐模板计算 current；已解锁直接置 100%，未解锁且 current >= target 时在同一事务内插入解锁行并绝对赋值 sys_user.exp。
   *   <li>汇总 unlockedCount / totalPercent / totalAchievementExp，分母是模板总数。
   *   <li>用查询开始时读到的解锁行生成最近 10 条时间线。
   * </ol>
   *
   * <p>事务与并发：rollbackFor = Exception.class，解锁插入与经验更新必须同事务；user_achievement 的唯一键 (user_id,
   * achievement_id) 不含 deleted 且无 @TableLogic，插入未捕获 DuplicateKeyException，重复或并发解锁会抛唯一键冲突并回滚整个请求。
   *
   * <p>统计口径：totalAchievementExp 是按模板 rewardExp 累加的展示值（含本次刚解锁的），不是从 wallet
   * 或流水读取；时间线只含本次查询开始时已存在的解锁行，本次刚解锁的成就要下次请求才出现。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得，服务端不信任请求参数
   * @return 成就页 VO，items 顺序与模板排序一致；无模板时返回空列表、totalPercent=0
   * @throws BusinessException 用户不存在时抛单参异常（code=500，HTTP 200 + body code 500 的假成功语义），调用方需按响应体判定
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public AchievementPageVO getMyAchievements(Long userId) {
    Member user = sysUserMapper.find(userId);
    if (user == null) throw new BusinessException("用户不存在");

    List<Achievement> allDefs =
        achievementMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Achievement>()
                .eq(Achievement::getStatus, 1)
                .orderByAsc(Achievement::getSortOrder)
                .orderByAsc(Achievement::getId));
    List<UserAchievement> myUnlocks =
        userAchievementMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAchievement>()
                .eq(UserAchievement::getUserId, userId));
    Map<Long, UserAchievement> unlockMap =
        myUnlocks.stream()
            .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua, (a, b) -> a));
    int totalAchievementExp = 0;
    int unlockedCnt = 0;
    List<AchievementItemVO> items = new ArrayList<>();

    for (Achievement def : allDefs) {
      AchievementItemVO item = buildItem(def);
      UserAchievement ua = unlockMap.get(def.getId());

      if (ua != null) {
        item.setUnlocked(true);
        item.setCurrent(def.getConditionValue());
        item.setPercent(100);
        item.setExp(def.getRewardExp() != null ? def.getRewardExp() : 0);
        item.setUnlockedTime(ua.getCreateTime() != null ? ua.getCreateTime().toString() : null);
        totalAchievementExp += item.getExp();
        unlockedCnt++;
      } else {
        int current = computeCurrent(userId, def.getConditionType());
        int target = def.getConditionValue();
        item.setUnlocked(false);
        item.setCurrent(current);
        item.setPercent(target > 0 ? Math.min(100, current * 100 / target) : 0);
        item.setExp(0);

        if (target > 0 && current >= target) {
          UserAchievement newUa = new UserAchievement();
          newUa.setUserId(userId);
          newUa.setAchievementId(def.getId());
          userAchievementMapper.insert(newUa);

          int expGain = def.getRewardExp() != null ? def.getRewardExp() : 0;
          int updatedExp = (user.getExp() != null ? user.getExp() : 0) + expGain;
          sysUserMapper.setLegacyExperience(userId, updatedExp);
          user.setExp(updatedExp);

          item.setUnlocked(true);
          item.setCurrent(target);
          item.setPercent(100);
          item.setExp(expGain);
          item.setUnlockedTime(LocalDateTime.now().toString());
          totalAchievementExp += expGain;
          unlockedCnt++;
          log.info("成就自动解锁：userId={}, name={}, expGain={}", userId, def.getName(), expGain);
        }
      }
      items.add(item);
    }

    AchievementPageVO vo = new AchievementPageVO();
    vo.setAchievements(items);
    vo.setTotalCount(items.size());
    vo.setUnlockedCount(unlockedCnt);
    vo.setTotalPercent(items.size() > 0 ? unlockedCnt * 100 / items.size() : 0);
    vo.setTotalAchievementExp(totalAchievementExp);

    List<UserAchievement> recent =
        myUnlocks.stream()
            .sorted(
                Comparator.comparing(
                    UserAchievement::getCreateTime,
                    Comparator.nullsFirst(Comparator.reverseOrder())))
            .limit(10)
            .collect(Collectors.toList());
    Map<Long, Achievement> defMap =
        allDefs.stream().collect(Collectors.toMap(Achievement::getId, d -> d, (a, b) -> a));

    List<AchievementPageVO.RecentUnlockVO> timeline = new ArrayList<>();
    for (UserAchievement ua : recent) {
      Achievement def = defMap.get(ua.getAchievementId());
      if (def == null) continue;
      AchievementPageVO.RecentUnlockVO rv = new AchievementPageVO.RecentUnlockVO();
      rv.setAchievementId(ua.getAchievementId());
      rv.setName(def.getName());
      rv.setDescription(def.getDescription());
      rv.setIcon(def.getIcon() != null ? def.getIcon() : "trophy");
      rv.setCategory(def.getCategory());
      rv.setExp(def.getRewardExp() != null ? def.getRewardExp() : 0);
      rv.setTimeAgo(formatTimeAgo(ua.getCreateTime()));
      timeline.add(rv);
    }
    vo.setRecentUnlocks(timeline);
    return vo;
  }

  /**
   * 轻量触发成就检查：复用 getMyAchievements 完成自动解锁，但丢弃页面数据。
   *
   * <p>失败只记 warn 不向调用方抛出，因此 POST /api/achievements/trigger 即使解锁失败也返回成功；这是刻意的尽力而为语义，不能当作解锁已完成的确认。
   *
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils；null 时直接返回，不做任何查询
   */
  @Override
  public void triggerAchievementCheck(Long userId) {
    if (userId == null) return;
    try {
      getMyAchievements(userId);
    } catch (Exception e) {
      log.warn("triggerAchievementCheck failed, userId={}", userId, e);
    }
  }

  /** 把成就模板的展示字段复制到列表项；icon 为 null 时回退 "trophy"，rewardExp 为 null 时按 0 处理。 */
  private AchievementItemVO buildItem(Achievement def) {
    AchievementItemVO item = new AchievementItemVO();
    item.setId(def.getId());
    item.setCode(def.getCode());
    item.setName(def.getName());
    item.setDescription(def.getDescription());
    item.setIcon(def.getIcon() != null ? def.getIcon() : "trophy");
    item.setCategory(def.getCategory());
    item.setTarget(def.getConditionValue());
    item.setRewardExp(def.getRewardExp() != null ? def.getRewardExp() : 0);
    return item;
  }

  /**
   * 按 conditionType 实时统计用户当前累计值；SQL 全部使用 ? 参数绑定并显式过滤 deleted = 0。
   *
   * <p>已实现 7 个分支：STREAK_DAYS 取 sys_user.streak_days；CHECKIN_DAYS / COMPLETE_PATH / REVIEW_FLASHCARD
   * / MISTAKE_MASTERED 取行数（路径要求 progress > 0，错题要求 mastered = 1）；READ_DOCS / FAVORITE_DOC 按 doc_id
   * 去重计数。
   *
   * <p>schema 列注释另列的 CODE_EXERCISE / NOTE_CREATED / MISTAKE_CLEARED / CATEGORY_ALL 没有对应分支，命中时返回
   * 0，这些模板永远不会自动解锁；type 或 userId 为 null 同样返回 0。
   */
  private int computeCurrent(Long userId, String type) {
    if (type == null || userId == null) return 0;
    switch (type) {
      case "STREAK_DAYS":
        return queryInt("SELECT streak_days FROM sys_user WHERE id = ?", userId);
      case "CHECKIN_DAYS":
        return queryInt(
            "SELECT COUNT(*) FROM user_check_in WHERE user_id = ? AND deleted = 0", userId);
      case "READ_DOCS":
        return queryInt(
            "SELECT COUNT(DISTINCT doc_id) FROM doc_read_progress WHERE user_id = ? AND deleted = 0",
            userId);
      case "FAVORITE_DOC":
        return queryInt(
            "SELECT COUNT(DISTINCT doc_id) FROM doc_favorite WHERE user_id = ? AND deleted = 0",
            userId);
      case "COMPLETE_PATH":
        return queryInt(
            "SELECT COUNT(*) FROM learning_user_path WHERE user_id = ? AND progress > 0 AND deleted = 0",
            userId);
      case "REVIEW_FLASHCARD":
        return queryInt(
            "SELECT COUNT(*) FROM learning_flashcard WHERE user_id = ? AND deleted = 0", userId);
      case "MISTAKE_MASTERED":
        return queryInt(
            "SELECT COUNT(*) FROM learning_mistake WHERE user_id = ? AND mastered = 1 AND deleted = 0",
            userId);
      default:
        return 0;
    }
  }

  /** 执行单值查询并把 null 结果折算为 0；COUNT 恒返回一行，STREAK_DAYS 依赖入口已确认存在的用户行。 */
  private int queryInt(String sql, Long userId) {
    Integer val = jdbcTemplate.queryForObject(sql, Integer.class, userId);
    return val != null ? val : 0;
  }

  /**
   * 把解锁时间格式化为相对文案：null 返回空串，按「刚刚 / N 分钟前 / N 小时前 / N 天前 / N 周前 / N 个月前」逐级取整。
   *
   * <p>基准是服务端 LocalDateTime.now()，未做时区换算；30 天按一个月、7 天按一周粗算，没有「昨天」分支。
   */
  private String formatTimeAgo(LocalDateTime time) {
    if (time == null) return "";
    LocalDateTime now = LocalDateTime.now();
    long seconds = ChronoUnit.SECONDS.between(time, now);
    if (seconds < 60) return "刚刚";
    long minutes = seconds / 60;
    if (minutes < 60) return minutes + " 分钟前";
    long hours = minutes / 60;
    if (hours < 24) return hours + " 小时前";
    long days = hours / 24;
    if (days < 7) return days + " 天前";
    if (days < 30) return (days / 7) + " 周前";
    long months = days / 30;
    return months + " 个月前";
  }
}
