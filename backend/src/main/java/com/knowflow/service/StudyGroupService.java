package com.knowflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.dto.GroupInviteDTO;
import com.knowflow.dto.GroupMessageSendDTO;
import com.knowflow.dto.StudyGroupCreateDTO;
import com.knowflow.vo.GroupMessageVO;
import com.knowflow.vo.StudyGroupMemberVO;
import com.knowflow.vo.StudyGroupVO;

import java.util.List;

/**
 * 学习小组服务接口
 */
public interface StudyGroupService {

    /**
     * 获取我加入的小组列表
     */
    List<StudyGroupVO> getMyGroups(Long userId);

    /**
     * 获取推荐小组列表（公开小组）
     */
    List<StudyGroupVO> getRecommendGroups(Long userId, int limit);

    /**
     * 获取小组详情
     */
    StudyGroupVO getGroupDetail(Long groupId, Long userId);

    /**
     * 创建小组
     */
    StudyGroupVO createGroup(StudyGroupCreateDTO dto, Long userId);

    /**
     * 更新小组信息
     */
    StudyGroupVO updateGroup(Long groupId, StudyGroupCreateDTO dto, Long userId);

    /**
     * 删除小组
     */
    void deleteGroup(Long groupId, Long userId);

    /**
     * 获取小组成员列表
     */
    List<StudyGroupMemberVO> getGroupMembers(Long groupId, Long userId);

    /**
     * 邀请成员加入小组
     */
    void inviteMember(GroupInviteDTO dto, Long inviterId);

    /**
     * 加入公开小组
     */
    void joinGroup(Long groupId, Long userId);

    /**
     * 退出小组
     */
    void leaveGroup(Long groupId, Long userId);

    /**
     * 移除成员（仅管理员和创建者可操作）
     */
    void removeMember(Long groupId, Long memberId, Long operatorId);

    /**
     * 更新成员角色
     */
    void updateMemberRole(Long groupId, Long memberId, String role, Long operatorId);

    /**
     * 获取小组消息列表
     */
    Page<GroupMessageVO> getMessages(Long groupId, Long userId, int page, int size);

    /**
     * 发送消息
     */
    GroupMessageVO sendMessage(GroupMessageSendDTO dto, Long userId);

    /**
     * 撤回消息
     */
    void recallMessage(Long messageId, Long userId);

    /**
     * 标记消息已读
     * @return 已读到的最后消息 ID（游标），用于实时广播已读回执
     */
    Long markAsRead(Long groupId, Long userId);

    /**
     * 获取未读消息数
     */
    int getUnreadCount(Long groupId, Long userId);

    /**
     * 更新小组公告
     */
    void updateAnnouncement(Long groupId, String announcement, Long userId);

    /**
     * 关联学习计划
     */
    void linkLearningPlan(Long groupId, Long planId, Long userId);
}