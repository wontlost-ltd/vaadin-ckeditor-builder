package com.wontlost.ckeditor.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wontlost.ckeditor.config.TurnstileProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Cloudflare Turnstile 验证过滤器。
 * 仅拦截 POST /login 请求，验证 cf-turnstile-response 参数。
 * 当 Turnstile 未配置时自动跳过。
 */
public class TurnstileFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TurnstileFilter.class);
    private static final String LOGIN_PATH = "/login";

    private final TurnstileProperties turnstileProperties;
    private final RestClient restClient;

    public TurnstileFilter(TurnstileProperties turnstileProperties) {
        this.turnstileProperties = turnstileProperties;
        this.restClient = RestClient.create();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!isLoginRequest(request)) {
            return true;
        }
        return !turnstileProperties.isConfigured();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getParameter("cf-turnstile-response");
        if (token == null || token.isBlank()) {
            log.warn("Turnstile token 缺失，拒绝登录请求");
            reject(request, response);
            return;
        }

        if (!verifyToken(token, request.getRemoteAddr())) {
            reject(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean verifyToken(String token, String remoteIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", turnstileProperties.getSecretKey());
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        try {
            TurnstileVerifyResponse verifyResponse = restClient.post()
                .uri(turnstileProperties.getVerifyUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TurnstileVerifyResponse.class);

            if (verifyResponse == null || !verifyResponse.success()) {
                log.warn("Turnstile 验证被拒绝: {}",
                    verifyResponse != null ? verifyResponse.errorCodes() : "空响应");
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("Turnstile 验证请求失败: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return LOGIN_PATH.equals(path);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/login?error");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileVerifyResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("error-codes") List<String> errorCodes
    ) {
    }
}
