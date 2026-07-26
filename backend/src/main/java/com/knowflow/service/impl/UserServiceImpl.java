package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.LoginDTO;
import com.knowflow.dto.RegisterDTO;
import com.knowflow.dto.UpdateProfileDTO;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.UserService;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.LoginVO;
import com.knowflow.vo.UserStatsVO;
import com.knowflow.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 用户业务服务实现。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    /** 登录校验：用户名或密码错误统一返回 401，以防止用户名枚举。 */
    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        // F-04 修复：登录失败返回 HTTP 401；统一提示语防止用户名枚举
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(BeanUtil.copyProperties(user, UserVO.class));
        return vo;
    }

    @Override
    public LoginVO register(RegisterDTO dto) {
        SysUser existUser = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }
        if (StrUtil.isNotBlank(dto.getEmail())) {
            SysUser existEmail = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, dto.getEmail()));
            if (existEmail != null) {
                throw new BusinessException("邮箱已被注册");
            }
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StrUtil.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole("USER");
        user.setTotalStudyHours(BigDecimal.ZERO);
        user.setReadDocsCount(0);
        user.setStreakDays(0);
        user.setFavoriteCount(0);
        user.setLevel(1);
        user.setExp(0);
        user.setEnergy(100);
        this.save(user);
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(BeanUtil.copyProperties(user, UserVO.class));
        return vo;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    @Override
    public UserStatsVO getUserStats(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserStatsVO stats = new UserStatsVO();
        stats.setUserId(userId);
        stats.setTotalStudyHours(user.getTotalStudyHours());
        stats.setReadDocsCount(user.getReadDocsCount());
        stats.setStreakDays(user.getStreakDays());
        stats.setFavoriteCount(user.getFavoriteCount());
        stats.setLevel(user.getLevel());
        stats.setExp(user.getExp());
        stats.setEnergy(user.getEnergy());
        stats.setCompletedPaths(0);
        stats.setTotalFlashcards(0);
        return stats;
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            SysUser existEmail = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, dto.getEmail())
                    .ne(SysUser::getId, userId));
            if (existEmail != null) {
                throw new BusinessException("邮箱已被使用");
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        this.updateById(user);
        return BeanUtil.copyProperties(user, UserVO.class);
    }
}
