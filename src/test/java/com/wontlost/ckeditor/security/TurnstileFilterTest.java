package com.wontlost.ckeditor.security;

import com.wontlost.ckeditor.config.TurnstileProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * TurnstileFilter 单元测试
 * 覆盖跳过条件、缺失 token 拒绝、正常过滤链传递
 */
@ExtendWith(MockitoExtension.class)
class TurnstileFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private TurnstileProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TurnstileProperties();
    }

    @Nested
    @DisplayName("shouldNotFilter — 跳过条件")
    class ShouldNotFilter {

        @Test
        @DisplayName("GET 请求跳过")
        void skipsGetRequests() throws Exception {
            properties.setSiteKey("test-key");
            properties.setSecretKey("test-secret");
            TurnstileFilter filter = new TurnstileFilter(properties);

            when(request.getMethod()).thenReturn("GET");

            // shouldNotFilter = true → doFilterInternal 不执行
            // 直接验证 GET 请求不会被拦截
            filter.doFilter(request, response, filterChain);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("非 /login 路径跳过")
        void skipsNonLoginPath() throws Exception {
            properties.setSiteKey("test-key");
            properties.setSecretKey("test-secret");
            TurnstileFilter filter = new TurnstileFilter(properties);

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/something");
            when(request.getContextPath()).thenReturn("");

            filter.doFilter(request, response, filterChain);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("未配置 Turnstile 时跳过")
        void skipsWhenNotConfigured() throws Exception {
            // properties 默认 siteKey="" secretKey="" → isConfigured=false
            TurnstileFilter filter = new TurnstileFilter(properties);

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/login");
            when(request.getContextPath()).thenReturn("");

            filter.doFilter(request, response, filterChain);
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("doFilterInternal — 验证逻辑")
    class DoFilter {

        @BeforeEach
        void configureTurnstile() {
            properties.setSiteKey("test-key");
            properties.setSecretKey("test-secret");
        }

        @Test
        @DisplayName("缺失 token 时重定向到 /login?error")
        void rejectsWhenTokenMissing() throws Exception {
            TurnstileFilter filter = new TurnstileFilter(properties);

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/login");
            when(request.getContextPath()).thenReturn("");
            when(request.getParameter("cf-turnstile-response")).thenReturn(null);

            filter.doFilter(request, response, filterChain);

            verify(response).sendRedirect("/login?error");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("空白 token 时重定向到 /login?error")
        void rejectsWhenTokenBlank() throws Exception {
            TurnstileFilter filter = new TurnstileFilter(properties);

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/login");
            when(request.getContextPath()).thenReturn("");
            when(request.getParameter("cf-turnstile-response")).thenReturn("  ");

            filter.doFilter(request, response, filterChain);

            verify(response).sendRedirect("/login?error");
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("isConfigured")
    class IsConfigured {

        @Test
        @DisplayName("两个 key 都有值时返回 true")
        void configuredWhenBothKeysPresent() {
            properties.setSiteKey("site-key");
            properties.setSecretKey("secret-key");
            assert properties.isConfigured();
        }

        @Test
        @DisplayName("site-key 为空时返回 false")
        void notConfiguredWhenSiteKeyEmpty() {
            properties.setSiteKey("");
            properties.setSecretKey("secret-key");
            assert !properties.isConfigured();
        }

        @Test
        @DisplayName("secret-key 为空时返回 false")
        void notConfiguredWhenSecretKeyEmpty() {
            properties.setSiteKey("site-key");
            properties.setSecretKey("");
            assert !properties.isConfigured();
        }
    }
}
