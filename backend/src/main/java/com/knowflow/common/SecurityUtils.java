package com.knowflow.common;

/** 旧调用方的兼容入口，认证上下文由 Framework 维护。 */
public final class SecurityUtils {
  private SecurityUtils() {}

  public static Long getCurrentUserId() {
    return com.knowflow.framework.security.SecurityUtils.getCurrentUserId();
  }

  public static Long getCurrentUserIdNullable() {
    return com.knowflow.framework.security.SecurityUtils.getCurrentUserIdNullable();
  }

  public static String getCurrentUserRole() {
    return com.knowflow.framework.security.SecurityUtils.getCurrentUserRole();
  }

  public static boolean isAdmin() {
    return com.knowflow.framework.security.SecurityUtils.isAdmin();
  }
}
