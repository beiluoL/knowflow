package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "消息通知接口")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "消息列表")
    @GetMapping
    public Result<PageResult<NotificationVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        IPage<NotificationVO> page = notificationService.getNotificationPage(userId, type, isRead, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead(Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(notificationService.getUnreadCount(userId));
    }
}
