package com.wontlost.ckeditor.api;

import com.wontlost.ckeditor.config.CollaborationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * CollaborationTokenController 单元测试
 * 覆盖 JWT token 生成的正常路径、匿名降级、权限验证
 */
@ExtendWith(MockitoExtension.class)
class CollaborationTokenControllerTest {

    private static final String ENV_ID = "env-123";
    // HmacSHA256 要求密钥至少 32 字节
    private static final String API_SECRET = "test-secret-that-is-long-enough-for-hmac-sha256";
    private static final String WS_URL = "wss://env-123.cke-cs.com/ws";

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private CollaborationProperties properties;
    private CollaborationTokenController controller;

    @BeforeEach
    void setUp() {
        properties = new CollaborationProperties();
        properties.setEnvironmentId(ENV_ID);
        properties.setApiSecret(API_SECRET);
        properties.setWebSocketUrl(WS_URL);
        controller = new CollaborationTokenController(properties);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("配置缺失")
    class NotConfigured {

        @Test
        @DisplayName("协作未配置时返回 400")
        void shouldReturnBadRequestWhenNotConfigured() {
            // 只需确保认证通过（非匿名），不需要 getName()
            when(securityContext.getAuthentication()).thenReturn(authentication);
            CollaborationProperties emptyProps = new CollaborationProperties();
            CollaborationTokenController emptyController = new CollaborationTokenController(emptyProps);

            ResponseEntity<String> response = emptyController.getToken();

            assertEquals(400, response.getStatusCode().value());
            assertEquals("Cloud Services not configured", response.getBody());
        }
    }

    @Nested
    @DisplayName("配置完整")
    class Configured {

        @Test
        @DisplayName("认证用户获取有效 JWT，包含正确 claims、权限和过期时间")
        void authenticatedUser_shouldReturnValidTokenWithExpectedClaims() {
            stubAuthentication("Alice", true);

            ResponseEntity<String> response = controller.getToken();

            assertEquals(200, response.getStatusCode().value());
            String token = response.getBody();
            assertNotNull(token);

            Claims claims = parseClaims(token);

            // jjwt 解析 aud 为 Set<String>
            assertEquals(ENV_ID, claims.getAudience().iterator().next());
            assertEquals("user-alice", claims.getSubject());

            Map<?, ?> user = claims.get("user", Map.class);
            assertEquals("Alice", user.get("name"));
            assertEquals("user-alice@ckeditor-builder.local", user.get("email"));

            // 验证 exp 存在且在未来 ~1 小时内
            assertNotNull(claims.getExpiration(), "Token 必须包含 exp 过期时间");
            long expiresInMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            assertTrue(expiresInMs > 3500_000 && expiresInMs <= 3600_000,
                "Token 有效期应约为 1 小时，实际: " + expiresInMs + "ms");

            // 细粒度权限（不含 comment:admin）
            List<?> permissions = extractPermissions(claims);
            assertEquals(4, permissions.size());
            assertTrue(permissions.containsAll(List.of(
                "document:read",
                "document:write",
                "comment:read",
                "comment:write"
            )));
            assertFalse(permissions.contains("comment:admin"));
        }

        @Test
        @DisplayName("匿名用户返回 401")
        void anonymousUser_shouldReturn401() {
            AnonymousAuthenticationToken anonAuth = new AnonymousAuthenticationToken(
                "key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            when(securityContext.getAuthentication()).thenReturn(anonAuth);

            ResponseEntity<String> response = controller.getToken();

            assertEquals(401, response.getStatusCode().value());
            assertEquals("Authentication required", response.getBody());
        }

        @Test
        @DisplayName("用户名含空格时 userId 正确格式化")
        void userNameWithSpaces_shouldNormalizeUserId() {
            stubAuthentication("Jane Doe", true);

            ResponseEntity<String> response = controller.getToken();

            Claims claims = parseClaims(response.getBody());
            Map<?, ?> user = claims.get("user", Map.class);

            assertEquals("user-jane-doe", claims.getSubject());
            assertEquals("user-jane-doe@ckeditor-builder.local", user.get("email"));
        }

        @Test
        @DisplayName("未认证时返回 401")
        void unauthenticated_shouldReturn401() {
            when(securityContext.getAuthentication()).thenReturn(null);

            ResponseEntity<String> response = controller.getToken();

            assertEquals(401, response.getStatusCode().value());
            assertEquals("Authentication required", response.getBody());
        }

        @Test
        @DisplayName("预览 token 仅含只读协作权限，包含 AI 权限")
        void previewToken_shouldHaveReadOnlyPermissionsAndAi() {
            ResponseEntity<String> response = controller.getAiPreviewToken();

            assertEquals(200, response.getStatusCode().value());
            String token = response.getBody();
            assertNotNull(token);

            Claims claims = parseClaims(token);
            assertEquals("user-preview", claims.getSubject());

            // 仅含只读协作权限
            List<?> permissions = extractPermissions(claims);
            assertEquals(2, permissions.size());
            assertTrue(permissions.containsAll(List.of("document:read", "comment:read")));
            assertFalse(permissions.contains("document:write"));

            // 包含 AI 权限（AI Chat 需要）
            Map<?, ?> auth = claims.get("auth", Map.class);
            assertNotNull(auth.get("ai"), "预览 token 应包含 AI 权限");
            @SuppressWarnings("unchecked")
            Map<String, ?> ai = (Map<String, ?>) auth.get("ai");
            List<?> aiPermissions = (List<?>) ai.get("permissions");
            assertTrue(aiPermissions.contains("ai:conversations:*"));
            assertTrue(aiPermissions.contains("ai:models:*"));

            // 有过期时间
            assertNotNull(claims.getExpiration());
        }

        @Test
        @DisplayName("认证 token 包含 AI 权限")
        void authenticatedToken_shouldIncludeAiPermissions() {
            stubAuthentication("Alice", true);

            ResponseEntity<String> response = controller.getToken();

            Claims claims = parseClaims(response.getBody());
            Map<?, ?> auth = claims.get("auth", Map.class);
            assertNotNull(auth.get("ai"), "认证 token 应包含 AI 权限");
            @SuppressWarnings("unchecked")
            Map<String, ?> ai = (Map<String, ?>) auth.get("ai");
            List<?> aiPermissions = (List<?>) ai.get("permissions");
            assertTrue(aiPermissions.contains("ai:conversations:*"));
            assertTrue(aiPermissions.contains("ai:models:*"));
            assertTrue(aiPermissions.contains("ai:actions:system:*"));
            assertTrue(aiPermissions.contains("ai:reviews:system:*"));
        }

    }

    private void stubAuthentication(String name, boolean authenticated) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(name);
    }

    private Claims parseClaims(String token) {
        SecretKey key = new SecretKeySpec(
            properties.getApiSecret().getBytes(StandardCharsets.US_ASCII),
            "HmacSHA256"
        );
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @SuppressWarnings("unchecked")
    private List<?> extractPermissions(Claims claims) {
        Map<?, ?> auth = claims.get("auth", Map.class);
        Map<?, ?> collaboration = (Map<?, ?>) auth.get("collaboration");
        Map<?, ?> wildcard = (Map<?, ?>) collaboration.get("*");
        return (List<?>) wildcard.get("permissions");
    }
}
