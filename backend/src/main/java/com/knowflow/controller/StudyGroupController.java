package com.knowflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.GroupInviteDTO;
import com.knowflow.dto.GroupMessageSendDTO;
import com.knowflow.dto.StudyGroupCreateDTO;
import com.knowflow.entity.StudyGroupMessage;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.StudyGroupMessageMapper;
import com.knowflow.service.StudyGroupService;
import com.knowflow.websocket.GroupWebSocketHandler;
import com.knowflow.vo.GroupMessageVO;
import com.knowflow.vo.StudyGroupMemberVO;
import com.knowflow.vo.StudyGroupVO;
import com.knowflow.util.UploadHelper;
import com.knowflow.config.UploadConfigProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 学习小组接口
 */
@Tag(name = "学习小组")
@RestController
@RequestMapping("/api/study-groups")
@RequiredArgsConstructor
public class StudyGroupController {

    private static final long MAX_GROUP_UPLOAD_BYTES = 50L * 1024 * 1024;
    private static final Map<String, Set<String>> ALLOWED_UPLOAD_TYPES = Map.ofEntries(
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")), Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("gif", Set.of("image/gif")), Map.entry("webp", Set.of("image/webp")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("txt", Set.of("text/plain")), Map.entry("md", Set.of("text/plain", "text/markdown")),
            Map.entry("csv", Set.of("text/plain", "text/csv")),
            Map.entry("doc", Set.of("application/msword", "application/octet-stream")),
            Map.entry("xls", Set.of("application/vnd.ms-excel", "application/octet-stream")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint", "application/octet-stream")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/octet-stream")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip", "application/octet-stream")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/zip", "application/octet-stream")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed", "application/octet-stream")));

    private final StudyGroupService studyGroupService;
    private final GroupWebSocketHandler groupWebSocketHandler;
    private final StudyGroupMessageMapper studyGroupMessageMapper;
    private final UploadConfigProperties uploadConfig;

    /**
     * 获取当前登录用户 ID；未登录时抛出 401 业务异常。
     * <p>复用 {@link SecurityUtils#getCurrentUserIdNullable()}，避免内联
     * {@code (Long) authentication.getPrincipal()} 在匿名/未认证场景下强转字符串
     * {@code "anonymousUser"} 导致 {@code ClassCastException} → HTTP 500。</p>
     */
    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    @Operation(summary = "我加入的小组列表")
    @GetMapping("/my")
    public Result<List<StudyGroupVO>> getMyGroups() {
        Long userId = currentUserId();
        return Result.success(studyGroupService.getMyGroups(userId));
    }

