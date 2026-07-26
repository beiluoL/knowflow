package com.knowflow.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登出 token 黑名单（内存实现，单实例开发环境适用）。
 * key=token 字符串，value=JWT 过期时间(epoch millis)。
 * token 自然过期后自动移出黑名单，避免无限增长。
 */
@Component
public class TokenBlacklistService {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * 将 token 加入黑名单。
     * expireAtMillis 取 JWT 的过期时间(epoch millis)，供后续自动清理使用；空 token 直接忽略。
     */
    public void add(String token, long expireAtMillis) {
        if (token == null || token.isBlank()) {
            return;
        }
        blacklist.put(token, expireAtMillis);
    }

    /**
     * 判断 token 是否仍在黑名单且未过期。
     * 若已超过过期时间则自动移出黑名单并返回 false，避免黑名单无限增长。
     */
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        Long expireAt = blacklist.get(token);
        if (expireAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }
}
