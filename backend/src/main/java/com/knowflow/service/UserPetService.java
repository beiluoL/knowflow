package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.dto.FocusGainDTO;
import com.knowflow.dto.UserPetUpdateDTO;
import com.knowflow.entity.UserPet;
import com.knowflow.mapper.UserPetMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.vo.UserPetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes pet creation and read-modify-write operations on the owning user row. */
@Service
@RequiredArgsConstructor
public class UserPetService {
    private final UserPetMapper petMapper;
    private final SysUserMapper userMapper;

    /** 体力值上限 */
    private static final int ENERGY_MAX = 100;
    /** 体力值下限 */
    private static final int ENERGY_MIN = 0;
    /** 喂食增加体力 */
    private static final int FEED_ENERGY_GAIN = 10;
    /** 喂食增加经验 */
    private static final int FEED_EXP_GAIN = 20;
    /** 玩耍消耗体力 */
    private static final int PLAY_ENERGY_COST = 15;
    /** 玩耍增加经验 */
    private static final int PLAY_EXP_GAIN = 30;

    /** 获取当前用户宠物，不存在则自动创建默认宠物 */
    @Transactional(rollbackFor = Exception.class)
    public UserPetVO get(Long userId) {
        lockOwner(userId);
        UserPet pet = getOrCreatePet(userId);
        return toVO(pet);
    }

    /** 喂食：energy+10（上限100）、exp+20、心情变为「开心」，处理升级 */
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

    /** 玩耍：energy-15（下限0）、exp+30，处理升级 */
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

    /** 专注完成获得经验：累计专注分钟与番茄数，每分钟 1 经验，处理升级 */
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

    /** 更新宠物名称/头像（仅允许改 name 和 avatar） */
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

    /** 获取或创建当前用户的宠物（默认：小鹰/1级/开心/体力80/经验0/上限100/头像owl） */
    private UserPet getOrCreatePet(Long userId) {
        UserPet pet = petMapper.selectOne(new LambdaQueryWrapper<UserPet>()
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

    /** 升级处理：exp>=maxExp 则 level+1、exp 归零、maxExp=floor(maxExp*1.5) */
    private void handleLevelUp(UserPet pet) {
        if (pet.getExp() >= pet.getMaxExp()) {
            pet.setLevel(pet.getLevel() + 1);
            pet.setExp(0);
            pet.setMaxExp((int) Math.floor(pet.getMaxExp() * 1.5));
        }
    }

    /** 将数值限制在 [min, max] 区间 */
    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

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
    /** Adds completed focus minutes and pomodoros within the caller's transaction. */
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

    private void lockOwner(Long userId) {
        if (userMapper.lockActiveUserId(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }
    }

}
