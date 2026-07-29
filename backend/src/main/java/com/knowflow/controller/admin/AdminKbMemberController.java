package com.knowflow.controller.admin;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.KbMember;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.CategoryService;
import com.knowflow.service.KbMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识库成员与权限管理 REST 接口。
 * 所有接口都需登录（Spring Security 已默认拦截 /api/**）；
 * 对于「写操作」（新增成员、修改角色、移除成员），要求操作人对该知识库是 OWNER 或系统 ADMIN。
 */
@Tag(name = "管理员-知识库成员与权限")
@RestController
@RequestMapping("/api/admin/kb-members")
@RequiredArgsConstructor
public class AdminKbMemberController {

    private final KbMemberService kbMemberService;
    private final SysUserMapper sysUserMapper;
    private final CategoryService categoryService;

    // ========== DTO 定义 ==========

    @Data
    public static class AddMemberReq {
        /** 知识库（分类）ID */
        private Long categoryId;
        /** 直接添加已注册用户的 ID（与 email 二选一，优先 userId） */
        private Long userId;
        /** 通过用户名/邮箱邀请（已存在 → 自动填 userId；不存在 → 存 invite_email 生成邀请码） */
        private String keyword;
        /** 邀请邮箱（未注册用户） */
        private String email;
        /** 成员角色：OWNER / EDITOR / READER（默认 READER） */
        private String role;
    }

    @Data
    public static class ChangeRoleReq {
        /** 新角色 */
        private String role;
    }

    @Data
    public static class MemberVO {
        private Long id;
        private Long categoryId;
        private Long userId;
        private String username;
        private String nickname;
        private String email;
        private String avatar;
        private String role;
        private String inviteCode;
        private String inviteEmail;
        private Integer status;
        private String joinTime;
        private String createTime;
    }

    // ========== 查询 ==========

    @Operation(summary = "知识库成员列表（含用户信息聚合）")
    @GetMapping("/category/{categoryId}")
    public Result<List<MemberVO>> listByCategory(@PathVariable Long categoryId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 权限：成员可见（含 Owner/Editor/Reader）或系统 ADMIN
        if (!kbMemberService.canView(categoryId, currentUserId)) {
            throw new BusinessException("无权限查看该知识库成员");
        }
        List<KbMember> members = kbMemberService.listByCategory(categoryId);
        List<Long> userIds = members.stream().map(KbMember::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Map.of()
                : sysUserMapper.selectBatchIds(userIds)
                        .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        List<MemberVO> result = new ArrayList<>();
        for (KbMember m : members) {
            MemberVO vo = new MemberVO();
            vo.setId(m.getId());
            vo.setCategoryId(m.getCategoryId());
            vo.setUserId(m.getUserId());
            vo.setRole(m.getRole());
            vo.setInviteCode(m.getInviteCode());
            vo.setInviteEmail(m.getInviteEmail());
            vo.setStatus(m.getStatus());
            vo.setJoinTime(m.getJoinTime() != null ? m.getJoinTime().toString() : null);
            vo.setCreateTime(m.getCreateTime() != null ? m.getCreateTime().toString() : null);
            if (m.getUserId() != null) {
                SysUser u = userMap.get(m.getUserId());
                if (u != null) {
                    vo.setUsername(u.getUsername());
                    vo.setNickname(u.getNickname());
                    vo.setEmail(u.getEmail());
                    vo.setAvatar(u.getAvatar());
                }
            }
            result.add(vo);
        }
        return Result.success(result);
    }

    @Operation(summary = "按关键字搜索可邀请用户（用户名/昵称/邮箱，排除已加入者）")
    @GetMapping("/search-users")
    public Result<List<MemberVO>> searchUsers(
            @RequestParam Long categoryId,
            @RequestParam String keyword) {
        SecurityUtils.getCurrentUserId(); // 鉴权：确保登录
        if (StrUtil.isBlank(keyword)) {
            return Result.success(List.of());
        }
        // 已加入的 userIds
        List<Long> joined = kbMemberService.listByCategory(categoryId).stream()
                .map(KbMember::getUserId).filter(Objects::nonNull).toList();
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        q.and(w -> w.like(SysUser::getUsername, keyword)
                .or().like(SysUser::getNickname, keyword)
                .or().like(SysUser::getEmail, keyword));
        if (!joined.isEmpty()) {
            q.notIn(SysUser::getId, joined);
        }
        q.last("LIMIT 20");
        List<SysUser> users = sysUserMapper.selectList(q);
        List<MemberVO> result = new ArrayList<>();
        for (SysUser u : users) {
            MemberVO vo = new MemberVO();
            vo.setUserId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setEmail(u.getEmail());
            vo.setAvatar(u.getAvatar());
            result.add(vo);
        }
        return Result.success(result);
    }

    // ========== 新增/邀请 ==========

    @Operation(summary = "添加成员 / 发送邀请（支持已注册用户或邮箱邀请未注册用户）")
    @PostMapping
    public Result<MemberVO> add(@RequestBody AddMemberReq req) {
        Long operator = SecurityUtils.getCurrentUserId();
        if (req.getCategoryId() == null) {
            throw new BusinessException("请指定知识库");
        }
        if (!kbMemberService.isOwner(req.getCategoryId(), operator)) {
            throw new BusinessException("无权限管理该知识库成员");
        }
        Long targetUserId = req.getUserId();
        String inviteEmail = req.getEmail();
        // keyword 优先级：按用户名/邮箱搜索用户
        if (targetUserId == null && StrUtil.isNotBlank(req.getKeyword())) {
            SysUser hit = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .and(w -> w.eq(SysUser::getUsername, req.getKeyword())
                            .or().eq(SysUser::getEmail, req.getKeyword()))
                    .last("LIMIT 1"));
            if (hit != null) {
                targetUserId = hit.getId();
            } else if (req.getKeyword().contains("@")) {
                inviteEmail = req.getKeyword();
            }
        }
        KbMember member = kbMemberService.addMember(
                req.getCategoryId(), targetUserId, req.getRole(), inviteEmail);
        // 聚合返回
        MemberVO vo = toMemberVO(member);
        return Result.success(vo);
    }

