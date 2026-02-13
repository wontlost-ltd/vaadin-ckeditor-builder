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
 */
@RestController
@RequestMapping("/api/ckeditor")
public class CollaborationTokenController {

    private static final Logger log = LoggerFactory.getLogger(CollaborationTokenController.class);

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
        String token = createToken(userName);
        return ResponseEntity.ok(token);
    }

    private String createToken(String userName) {
        SecretKey key = new SecretKeySpec(
            collaborationProperties.getApiSecret().getBytes(StandardCharsets.US_ASCII),
            "HmacSHA256"
        );

        String userId = "user-" + userName.replaceAll("\\s+", "-").toLowerCase();

        Map<String, Object> payload = new HashMap<>();
        payload.put("aud", collaborationProperties.getEnvironmentId());
        payload.put("iat", System.currentTimeMillis() / 1000);
        payload.put("sub", userId);
        payload.put("user", Map.of(
            "email", userId + "@ckeditor-builder.local",
            "name", userName
        ));
        // 自定义权限：文档读写 + 评论读写，不含 comment:admin 和 comment:modify_all，
        // 确保用户只能编辑/删除自己的评论。
        payload.put("auth", Map.of(
            "collaboration", Map.of(
                "*", Map.of("permissions", List.of(
                    "document:read",
                    "document:write",
                    "comment:read",
                    "comment:write"
                ))
            )
        ));

        return Jwts.builder()
            .claims(payload)
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }
}
