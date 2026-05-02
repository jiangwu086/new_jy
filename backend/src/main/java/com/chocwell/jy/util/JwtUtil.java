package com.chocwell.jy.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具类（统一处理用户端和管理端 token）
 * subject 区分身份：
 *   "user"  — 小程序用户，claim userId
 *   "admin" — 后台管理员，claim adminId + role
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-days:30}")
    private int expireDays;

    // ─── 用户端 ──────────────────────────────────────────────

    public String generate(Long userId, String openid) {
        return JWT.create()
                .withSubject("user")
                .withClaim("userId", userId)
                .withClaim("openid", openid == null ? "" : openid)
                .withIssuedAt(new Date())
                .withExpiresAt(expireAt())
                .sign(alg());
    }

    public Long parseUserId(String token) {
        DecodedJWT jwt = verify(token);
        if (!"user".equals(jwt.getSubject())) throw new RuntimeException("非用户 token");
        return jwt.getClaim("userId").asLong();
    }

    // ─── 管理端 ──────────────────────────────────────────────

    public String generateAdmin(Long adminId, String role) {
        return JWT.create()
                .withSubject("admin")
                .withClaim("adminId", adminId)
                .withClaim("role", role)
                .withIssuedAt(new Date())
                .withExpiresAt(expireAt())
                .sign(alg());
    }

    public Long parseAdminId(String token) {
        DecodedJWT jwt = verify(token);
        if (!"admin".equals(jwt.getSubject())) throw new RuntimeException("非管理员 token");
        return jwt.getClaim("adminId").asLong();
    }

    public String parseAdminRole(String token) {
        return verify(token).getClaim("role").asString();
    }

    // ─── 机构端 ──────────────────────────────────────────────

    public String generateOrg(Long adminId, Long orgId) {
        return JWT.create()
                .withSubject("org")
                .withClaim("adminId", adminId)
                .withClaim("orgId", orgId)
                .withIssuedAt(new Date())
                .withExpiresAt(expireAt())
                .sign(alg());
    }

    public Long parseOrgAdminId(String token) {
        DecodedJWT jwt = verify(token);
        if (!"org".equals(jwt.getSubject())) throw new RuntimeException("非机构管理员 token");
        return jwt.getClaim("adminId").asLong();
    }

    public Long parseOrgId(String token) {
        return verify(token).getClaim("orgId").asLong();
    }

    // ─── 通用 ────────────────────────────────────────────────

    private DecodedJWT verify(String token) {
        try {
            JWTVerifier v = JWT.require(alg()).build();
            return v.verify(token);
        } catch (JWTVerificationException e) {
            throw new RuntimeException("token 无效或已过期");
        }
    }

    private Algorithm alg() {
        return Algorithm.HMAC256(secret);
    }

    private Date expireAt() {
        long ms = (long) expireDays * 86400 * 1000;
        return new Date(System.currentTimeMillis() + ms);
    }
}
