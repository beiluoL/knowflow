package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.KbMember;

import java.util.List;

/**
 * 知识库成员服务接口。
 * <p>
 * 职责：
 * 1. 成员的增删改查（查询指定知识库下成员列表 / 成员详情）
 * 2. 权限判断（对指定知识库：是否 Owner / Editor / Reader 可见）
 * 3. 邀请（按已存在用户 ID、或按邮箱邀请未注册用户）
 * 4. 成员角色变更（提升/降级）
 */
public interface KbMemberService extends IService<KbMember> {

    /**
     * 获取指定知识库下所有成员。
     *
     * @param categoryId 知识库（分类）ID
     * @return 成员列表，按创建时间升序（Owner 在前）
     */
    List<KbMember> listByCategory(Long categoryId);

    /**
     * 查询某用户在指定知识库中的成员记录。
     *
     * @param categoryId 知识库ID
     * @param userId     用户ID
     * @return 成员实体；null 表示未加入
     */
    KbMember getMember(Long categoryId, Long userId);

    /**
     * 当前用户是否对该知识库拥有 Owner 权限（含系统 ADMIN）。
     */
    boolean isOwner(Long categoryId, Long currentUserId);

    /**
     * 当前用户是否对该知识库拥有「可编辑文档」权限（Owner/Editor，或系统 ADMIN）。
     */
    boolean canEditDocs(Long categoryId, Long currentUserId);

    /**
     * 当前用户是否对该知识库可查看（任一成员角色 + 系统 ADMIN）。
     */
    boolean canView(Long categoryId, Long currentUserId);

    /**
     * 添加/邀请成员：
     * <ul>
     *     <li>userId 不为空：直接按已注册用户添加</li>
     *     <li>userId 为空且 inviteEmail 不为空：生成邀请码保存邀请记录（前端可发送邮件）</li>
     * </ul>
     *
     * @return 新建或已存在的成员记录
     */
    KbMember addMember(Long categoryId, Long userId, String role, String inviteEmail);

    /**
     * 变更成员角色（仅 Owner / 系统 ADMIN 可调用）。
     */
    void changeRole(Long memberId, String newRole, Long operatorUserId);

    /**
     * 移除成员（逻辑删除 + status=0，仅 Owner / 系统 ADMIN 可调用，且不可移除自己）。
     */
    void removeMember(Long memberId, Long operatorUserId);

    /**
     * 凭邀请码接受邀请，关联到新的 userId（注册后首次登录时调用）。
     */
    KbMember acceptInvite(String inviteCode, Long userId);
}
