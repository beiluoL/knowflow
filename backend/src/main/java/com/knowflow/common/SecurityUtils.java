package com.knowflow.common;

import com.knowflow.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 上下文工具类，统一提取当前登录用户 ID。
 * 避免 Controller 中重复实现 getCurrentUserId() 方法。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID；未登录时抛出业务异常。
     *
     * @return 当前用户 ID（非 null）
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("未登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            throw new BusinessException("未登录");
        }
        try {
            return (Long) principal;
        } catch (ClassCastException e) {
            // 匿名用户等情况，principal 可能为字符串
            throw new BusinessException("未登录");
        }
    }

    /**
     * 获取当前用户角色（sys_user.role），若未设置则返回空字符串。
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "";
        }
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("");
    }

    /**
     * 当前用户是否是 ADMIN（系统管理员，可访问所有知识库）。
     */
    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(getCurrentUserRole());
    }
}
