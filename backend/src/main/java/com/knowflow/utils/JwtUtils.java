package com.knowflow.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：负责 token 的生成、解析、校验及声明（userId/username/role）提取。
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire:86400000}")
    private Long expire;

    /**
     * 基于配置密钥构造 HMAC-SHA 签名密钥；secret 需满足 JWT 对密钥长度的最低要求。
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token：写入 userId/username/role 声明，设置签发与过期时间（默认 86400000 毫秒=24h）并签名。
     */
    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role != null ? role : "USER");
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expire))
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 解析并验签 token，返回声明体 Claims；签名错误或已篡改将抛出异常。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 token 是否有效（可正常解析且签名无误），异常统一返回 false。
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 提取角色声明，缺失时回退为 "USER"。
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        String role = claims.get("role", String.class);
        return role != null ? role : "USER";
    }

    /**
     * 提取过期时间（epoch millis）；无过期声明时回退为当前时间。
     */
    public long getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();
        return expiration != null ? expiration.getTime() : System.currentTimeMillis();
    }
}
