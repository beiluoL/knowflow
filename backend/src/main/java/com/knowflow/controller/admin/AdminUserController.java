package com.knowflow.controller.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.UserQueryDTO;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.UserService;
import com.knowflow.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** 管理员用户管理 REST 接口（@PreAuthorize 限定 ADMIN 角色），提供用户增删改查与密码重置。 */
@Tag(name = "管理员用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "用户列表")
    @GetMapping
    public Result<PageResult<UserVO>> list(UserQueryDTO dto) {
        // 限制单页最大 100 条，防止大分页拖垮数据库
        Page<SysUser> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUsername, dto.getKeyword())
                    .or().like(SysUser::getNickname, dto.getKeyword())
                    .or().like(SysUser::getEmail, dto.getKeyword()));
        }
        if (dto.getRole() != null && !dto.getRole().isEmpty()) {
            wrapper.eq(SysUser::getRole, dto.getRole());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> result = userService.page(page, wrapper);
        PageResult<UserVO> pageResult = PageResult.of(result.convert(user -> BeanUtil.copyProperties(user, UserVO.class)));
        return Result.success(pageResult);
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        SysUser user = userService.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.success(BeanUtil.copyProperties(user, UserVO.class));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        if (StrUtil.isBlank(user.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (StrUtil.isBlank(user.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        SysUser exist = userService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername()));
        if (exist != null) {
            throw new BusinessException("用户名已存在");
        }
        user.setId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (StrUtil.isBlank(user.getRole())) {
            user.setRole("USER");
        }
        if (StrUtil.isBlank(user.getNickname())) {
            user.setNickname(user.getUsername());
        }
        user.setTotalStudyHours(user.getTotalStudyHours() != null ? user.getTotalStudyHours() : BigDecimal.ZERO);
        user.setReadDocsCount(user.getReadDocsCount() != null ? user.getReadDocsCount() : 0);
        user.setStreakDays(user.getStreakDays() != null ? user.getStreakDays() : 0);
        user.setFavoriteCount(user.getFavoriteCount() != null ? user.getFavoriteCount() : 0);
        user.setLevel(user.getLevel() != null ? user.getLevel() : 1);
        user.setExp(user.getExp() != null ? user.getExp() : 0);
        user.setEnergy(user.getEnergy() != null ? user.getEnergy() : 100);
        userService.save(user);
        return Result.success();
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        SysUser exist = userService.getById(id);
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }
        user.setId(id);
        user.setPassword(null);
        user.setDeleted(null);
        user.setCreateTime(null);
        user.setUpdateTime(null);
        userService.updateById(user);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        SysUser exist = userService.getById(id);
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }
        String newPassword = body.get("password");
        if (StrUtil.isBlank(newPassword) || newPassword.length() < 6) {
            throw new BusinessException("密码不能为空且不少于6位");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(update);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long currentUserId = getCurrentUserId();
        if (id.equals(currentUserId)) {
            throw new BusinessException("不能删除当前登录用户");
        }
        SysUser target = userService.getById(id);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException("不能删除管理员账户");
        }
        userService.removeById(id);
        return Result.success();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("未登录");
        }
        return (Long) authentication.getPrincipal();
    }
}
