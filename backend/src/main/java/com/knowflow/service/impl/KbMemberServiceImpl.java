package com.knowflow.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.KbMember;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.KbMemberMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.KbMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 知识库成员业务服务实现。
 * <p>
 * 权限模型：
 * - ADMIN（系统管理员）：所有操作放行
 * - OWNER（知识库拥有者）：成员管理、编辑文档、删除知识库、邀请
 * - EDITOR（编辑者）：编辑文档、增删文档，不可成员管理、不可删除知识库
 * - READER（阅读者）：只读
 */
@Service
@RequiredArgsConstructor
public class KbMemberServiceImpl extends ServiceImpl<KbMemberMapper, KbMember> implements KbMemberService {

    private final SysUserMapper sysUserMapper;

    private static final Set<String> VALID_ROLES = Set.of(
            KbMember.ROLE_OWNER, KbMember.ROLE_EDITOR, KbMember.ROLE_READER
    );

    @Override
    public List<KbMember> listByCategory(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        List<KbMember> list = this.list(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getCategoryId, categoryId)
                .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE));
        // Owner 排第一，然后按加入时间升序
        list.sort(Comparator
                .comparing((KbMember m) -> !KbMember.ROLE_OWNER.equals(m.getRole()))
                .thenComparing(KbMember::getCreateTime));
        return list;
    }

    @Override
    public KbMember getMember(Long categoryId, Long userId) {
        if (categoryId == null || userId == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getCategoryId, categoryId)
                .eq(KbMember::getUserId, userId)
                .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE)
                .last("LIMIT 1"));
    }

    @Override
    public boolean isOwner(Long categoryId, Long currentUserId) {
        if (isAdminUser(currentUserId)) {
            return true;
        }
        KbMember member = getMember(categoryId, currentUserId);
        return member != null && KbMember.ROLE_OWNER.equals(member.getRole());
    }

    @Override
    public boolean canEditDocs(Long categoryId, Long currentUserId) {
        if (isAdminUser(currentUserId)) {
            return true;
        }
        KbMember member = getMember(categoryId, currentUserId);
        return member != null
                && (KbMember.ROLE_OWNER.equals(member.getRole())
                    || KbMember.ROLE_EDITOR.equals(member.getRole()));
    }

    @Override
    public boolean canView(Long categoryId, Long currentUserId) {
        if (isAdminUser(currentUserId)) {
            return true;
        }
        return getMember(categoryId, currentUserId) != null;
    }

    /**
     * 判断指定用户是否为系统管理员。
     * <p>
     * 优先通过 userId 查询 sys_user.role 判断，避免依赖 {@link SecurityContextHolder}。
     * 因为 SSE 等异步场景中 {@link SecurityContextHolder} 默认使用 ThreadLocal，
     * 主线程的安全上下文不会传递到异步线程，导致 {@link SecurityUtils#isAdmin()} 失效。
     *
     * @param currentUserId 当前用户 ID（可为 null）
     * @return 如果用户角色为 ADMIN 则返回 true
     */
    private boolean isAdminUser(Long currentUserId) {
        // 兜底：主线程场景下优先使用 SecurityContext（避免额外查库）
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        // 异步线程场景：通过 userId 查询 sys_user.role
        if (currentUserId != null) {
            SysUser user = sysUserMapper.selectById(currentUserId);
            return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbMember addMember(Long categoryId, Long userId, String role, String inviteEmail) {
        if (categoryId == null) {
            throw new BusinessException("知识库ID不能为空");
        }
        if ((userId == null) && StrUtil.isBlank(inviteEmail)) {
            throw new BusinessException("请指定成员用户或邀请邮箱");
        }
        String finalRole = (role == null || role.isBlank()) ? KbMember.ROLE_READER : role;
        if (!VALID_ROLES.contains(finalRole)) {
            throw new BusinessException("非法的角色值");
        }

        // ============ 邮箱邀请（未注册用户） ============
        if (userId == null) {
            // 是否已有待接受的邀请记录（相同邮箱+知识库）
            KbMember existing = this.getOne(new LambdaQueryWrapper<KbMember>()
                    .eq(KbMember::getCategoryId, categoryId)
                    .eq(KbMember::getInviteEmail, inviteEmail)
                    .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE)
                    .isNull(KbMember::getUserId)
                    .last("LIMIT 1"));
            if (existing != null) {
                existing.setRole(finalRole);
                this.updateById(existing);
                return existing;
            }
            KbMember invite = new KbMember();
            invite.setCategoryId(categoryId);
            invite.setRole(finalRole);
            invite.setInviteEmail(inviteEmail);
            invite.setInviteCode(generateInviteCode());
            invite.setStatus(KbMember.STATUS_ACTIVE);
            invite.setJoinTime(null);
            this.save(invite);
            return invite;
        }

        // ============ 直接添加已注册用户 ============
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        KbMember existing = getMember(categoryId, userId);
        if (existing != null) {
            existing.setRole(finalRole);
            this.updateById(existing);
            return existing;
        }
        KbMember member = new KbMember();
        member.setCategoryId(categoryId);
        member.setUserId(userId);
        member.setRole(finalRole);
        member.setStatus(KbMember.STATUS_ACTIVE);
        member.setJoinTime(LocalDateTime.now());
        this.save(member);
        return member;
    }

    @Override
    public void changeRole(Long memberId, String newRole, Long operatorUserId) {
        if (memberId == null) {
            throw new BusinessException("成员ID不能为空");
        }
        if (!VALID_ROLES.contains(newRole)) {
            throw new BusinessException("非法的角色值");
        }
        KbMember target = this.getById(memberId);
        if (target == null) {
            throw new BusinessException("成员记录不存在");
        }
        // 操作人必须是该知识库 Owner 或系统 ADMIN
        if (!isOwner(target.getCategoryId(), operatorUserId)) {
            throw new BusinessException("无权修改成员角色");
        }
        // 不能把最后一个 Owner 降级（防止孤立知识库）
        if (KbMember.ROLE_OWNER.equals(target.getRole()) && !KbMember.ROLE_OWNER.equals(newRole)) {
            long ownerCount = this.count(new LambdaQueryWrapper<KbMember>()
                    .eq(KbMember::getCategoryId, target.getCategoryId())
                    .eq(KbMember::getRole, KbMember.ROLE_OWNER)
                    .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE));
            if (ownerCount <= 1) {
                throw new BusinessException("知识库至少保留一个 Owner，无法降级该成员");
            }
        }
        target.setRole(newRole);
        this.updateById(target);
    }

    @Override
    public void removeMember(Long memberId, Long operatorUserId) {
        if (memberId == null) {
            throw new BusinessException("成员ID不能为空");
        }
        KbMember target = this.getById(memberId);
        if (target == null) {
            throw new BusinessException("成员记录不存在");
        }
        if (!isOwner(target.getCategoryId(), operatorUserId)) {
            throw new BusinessException("无权移除成员");
        }
        // 禁止 Owner 移除自己（避免孤立知识库）
        if (operatorUserId != null && operatorUserId.equals(target.getUserId())
                && KbMember.ROLE_OWNER.equals(target.getRole())) {
            long ownerCount = this.count(new LambdaQueryWrapper<KbMember>()
                    .eq(KbMember::getCategoryId, target.getCategoryId())
                    .eq(KbMember::getRole, KbMember.ROLE_OWNER)
                    .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE));
            if (ownerCount <= 1) {
                throw new BusinessException("至少保留一位 Owner，不可移除自己");
            }
        }
        target.setStatus(KbMember.STATUS_REMOVED);
        this.updateById(target);
        this.removeById(target); // 触发 @TableLogic 逻辑删除
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbMember acceptInvite(String inviteCode, Long userId) {
        if (StrUtil.isBlank(inviteCode) || userId == null) {
            throw new BusinessException("邀请码和用户ID不能为空");
        }
        KbMember invite = this.getOne(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getInviteCode, inviteCode)
                .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE)
                .isNull(KbMember::getUserId)
                .last("LIMIT 1"));
        if (invite == null) {
            throw new BusinessException("邀请码无效或已过期");
        }
        // 是否已有该用户在该知识库的成员记录
        KbMember exists = getMember(invite.getCategoryId(), userId);
        if (exists != null) {
            // 清理掉邀请记录，返回现有成员
            invite.setStatus(KbMember.STATUS_REMOVED);
            this.removeById(invite);
            return exists;
        }
        invite.setUserId(userId);
        invite.setJoinTime(LocalDateTime.now());
        invite.setInviteCode(null);
        this.updateById(invite);
        return invite;
    }

    /** 生成 12 位短邀请码（大小写字母+数字）。 */
    private String generateInviteCode() {
        return IdUtil.nanoId(12);
    }
}