    @Operation(summary = "推荐小组列表")
    @GetMapping("/recommend")
    public Result<List<StudyGroupVO>> getRecommendGroups(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.getRecommendGroups(userId, limit));
    }

    @Operation(summary = "小组详情")
    @GetMapping("/{id}")
    public Result<StudyGroupVO> getGroupDetail(@PathVariable Long id) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.getGroupDetail(id, userId));
    }

    @Operation(summary = "创建小组")
    @PostMapping
    public Result<StudyGroupVO> createGroup(@Valid @RequestBody StudyGroupCreateDTO dto) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.createGroup(dto, userId));
    }

    @Operation(summary = "更新小组信息")
    @PutMapping("/{id}")
    public Result<StudyGroupVO> updateGroup(@PathVariable Long id,
                                            @Valid @RequestBody StudyGroupCreateDTO dto) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.updateGroup(id, dto, userId));
    }

    @Operation(summary = "删除小组")
    @DeleteMapping("/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        Long userId = currentUserId();
        studyGroupService.deleteGroup(id, userId);
        return Result.success();
    }

    @Operation(summary = "小组成员列表")
    @GetMapping("/{id}/members")
    public Result<List<StudyGroupMemberVO>> getGroupMembers(@PathVariable Long id) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.getGroupMembers(id, userId));
    }

    @Operation(summary = "邀请成员")
    @PostMapping("/invite")
    public Result<Void> inviteMember(@Valid @RequestBody GroupInviteDTO dto) {
        Long userId = currentUserId();
        studyGroupService.inviteMember(dto, userId);
        return Result.success();
    }

    @Operation(summary = "加入公开小组")
    @PostMapping("/{id}/join")
    public Result<Void> joinGroup(@PathVariable Long id) {
        Long userId = currentUserId();
        studyGroupService.joinGroup(id, userId);
        return Result.success();
    }

    @Operation(summary = "退出小组")
    @PostMapping("/{id}/leave")
    public Result<Void> leaveGroup(@PathVariable Long id) {
        Long userId = currentUserId();
        studyGroupService.leaveGroup(id, userId);
        return Result.success();
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/{groupId}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long groupId,
                                      @PathVariable Long memberId) {
        Long userId = currentUserId();
        studyGroupService.removeMember(groupId, memberId, userId);
        return Result.success();
    }

    @Operation(summary = "更新成员角色")
    @PutMapping("/{groupId}/members/{memberId}/role")
    public Result<Void> updateMemberRole(@PathVariable Long groupId,
                                          @PathVariable Long memberId,
                                          @RequestParam String role) {
        Long userId = currentUserId();
        studyGroupService.updateMemberRole(groupId, memberId, role, userId);
        return Result.success();
    }

    @Operation(summary = "获取消息列表")
    @GetMapping("/{id}/messages")
    public Result<Page<GroupMessageVO>> getMessages(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.getMessages(id, userId, page, size));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/messages")
    public Result<GroupMessageVO> sendMessage(@Valid @RequestBody GroupMessageSendDTO dto) {
        Long userId = currentUserId();
        GroupMessageVO messageVO = studyGroupService.sendMessage(dto, userId);

        // 通过 WebSocket 广播给小组所有在线成员（HTTP 作为 WS 断连时的回退，也需要实时推送）
        groupWebSocketHandler.broadcastToGroup(dto.getGroupId(), Map.of(
                "type", "message",
                "data", messageVO
        ), null);

        // 发送 @提及 通知
        if (messageVO.getMentionUsers() != null && !messageVO.getMentionUsers().isEmpty()) {
            for (GroupMessageVO.MentionedUser mentionedUser : messageVO.getMentionUsers()) {
                groupWebSocketHandler.sendToUser(mentionedUser.getId(), Map.of(
                        "type", "mention",
                        "groupId", dto.getGroupId(),
                        "messageId", messageVO.getId(),
                        "senderName", messageVO.getSenderName(),
                        "content", messageVO.getContent()
                ));
            }
        }

        return Result.success(messageVO);
    }

    @Operation(summary = "撤回消息")
    @DeleteMapping("/messages/{id}")
    public Result<Void> recallMessage(@PathVariable Long id) {
        Long userId = currentUserId();
        StudyGroupMessage message = studyGroupMessageMapper.selectById(id);
        studyGroupService.recallMessage(id, userId);
        // 实时广播撤回，让小组成员会话里的该消息立即变为“已撤回”
        if (message != null) {
            groupWebSocketHandler.broadcastRecall(message.getGroupId(), id);
        }
        return Result.success();
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = currentUserId();
        Long lastReadMessageId = studyGroupService.markAsRead(id, userId);
        // 实时广播已读回执，让小组成员（尤其是发送者）实时看到"已读"
        if (lastReadMessageId != null) {
            groupWebSocketHandler.broadcastReadReceipt(id, userId, lastReadMessageId);
        }
        return Result.success();
    }

    @Operation(summary = "获取未读消息数")
    @GetMapping("/{id}/unread")
    public Result<Integer> getUnreadCount(@PathVariable Long id) {
        Long userId = currentUserId();
        return Result.success(studyGroupService.getUnreadCount(id, userId));
    }

    @Operation(summary = "更新小组公告")
    @PutMapping("/{id}/announcement")
    public Result<Void> updateAnnouncement(@PathVariable Long id,
                                            @RequestParam String announcement) {
        Long userId = currentUserId();
        studyGroupService.updateAnnouncement(id, announcement, userId);
        return Result.success();
    }

    @Operation(summary = "关联学习计划")
    @PutMapping("/{id}/learning-plan")
    public Result<Void> linkLearningPlan(@PathVariable Long id,
                                          @RequestParam Long planId) {
        Long userId = currentUserId();
        studyGroupService.linkLearningPlan(id, planId, userId);
        return Result.success();
    }

    @Operation(summary = "上传文件（图片/文件）")
    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadFile(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = currentUserId();
        studyGroupService.requireMember(id, userId);
        if (file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > MAX_GROUP_UPLOAD_BYTES) {
            throw new BusinessException(400, "文件大小不能超过50MB");
        }
        validateUploadType(file);
        return Result.success(UploadHelper.save(file, uploadConfig.getDir()));
    }

    private void validateUploadType(MultipartFile file) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        int dot = filename.lastIndexOf('.');
        String extension = dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String contentType = Optional.ofNullable(file.getContentType()).orElse("")
                .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        Set<String> allowedContentTypes = ALLOWED_UPLOAD_TYPES.get(extension);
        if (allowedContentTypes == null || !allowedContentTypes.contains(contentType)) {
            throw new BusinessException(400, "不支持的文件类型");
        }
        if (!hasExpectedSignature(file, extension)) {
            throw new BusinessException(400, "文件内容与类型不匹配");
        }
    }

    private boolean hasExpectedSignature(MultipartFile file, String extension) throws IOException {
        if (Set.of("txt", "md", "csv").contains(extension)) {
            return true;
        }
        byte[] header = new byte[12];
        int length;
        try (InputStream input = file.getInputStream()) {
            length = input.read(header);
        }
        return switch (extension) {
            case "png" -> startsWith(header, length, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "jpg", "jpeg" -> startsWith(header, length, 0xff, 0xd8, 0xff);
            case "gif" -> startsWith(header, length, 'G', 'I', 'F', '8', '7', 'a')
                    || startsWith(header, length, 'G', 'I', 'F', '8', '9', 'a');
            case "webp" -> startsWith(header, length, 'R', 'I', 'F', 'F')
                    && length >= 12 && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            case "pdf" -> startsWith(header, length, '%', 'P', 'D', 'F', '-');
            case "zip", "docx", "xlsx", "pptx" -> startsWith(header, length, 'P', 'K', 0x03, 0x04)
                    || startsWith(header, length, 'P', 'K', 0x05, 0x06)
                    || startsWith(header, length, 'P', 'K', 0x07, 0x08);
            case "doc", "xls", "ppt" -> startsWith(header, length, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, int length, int... signature) {
        if (length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xff) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
