package com.knowflow.reward.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.identity.contract.RewardMemberAccess;
import com.knowflow.productivity.dto.FocusGainDTO;
import com.knowflow.reward.dto.UserPetUpdateDTO;
import com.knowflow.reward.entity.UserPet;
import com.knowflow.reward.mapper.UserPetMapper;
import com.knowflow.reward.vo.UserPetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习宠物服务：维护 user_pet 中每个用户的陪伴宠物，处理喂食、玩耍、专注结算与名称头像修改。
 *
 * <p>私有数据隔离：所有查询都带 userId 条件，userId 由 Controller 从 SecurityUtils 取得；宠物不与 wallet / reward_ledger
 * 互通，经验与等级是独立的展示体系。
 *
 * <p>并发模型：每个公共方法先用 RewardMemberAccess#lockActiveUserId 对 sys_user 行加行锁，把同一用户的「创建 + 读改写」串行化；user_pet
 * 只有普通索引没有唯一约束，并发首次创建全靠这把锁，UserPetTransactionTest 已验证两个并发专注结算只创建一只宠物且分钟数不丢。
 *
 * <p>事务与副作用：写方法全部 rollbackFor = Exception.class，updateById / insert 影响行数不为 1 时抛 409 使事务回滚；连只读的 get
 * 也会在宠物不存在时创建默认宠物。
 */
@Service
@RequiredArgsConstructor
public class UserPetService {
  private final UserPetMapper petMapper;
  private final RewardMemberAccess userMapper;

  /** 体力值上限，喂食与玩耍都把 energy 夹逼到该值；与 DDL 的 energy 0-100 口径一致。 */
  private static final int ENERGY_MAX = 100;

  /** 体力值下限；玩耍不校验余额是否够扣，不足时直接落到 0。 */
  private static final int ENERGY_MIN = 0;

  /** 单次喂食增加的体力值。 */
  private static final int FEED_ENERGY_GAIN = 10;

  /** 单次喂食增加的经验值。 */
  private static final int FEED_EXP_GAIN = 20;

  /** 单次玩耍消耗的体力值，不要求当前体力足额。 */
  private static final int PLAY_ENERGY_COST = 15;

  /** 单次玩耍增加的经验值。 */
  private static final int PLAY_EXP_GAIN = 30;

  /**
   * 读取当前用户宠物视图；宠物不存在时按默认值创建，因此 GET 请求带写副作用。
   *
   * <p>先锁用户行再查宠物行，避免与并发的首次创建交错；返回的是逐字段复制的 VO，不含额外统计。
   *
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils
   * @return 宠物视图；首次访问返回默认宠物（小鹰 / 1 级 / 开心 / 体力 80 / 经验 0 / 上限 100 / owl）
   */
  @Transactional(rollbackFor = Exception.class)
  public UserPetVO get(Long userId) {
    lockOwner(userId);
    UserPet pet = getOrCreatePet(userId);
    return toVO(pet);
  }

  /**
   * 喂食：体力 +10（夹逼到 0–100）、经验 +20、心情置为「开心」，随后结算升级。
   *
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils
   * @return 更新后的宠物视图
   * @throws BusinessException 用户不存在（404）或更新行数不为 1（409）时抛出并回滚事务
   */
  @Transactional(rollbackFor = Exception.class)
  public UserPetVO feed(Long userId) {
    lockOwner(userId);
    UserPet pet = getOrCreatePet(userId);
    pet.setEnergy(clamp(pet.getEnergy() + FEED_ENERGY_GAIN, ENERGY_MIN, ENERGY_MAX));
    pet.setExp(pet.getExp() + FEED_EXP_GAIN);
    pet.setMood("开心");
    handleLevelUp(pet);
    if (petMapper.updateById(pet) != 1) {
      throw new BusinessException(409, "宠物更新冲突");
    }
    return toVO(pet);
  }

