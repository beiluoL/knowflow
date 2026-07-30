package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.dto.GroupInviteDTO;
import com.knowflow.dto.GroupMessageSendDTO;
import com.knowflow.dto.StudyGroupCreateDTO;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.service.StudyGroupService;
import com.knowflow.vo.GroupMessageVO;
import com.knowflow.vo.StudyGroupMemberVO;
import com.knowflow.vo.StudyGroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习小组服务实现
 */
@Service
@RequiredArgsConstructor
public class StudyGroupServiceImpl implements StudyGroupService {

    private final StudyGroupMapper studyGroupMapper;
    private final StudyGroupMemberMapper studyGroupMemberMapper;
    private final StudyGroupMessageMapper studyGroupMessageMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<StudyGroupVO> getMyGroups(Long userId) {
        // 查询用户加入的小组
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        List<StudyGroupMember> memberships = studyGroupMemberMapper.selectList(memberWrapper);

        if (memberships.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取小组ID列表
        List<Long> groupIds = memberships.stream()
                .map(StudyGroupMember::getGroupId)
                .collect(Collectors.toList());

        // 查询小组信息
        LambdaQueryWrapper<StudyGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(StudyGroup::getId, groupIds);
        List<StudyGroup> groups = studyGroupMapper.selectList(groupWrapper);

        // 构建用户角色映射
        Map<Long, String> roleMap = memberships.stream()
                .collect(Collectors.toMap(StudyGroupMember::getGroupId, StudyGroupMember::getRole));

        // 查询创建者信息
        Set<Long> ownerIds = groups.stream().map(StudyGroup::getOwnerId).collect(Collectors.toSet());
        Map<Long, SysUser> ownerMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getId, ownerIds);
            sysUserMapper.selectList(userWrapper).forEach(u -> ownerMap.put(u.getId(), u));
        }

