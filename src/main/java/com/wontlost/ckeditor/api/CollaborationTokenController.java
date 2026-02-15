package com.wontlost.ckeditor.api;

import com.wontlost.ckeditor.config.CollaborationProperties;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CKEditor Cloud Services Token 端点
 *
 * 为 CKEditor 5 实时协作功能提供 JWT 认证 token。
 * CKEditor 前端通过 cloudServices.tokenUrl 指向此端点，
 * 自动获取 token 用于 WebSocket 连接认证。
 *
 * 用户身份从 Spring Security 认证上下文获取，
 * 未认证用户返回 401。
 *
 * Token 格式遵循 CKEditor Cloud Services 规范：
 * - aud: 环境 ID
 * - iat: 签发时间
 * - sub: 用户标识
 * - user: 用户显示信息（名称、邮箱）
 * - auth: 协作权限（自定义权限，不含 comment:admin/comment:modify_all）
 * - exp: 过期时间（签发后 1 小时）
 */
@RestController
@RequestMapping("/api/ckeditor")
public class CollaborationTokenController {

    private static final Logger log = LoggerFactory.getLogger(CollaborationTokenController.class);

    /** Token 有效期：1 小时 */
    private static final long TOKEN_TTL_MS = 3600_000;

    private final CollaborationProperties collaborationProperties;

    public CollaborationTokenController(CollaborationProperties collaborationProperties) {
        this.collaborationProperties = collaborationProperties;
    }

    /**
     * 生成 CKEditor Cloud Services JWT token
     *
     * 用户身份从 Spring Security 认证上下文获取。
     * CKEditor 前端期望响应体为纯文本 JWT 字符串。
     */
    @GetMapping(value = "/token", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        if (!collaborationProperties.isConfigured()) {
            log.warn("CKEditor Cloud Services 未配置，无法生成 token");
            return ResponseEntity.badRequest().body("Cloud Services not configured");
        }

        String userName = auth.getName();
        String token = createAuthenticatedToken(userName);
        return ResponseEntity.ok(token);
    }

    /**
     * 匿名 AI 预览用 token 端点
     *
     * Wizard 预览中 AI 插件需要 cloudServices.tokenUrl，
     * 但预览页面是匿名访问，无法使用 /token 端点。
     * 此端点生成只读 preview 用户的 token（无写权限、无 AI 权限）。
     */
    @GetMapping(value = "/ai-token", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAiPreviewToken() {
        if (!collaborationProperties.isConfigured()) {
            return ResponseEntity.badRequest().body("Cloud Services not configured");
        }

        String token = createPreviewToken();
        return ResponseEntity.ok(token);
    }

    /**
     * 认证用户 token — 细粒度协作权限 + AI 权限
     * 使用 permissions 列表（不含 comment:admin）替代 role: "writer"
     */
    private String createAuthenticatedToken(String userName) {
        Map<String, Object> authMap = new HashMap<>();
        // 细粒度协作权限 — 不含 comment:admin，用户只能管理自己的评论
        authMap.put("collaboration", Map.of(
            "*", Map.of("permissions", List.of(
                "document:read",
                "document:write",
                "comment:read",
                "comment:write"
            ))
        ));
        // AI 权限 — CKEditor Cloud Services 需要这些权限才能代理 AI 请求
        authMap.put("ai", Map.of(
            "permissions", List.of(
                "ai:conversations:*",
                "ai:models:*",
                "ai:actions:system:*",
                "ai:reviews:system:*"
            )
        ));

        return buildToken(userName, authMap);
    }

    /**
     * 预览用 token — 只读协作权限 + AI 权限
     * AI Chat 需要 auth.ai.permissions 才能在 CKEditor Cloud Services 上创建对话
     */
    private String createPreviewToken() {
        Map<String, Object> authMap = new HashMap<>();
        authMap.put("collaboration", Map.of(
            "*", Map.of("permissions", List.of(
                "document:read",
                "comment:read"
            ))
        ));
        // AI 权限 — CKEditor Cloud Services 需要这些权限才能代理 AI 请求
        authMap.put("ai", Map.of(
            "permissions", List.of(
                "ai:conversations:*",
                "ai:models:*",
                "ai:actions:system:*",
                "ai:reviews:system:*"
            )
        ));

        return buildToken("preview", authMap);
    }

    private String buildToken(String userName, Map<String, Object> authMap) {
        SecretKey key = new SecretKeySpec(
            collaborationProperties.getApiSecret().getBytes(StandardCharsets.US_ASCII),
            "HmacSHA256"
        );

        String userId = "user-" + userName.replaceAll("\\s+", "-").toLowerCase();
        long nowMs = System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("aud", collaborationProperties.getEnvironmentId());
        payload.put("iat", nowMs / 1000);
        payload.put("sub", userId);
        payload.put("user", Map.of(
            "email", userId + "@ckeditor-builder.local",
            "name", userName
        ));
        payload.put("auth", authMap);

        return Jwts.builder()
            .claims(payload)
            .expiration(new Date(nowMs + TOKEN_TTL_MS))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }
}