  /**
   * 玩耍：体力 -15（夹逼到 0–100）、经验 +30，随后结算升级。
   *
   * <p>不检查体力是否足够，体力不足时 clamp 到 0 仍可获得经验；本方法也不改心情。
   *
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils
   * @return 更新后的宠物视图
   * @throws BusinessException 用户不存在（404）或更新行数不为 1（409）时抛出并回滚事务
   */
  @Transactional(rollbackFor = Exception.class)
  public UserPetVO play(Long userId) {
    lockOwner(userId);
    UserPet pet = getOrCreatePet(userId);
    pet.setEnergy(clamp(pet.getEnergy() - PLAY_ENERGY_COST, ENERGY_MIN, ENERGY_MAX));
    pet.setExp(pet.getExp() + PLAY_EXP_GAIN);
    handleLevelUp(pet);
    if (petMapper.updateById(pet) != 1) {
      throw new BusinessException(409, "宠物更新冲突");
    }
    return toVO(pet);
  }

  /**
   * 前端 /focus-gain 入口的专注结算：累计专注分钟与番茄数，并按每分钟 1 点经验加成。
   *
   * <p>FocusGainDTO 的 minutes / pomodoros 有 @Min(0) 校验，服务端仍把 null 折算为 0；逻辑与 grantFocus 相同，后者供
   * FocusSessionService 在专注会话完成后调用，两处不做去重。
   *
   * @param dto 专注结算入参，minutes 与 pomodoros 可为 null（按 0 处理）
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils
   * @return 更新后的宠物视图
   * @throws BusinessException 用户不存在（404）或更新行数不为 1（409）时抛出并回滚事务
   */
  @Transactional(rollbackFor = Exception.class)
  public UserPetVO focusGain(FocusGainDTO dto, Long userId) {
    lockOwner(userId);
    UserPet pet = getOrCreatePet(userId);
    int minutes = dto.getMinutes() != null ? dto.getMinutes() : 0;
    int pomodoros = dto.getPomodoros() != null ? dto.getPomodoros() : 0;
    pet.setTotalFocusMinutes(pet.getTotalFocusMinutes() + minutes);
    pet.setTotalPomodoros(pet.getTotalPomodoros() + pomodoros);
    // 每分钟 1 经验
    pet.setExp(pet.getExp() + minutes);
    handleLevelUp(pet);
    if (petMapper.updateById(pet) != 1) {
      throw new BusinessException(409, "宠物更新冲突");
    }
    return toVO(pet);
  }

  /**
   * 修改宠物名称与头像，其余字段不受请求影响。
   *
   * <p>name 由 @NotBlank / @Size(max = 50) 校验后直接保存，不裁剪空格；avatar 为
   * null、空串或全空白时保留原值，非空值直接覆盖且当前不校验标识集合。
   *
   * @param dto 宠物更新入参，只允许 name / avatar 两个字段生效
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils
   * @return 更新后的宠物视图
   * @throws BusinessException 用户不存在（404）或更新行数不为 1（409）时抛出并回滚事务
   */
  @Transactional(rollbackFor = Exception.class)
  public UserPetVO update(UserPetUpdateDTO dto, Long userId) {
    lockOwner(userId);
    UserPet pet = getOrCreatePet(userId);
    pet.setName(dto.getName());
    if (dto.getAvatar() != null && !dto.getAvatar().isBlank()) {
      pet.setAvatar(dto.getAvatar());
    }
    if (petMapper.updateById(pet) != 1) {
      throw new BusinessException(409, "宠物更新冲突");
    }
    return toVO(pet);
  }