    // ========== 修改/删除 ==========

    @Operation(summary = "变更成员角色")
    @PutMapping("/{memberId}/role")
    public Result<Void> changeRole(@PathVariable Long memberId, @RequestBody ChangeRoleReq req) {
        Long operator = SecurityUtils.getCurrentUserId();
        kbMemberService.changeRole(memberId, req.getRole(), operator);
        return Result.success();
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/{memberId}")
    public Result<Void> remove(@PathVariable Long memberId) {
        Long operator = SecurityUtils.getCurrentUserId();
        kbMemberService.removeMember(memberId, operator);
        return Result.success();
    }

    // ========== 辅助 ==========

    private MemberVO toMemberVO(KbMember m) {
        MemberVO vo = new MemberVO();
        vo.setId(m.getId());
        vo.setCategoryId(m.getCategoryId());
        vo.setUserId(m.getUserId());
        vo.setRole(m.getRole());
        vo.setInviteCode(m.getInviteCode());
        vo.setInviteEmail(m.getInviteEmail());
        vo.setStatus(m.getStatus());
        vo.setJoinTime(m.getJoinTime() != null ? m.getJoinTime().toString() : null);
        vo.setCreateTime(m.getCreateTime() != null ? m.getCreateTime().toString() : null);
        if (m.getUserId() != null) {
            SysUser u = sysUserMapper.selectById(m.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setNickname(u.getNickname());
                vo.setEmail(u.getEmail());
                vo.setAvatar(u.getAvatar());
            }
        }
        return vo;
    }

    /**
     * 内部工具：创建知识库时自动把创建者设为 OWNER。
     * 为 CategoryService 调用提供便捷入口（通过 Spring 依赖注入在 CategoryServiceImpl 中调用此 Controller bean 调用）。
     * TODO: 后续可提升到 Service 层复用，当前为避免循环依赖直接在 Controller 公开。
     */
    public void ensureOwnerForCategory(Long categoryId, Long ownerId) {
        kbMemberService.addMember(categoryId, ownerId, KbMember.ROLE_OWNER, null);
    }
}
