package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.LoginDTO;
import com.knowflow.dto.RegisterDTO;
import com.knowflow.dto.UpdateProfileDTO;
import com.knowflow.entity.DocFavorite;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningTask;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocFavoriteMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningTaskMapper;
import com.knowflow.mapper.LearningUserPathMapper;
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
import java.util.List;
import java.util.stream.Collectors;

/** 用户业务服务实现。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final DocReadProgressMapper readProgressMapper;
    private final DocFavoriteMapper favoriteMapper;
    private final LearningUserPathMapper userPathMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final LearningTaskMapper taskMapper;

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
            throw new BusinessException(404, "用户不存在");
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    @Override
    public UserStatsVO getUserStats(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 所有统计均基于真实业务表实时聚合，避免存储字段漂移导致「死数据」
        int readDocsCount = Math.toIntExact(readProgressMapper.selectCount(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId)));
        int favoriteCount = Math.toIntExact(favoriteMapper.selectCount(
                new LambdaQueryWrapper<DocFavorite>().eq(DocFavorite::getUserId, userId)));
        int completedPaths = Math.toIntExact(userPathMapper.selectCount(
                new LambdaQueryWrapper<LearningUserPath>()
                        .eq(LearningUserPath::getUserId, userId)
                        .ge(LearningUserPath::getProgress, new BigDecimal(100))));

        List<LearningUserPath> enrolled = userPathMapper.selectList(
                new LambdaQueryWrapper<LearningUserPath>().eq(LearningUserPath::getUserId, userId));
        long totalFlashcards;
        if (enrolled.isEmpty()) {
            totalFlashcards = 0;
        } else {
            List<Long> pathIds = enrolled.stream().map(LearningUserPath::getPathId).collect(Collectors.toList());
            totalFlashcards = flashcardMapper.selectCount(
                    new LambdaQueryWrapper<LearningFlashcard>().in(LearningFlashcard::getPathId, pathIds));
        }

        // 等级与经验由真实学习行为派生：阅读 +10/篇、完成路径 +50、收藏 +5
        int exp = readDocsCount * 10 + completedPaths * 50 + favoriteCount * 5;
        int level = exp / 100 + 1;
        // 精力值：待完成任务越多消耗越多，下限 0 上限 100
        long pendingTasks = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .eq(LearningTask::getStatus, 0));
        int energy = Math.max(0, Math.min(100, 100 - (int) pendingTasks * 10));

        UserStatsVO stats = new UserStatsVO();
        stats.setUserId(userId);
        stats.setTotalStudyHours(user.getTotalStudyHours());
        stats.setReadDocsCount(readDocsCount);
        stats.setStreakDays(user.getStreakDays());
        stats.setFavoriteCount(favoriteCount);
        stats.setLevel(level);
        stats.setExp(exp);
        stats.setEnergy(energy);
        stats.setCompletedPaths(completedPaths);
        stats.setTotalFlashcards((int) totalFlashcards);
        return stats;
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
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