  /**
   * 读取或新建宠物行，查询带 LIMIT 1 FOR UPDATE 且由 @TableLogic 自动过滤软删行。
   *
   * <p>默认值来自 DDL 与既有行为：小鹰 / 1 级 / 开心 / 体力 80 / 经验 0 / 上限 100 / owl / 两个累计计数为 0；调用方必须先锁 sys_user
   * 行，否则并发首次访问可能创建多只宠物。
   *
   * @return 已加锁的宠物行
   * @throws BusinessException 插入行数不为 1 时抛 409
   */
  private UserPet getOrCreatePet(Long userId) {
    UserPet pet =
        petMapper.selectOne(
            new LambdaQueryWrapper<UserPet>()
                .eq(UserPet::getUserId, userId)
                .last("LIMIT 1 FOR UPDATE"));
    if (pet != null) {
      return pet;
    }
    pet = new UserPet();
    pet.setUserId(userId);
    pet.setName("小鹰");
    pet.setLevel(1);
    pet.setMood("开心");
    pet.setEnergy(80);
    pet.setExp(0);
    pet.setMaxExp(100);
    pet.setAvatar("owl");
    pet.setTotalFocusMinutes(0);
    pet.setTotalPomodoros(0);
    if (petMapper.insert(pet) != 1) {
      throw new BusinessException(409, "宠物创建冲突");
    }
    return pet;
  }

  /**
   * 单次升级结算：exp 达到 maxExp 时 level + 1、exp 归零、maxExp 按 floor(maxExp * 1.5) 放大。
   *
   * <p>只判断一次，不是循环升级；超出阈值的经验被直接丢弃，不会结转到下一级。
   */
  private void handleLevelUp(UserPet pet) {
    if (pet.getExp() >= pet.getMaxExp()) {
      pet.setLevel(pet.getLevel() + 1);
      pet.setExp(0);
      pet.setMaxExp((int) Math.floor(pet.getMaxExp() * 1.5));
    }
  }

  /** 把数值限制在 [min, max] 区间；体力的加减都经此处理，不因不足或超额抛异常。 */
  private int clamp(int value, int min, int max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  /** 逐字段复制实体到 VO，不计算统计、不脱敏，也不把 null 折算为 0；历史数据的可空性沿用实体状态。 */
  private UserPetVO toVO(UserPet pet) {
    UserPetVO vo = new UserPetVO();
    vo.setId(pet.getId());
    vo.setUserId(pet.getUserId());
    vo.setName(pet.getName());
    vo.setLevel(pet.getLevel());
    vo.setMood(pet.getMood());
    vo.setEnergy(pet.getEnergy());
    vo.setExp(pet.getExp());
    vo.setMaxExp(pet.getMaxExp());
    vo.setAvatar(pet.getAvatar());
    vo.setTotalFocusMinutes(pet.getTotalFocusMinutes());
    vo.setTotalPomodoros(pet.getTotalPomodoros());
    vo.setCreateTime(pet.getCreateTime());
    vo.setUpdateTime(pet.getUpdateTime());
    return vo;
  }

  /**
   * 专注会话完成后的服务端结算入口，与 focusGain 使用同一套累计与升级规则。
   *
   * <p>由 FocusSessionService 在 minutes >= 1
   * 时调用，参数已是基本类型且调用方做了非空处理；同样先锁用户行再读改写，UserPetTransactionTest 的并发用例覆盖此入口。
   *
   * @param minutes 本次专注分钟数，同时按 1:1 折算经验
   * @param pomodoros 本次完成番茄数，只累计到 totalPomodoros
   * @return 更新后的宠物视图
   * @throws BusinessException 用户不存在（404）或更新行数不为 1（409）时抛出并回滚事务
   */
  @Transactional(rollbackFor = Exception.class)
  public UserPetVO grantFocus(Long userId, int minutes, int pomodoros) {
    lockOwner(userId);
    UserPet pet = getOrCreatePet(userId);
    pet.setTotalFocusMinutes(pet.getTotalFocusMinutes() + minutes);
    pet.setTotalPomodoros(pet.getTotalPomodoros() + pomodoros);
    pet.setExp(pet.getExp() + minutes);
    handleLevelUp(pet);
    if (petMapper.updateById(pet) != 1) {
      throw new BusinessException(409, "宠物更新冲突");
    }
    return toVO(pet);
  }

  /** 在调用方事务内锁定有效的 sys_user 行；取不到（用户不存在或已删除）时抛 404，阻断后续宠物创建。 */
  private void lockOwner(Long userId) {
    if (userMapper.lockActiveUserId(userId) == null) {
      throw new BusinessException(404, "用户不存在");
    }
  }
}
