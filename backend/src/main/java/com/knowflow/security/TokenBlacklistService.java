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

    public void add(String token, long expireAtMillis) {
        if (token == null || token.isBlank()) {
            return;
        }
        blacklist.put(token, expireAtMillis);
    }

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