        return groups.stream().map(g -> {
            StudyGroupVO vo = new StudyGroupVO();
            vo.setId(g.getId());
            vo.setName(g.getName());
            vo.setDescription(g.getDescription());
            vo.setIcon(g.getIcon());
            vo.setColor(g.getColor());
            vo.setType(g.getType());
            vo.setOwnerId(g.getOwnerId());
            vo.setMemberCount(g.getMemberCount());
            vo.setAnnouncement(g.getAnnouncement());
            vo.setLearningPlanId(g.getLearningPlanId());
            vo.setCreateTime(g.getCreateTime());
            vo.setUserRole(roleMap.get(g.getId()));
            vo.setUnreadCount(getUnreadCount(g.getId(), userId));

            SysUser owner = ownerMap.get(g.getOwnerId());
            if (owner != null) {
                vo.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<StudyGroupVO> getRecommendGroups(Long userId, int limit) {
        // 查询公开小组，排除用户已加入的
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        List<Long> joinedGroupIds = studyGroupMemberMapper.selectList(memberWrapper).stream()
                .map(StudyGroupMember::getGroupId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<StudyGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.eq(StudyGroup::getType, "PUBLIC");
        if (!joinedGroupIds.isEmpty()) {
            groupWrapper.notIn(StudyGroup::getId, joinedGroupIds);
        }
        groupWrapper.orderByDesc(StudyGroup::getMemberCount);
        groupWrapper.last("LIMIT " + limit);

        List<StudyGroup> groups = studyGroupMapper.selectList(groupWrapper);

        return groups.stream().map(g -> {
            StudyGroupVO vo = new StudyGroupVO();
            vo.setId(g.getId());
            vo.setName(g.getName());
            vo.setDescription(g.getDescription());
            vo.setIcon(g.getIcon());
            vo.setColor(g.getColor());
            vo.setType(g.getType());
            vo.setMemberCount(g.getMemberCount());
            vo.setCreateTime(g.getCreateTime());

            SysUser owner = sysUserMapper.selectById(g.getOwnerId());
            if (owner != null) {
                vo.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public StudyGroupVO getGroupDetail(Long groupId, Long userId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }

        // 检查用户是否有权限查看（私有小组需要是成员）
        if ("PRIVATE".equals(group.getType())) {
            LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
            memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
            memberWrapper.eq(StudyGroupMember::getUserId, userId);
            if (studyGroupMemberMapper.selectCount(memberWrapper) == 0) {
                throw new RuntimeException("无权访问该小组");
            }
        }

        StudyGroupVO vo = new StudyGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setIcon(group.getIcon());
        vo.setColor(group.getColor());
        vo.setType(group.getType());
        vo.setOwnerId(group.getOwnerId());
        vo.setMemberCount(group.getMemberCount());
        vo.setAnnouncement(group.getAnnouncement());
        vo.setLearningPlanId(group.getLearningPlanId());
        vo.setCreateTime(group.getCreateTime());

        // 获取用户角色
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        StudyGroupMember membership = studyGroupMemberMapper.selectOne(memberWrapper);
        if (membership != null) {
            vo.setUserRole(membership.getRole());
        }

        SysUser owner = sysUserMapper.selectById(group.getOwnerId());
        if (owner != null) {
            vo.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
        }
        return vo;
    }

    @Override
    @Transactional
    public StudyGroupVO createGroup(StudyGroupCreateDTO dto, Long userId) {
        StudyGroup group = new StudyGroup();
        group.setName(dto.getName());
        group.setDescription(dto.getDescription());
        group.setIcon(dto.getIcon());
        group.setColor(dto.getColor());
        group.setType(dto.getType() != null ? dto.getType() : "PUBLIC");
        group.setOwnerId(userId);
        group.setMemberCount(1);
        group.setLearningPlanId(dto.getLearningPlanId());
        studyGroupMapper.insert(group);

        // 创建者加入小组
        StudyGroupMember member = new StudyGroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole("OWNER");
        studyGroupMemberMapper.insert(member);

        StudyGroupVO vo = new StudyGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setIcon(group.getIcon());
        vo.setColor(group.getColor());
        vo.setType(group.getType());
        vo.setOwnerId(group.getOwnerId());
        vo.setMemberCount(group.getMemberCount());
        vo.setCreateTime(group.getCreateTime());
        vo.setUserRole("OWNER");

        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            vo.setOwnerName(user.getNickname() != null ? user.getNickname() : user.getUsername());
        }
        return vo;
    }

    @Override
    @Transactional
    public StudyGroupVO updateGroup(Long groupId, StudyGroupCreateDTO dto, Long userId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }

        // 检查权限（只有创建者和管理员可以修改）
        checkAdminPermission(groupId, userId);

        if (dto.getName() != null) group.setName(dto.getName());
        if (dto.getDescription() != null) group.setDescription(dto.getDescription());
        if (dto.getIcon() != null) group.setIcon(dto.getIcon());
        if (dto.getColor() != null) group.setColor(dto.getColor());
        if (dto.getType() != null) group.setType(dto.getType());
        if (dto.getLearningPlanId() != null) group.setLearningPlanId(dto.getLearningPlanId());

        studyGroupMapper.updateById(group);
        return getGroupDetail(groupId, userId);
    }

    @Override
    @Transactional
    public void deleteGroup(Long groupId, Long userId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }

        // 只有创建者可以删除小组
        if (!group.getOwnerId().equals(userId)) {
            throw new RuntimeException("只有创建者可以删除小组");
        }

        // 删除所有成员
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        studyGroupMemberMapper.delete(memberWrapper);

        // 删除所有消息
        LambdaQueryWrapper<StudyGroupMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(StudyGroupMessage::getGroupId, groupId);
        studyGroupMessageMapper.delete(messageWrapper);

        // 删除小组
        studyGroupMapper.deleteById(groupId);
    }

    @Override
    public List<StudyGroupMemberVO> getGroupMembers(Long groupId, Long userId) {
        // 检查权限
        checkMemberPermission(groupId, userId);

        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        List<StudyGroupMember> members = studyGroupMemberMapper.selectList(memberWrapper);

        if (members.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取用户信息
        List<Long> userIds = members.stream().map(StudyGroupMember::getUserId).collect(Collectors.toList());
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(SysUser::getId, userIds);
        Map<Long, SysUser> userMap = new HashMap<>();
        sysUserMapper.selectList(userWrapper).forEach(u -> userMap.put(u.getId(), u));

        // 获取邀请人信息
        Set<Long> inviterIds = members.stream()
                .map(StudyGroupMember::getInvitedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> inviterNameMap = new HashMap<>();
        if (!inviterIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> inviterWrapper = new LambdaQueryWrapper<>();
            inviterWrapper.in(SysUser::getId, inviterIds);
            sysUserMapper.selectList(inviterWrapper).forEach(u -> 
                inviterNameMap.put(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername()));
        }

        return members.stream().map(m -> {
            StudyGroupMemberVO vo = new StudyGroupMemberVO();
            vo.setId(m.getId());
            vo.setGroupId(m.getGroupId());
            vo.setUserId(m.getUserId());
            vo.setRole(m.getRole());
            vo.setJoinTime(m.getCreateTime());

            SysUser user = userMap.get(m.getUserId());
            if (user != null) {
                vo.setUserName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                vo.setUserEmail(user.getEmail());
                vo.setUserAvatar(user.getAvatar());
            }

            if (m.getInvitedBy() != null) {
                vo.setInvitedByName(inviterNameMap.get(m.getInvitedBy()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void inviteMember(GroupInviteDTO dto, Long inviterId) {
        // 检查权限
        checkMemberPermission(dto.getGroupId(), inviterId);

        // 根据邮箱或用户ID查找用户
        SysUser user = null;
        if (dto.getEmail() != null) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(SysUser::getEmail, dto.getEmail());
            user = sysUserMapper.selectOne(userWrapper);
        } else if (dto.getUserId() != null) {
            user = sysUserMapper.selectById(dto.getUserId());
        }

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 检查是否已是成员
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, dto.getGroupId());
        memberWrapper.eq(StudyGroupMember::getUserId, user.getId());
        if (studyGroupMemberMapper.selectCount(memberWrapper) > 0) {
            throw new RuntimeException("用户已是小组成员");
        }

        // 添加成员
        StudyGroupMember member = new StudyGroupMember();
        member.setGroupId(dto.getGroupId());
        member.setUserId(user.getId());
        member.setRole(dto.getRole() != null ? dto.getRole() : "MEMBER");
        member.setInvitedBy(inviterId);
        studyGroupMemberMapper.insert(member);

        // 更新成员数量
        updateMemberCount(dto.getGroupId());
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, Long userId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }

        if (!"PUBLIC".equals(group.getType())) {
            throw new RuntimeException("私有小组需要邀请才能加入");
        }

        // 检查是否已是成员
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        if (studyGroupMemberMapper.selectCount(memberWrapper) > 0) {
            throw new RuntimeException("已是小组成员");
        }

        // 添加成员
        StudyGroupMember member = new StudyGroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole("MEMBER");
        studyGroupMemberMapper.insert(member);

        // 更新成员数量
        updateMemberCount(groupId);
    }

    @Override
    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        StudyGroupMember member = studyGroupMemberMapper.selectOne(memberWrapper);

        if (member == null) {
            throw new RuntimeException("不是小组成员");
        }

        if ("OWNER".equals(member.getRole())) {
            throw new RuntimeException("创建者不能退出小组，请先转让或删除小组");
        }

        studyGroupMemberMapper.deleteById(member.getId());
        updateMemberCount(groupId);
    }

    @Override
    @Transactional
    public void removeMember(Long groupId, Long memberId, Long operatorId) {
        checkAdminPermission(groupId, operatorId);

        StudyGroupMember member = studyGroupMemberMapper.selectById(memberId);
        if (member == null || !member.getGroupId().equals(groupId)) {
            throw new RuntimeException("成员不存在");
        }

        if ("OWNER".equals(member.getRole())) {
            throw new RuntimeException("不能移除创建者");
        }

        studyGroupMemberMapper.deleteById(memberId);
        updateMemberCount(groupId);
    }

    @Override
    @Transactional
    public void updateMemberRole(Long groupId, Long memberId, String role, Long operatorId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null || !group.getOwnerId().equals(operatorId)) {
            throw new RuntimeException("只有创建者可以修改成员角色");
        }

        StudyGroupMember member = studyGroupMemberMapper.selectById(memberId);
        if (member == null || !member.getGroupId().equals(groupId)) {
            throw new RuntimeException("成员不存在");
        }

        member.setRole(role);
        studyGroupMemberMapper.updateById(member);
    }

    @Override
    public Page<GroupMessageVO> getMessages(Long groupId, Long userId, int page, int size) {
        checkMemberPermission(groupId, userId);

        LambdaQueryWrapper<StudyGroupMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroupMessage::getGroupId, groupId);
        wrapper.orderByDesc(StudyGroupMessage::getCreateTime);

        Page<StudyGroupMessage> messagePage = new Page<>(page, size);
        studyGroupMessageMapper.selectPage(messagePage, wrapper);

        // 获取发送者信息
        Set<Long> senderIds = messagePage.getRecords().stream()
                .map(StudyGroupMessage::getSenderId)
                .collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getId, senderIds);
            sysUserMapper.selectList(userWrapper).forEach(u -> senderMap.put(u.getId(), u));
        }

        // 其他成员的已读游标（取最大值），用于判断"我发出的消息是否被他人读过"
        Long otherReadCursor = getOtherReadCursor(groupId, userId);

        Page<GroupMessageVO> voPage = new Page<>(page, size, messagePage.getTotal());
        voPage.setRecords(messagePage.getRecords().stream().map(m -> {
            GroupMessageVO vo = convertToMessageVO(m, senderMap);
            boolean isMine = m.getSenderId().equals(userId);
            vo.setIsMine(isMine);
            // 已读仅对我发出的消息有意义
            vo.setRead(isMine && otherReadCursor != null && m.getId() <= otherReadCursor);
            return vo;
        }).collect(Collectors.toList()));

        return voPage;
    }

    @Override
    @Transactional
    public GroupMessageVO sendMessage(GroupMessageSendDTO dto, Long userId) {
        checkMemberPermission(dto.getGroupId(), userId);

        StudyGroupMessage message = new StudyGroupMessage();
        message.setGroupId(dto.getGroupId());
        message.setSenderId(userId);
        message.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : "TEXT");
        message.setContent(dto.getContent());
        message.setFileUrl(dto.getFileUrl());
        message.setFileName(dto.getFileName());
        message.setFileSize(dto.getFileSize());
        message.setCodeLanguage(dto.getCodeLanguage());
        message.setRecalled(0);

        if (dto.getMentionUserIds() != null && !dto.getMentionUserIds().isEmpty()) {
            message.setMentionUserIds(dto.getMentionUserIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }

        studyGroupMessageMapper.insert(message);

        SysUser sender = sysUserMapper.selectById(userId);
        Map<Long, SysUser> senderMap = new HashMap<>();
        if (sender != null) {
            senderMap.put(userId, sender);
        }

        GroupMessageVO vo = convertToMessageVO(message, senderMap);
        vo.setIsMine(true);
        return vo;
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId, Long userId) {
        StudyGroupMessage message = studyGroupMessageMapper.selectById(messageId);
        if (message == null) {
            throw new RuntimeException("消息不存在");
        }

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("只能撤回自己的消息");
        }

        message.setRecalled(1);
        studyGroupMessageMapper.updateById(message);
    }

    @Override
    @Transactional
    public Long markAsRead(Long groupId, Long userId) {
        LambdaQueryWrapper<StudyGroupMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroupMessage::getGroupId, groupId);
        wrapper.orderByDesc(StudyGroupMessage::getId);
        wrapper.last("LIMIT 1");
        StudyGroupMessage lastMessage = studyGroupMessageMapper.selectOne(wrapper);

        if (lastMessage != null) {
            LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
            memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
            memberWrapper.eq(StudyGroupMember::getUserId, userId);
            StudyGroupMember member = studyGroupMemberMapper.selectOne(memberWrapper);
            if (member != null) {
                member.setLastReadMessageId(lastMessage.getId());
                studyGroupMemberMapper.updateById(member);
            }
            return lastMessage.getId();
        }
        return null;
    }

    /** 取除自己外其他成员的最大 last_read_message_id，用于群聊"已读"展示 */
    private Long getOtherReadCursor(Long groupId, Long userId) {
        LambdaQueryWrapper<StudyGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroupMember::getGroupId, groupId);
        wrapper.ne(StudyGroupMember::getUserId, userId);
        wrapper.isNotNull(StudyGroupMember::getLastReadMessageId);
        List<StudyGroupMember> members = studyGroupMemberMapper.selectList(wrapper);
        return members.stream()
                .map(StudyGroupMember::getLastReadMessageId)
                .max(Long::compareTo)
                .orElse(null);
    }

    @Override
    public int getUnreadCount(Long groupId, Long userId) {
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        StudyGroupMember member = studyGroupMemberMapper.selectOne(memberWrapper);

        if (member == null || member.getLastReadMessageId() == null) {
            LambdaQueryWrapper<StudyGroupMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StudyGroupMessage::getGroupId, groupId);
            return Math.toIntExact(studyGroupMessageMapper.selectCount(wrapper));
        }

        LambdaQueryWrapper<StudyGroupMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroupMessage::getGroupId, groupId);
        wrapper.gt(StudyGroupMessage::getId, member.getLastReadMessageId());
        return Math.toIntExact(studyGroupMessageMapper.selectCount(wrapper));
    }

    @Override
    @Transactional
    public void updateAnnouncement(Long groupId, String announcement, Long userId) {
        checkAdminPermission(groupId, userId);

        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }
        group.setAnnouncement(announcement);
        studyGroupMapper.updateById(group);
    }

    @Override
    @Transactional
    public void linkLearningPlan(Long groupId, Long planId, Long userId) {
        checkAdminPermission(groupId, userId);

        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }
        group.setLearningPlanId(planId);
        studyGroupMapper.updateById(group);
    }

    private void checkMemberPermission(Long groupId, Long userId) {
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        if (studyGroupMemberMapper.selectCount(memberWrapper) == 0) {
            throw new RuntimeException("不是小组成员");
        }
    }

    private void checkAdminPermission(Long groupId, Long userId) {
        LambdaQueryWrapper<StudyGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(StudyGroupMember::getGroupId, groupId);
        memberWrapper.eq(StudyGroupMember::getUserId, userId);
        StudyGroupMember member = studyGroupMemberMapper.selectOne(memberWrapper);

        if (member == null || (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole()))) {
            throw new RuntimeException("没有管理权限");
        }
    }

    private void updateMemberCount(Long groupId) {
        LambdaQueryWrapper<StudyGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroupMember::getGroupId, groupId);
        long count = studyGroupMemberMapper.selectCount(wrapper);

        StudyGroup group = new StudyGroup();
        group.setId(groupId);
        group.setMemberCount((int) count);
        studyGroupMapper.updateById(group);
    }

    private GroupMessageVO convertToMessageVO(StudyGroupMessage message, Map<Long, SysUser> senderMap) {
        GroupMessageVO vo = new GroupMessageVO();
        vo.setId(message.getId());
        vo.setGroupId(message.getGroupId());
        vo.setSenderId(message.getSenderId());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setFileUrl(message.getFileUrl());
        vo.setFileName(message.getFileName());
        vo.setFileSize(message.getFileSize());
        vo.setCodeLanguage(message.getCodeLanguage());
        vo.setCreateTime(message.getCreateTime());
        vo.setRecalled(message.getRecalled() == 1);

        SysUser sender = senderMap.get(message.getSenderId());
        if (sender != null) {
            vo.setSenderName(sender.getNickname() != null ? sender.getNickname() : sender.getUsername());
            vo.setSenderAvatar(sender.getAvatar());
        }

        // 解析@提及用户
        if (message.getMentionUserIds() != null && !message.getMentionUserIds().isEmpty()) {
            List<GroupMessageVO.MentionedUser> mentionUsers = Arrays.stream(message.getMentionUserIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .map(uid -> {
                        GroupMessageVO.MentionedUser mu = new GroupMessageVO.MentionedUser();
                        mu.setId(uid);
                        SysUser u = senderMap.get(uid);
                        if (u != null) {
                            mu.setName(u.getNickname() != null ? u.getNickname() : u.getUsername());
                        }
                        return mu;
                    })
                    .collect(Collectors.toList());
            vo.setMentionUsers(mentionUsers);
        }

        return vo;
    }
}