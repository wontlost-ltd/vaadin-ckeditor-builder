package com.wontlost.ckeditor.api;

import com.wontlost.ckeditor.config.AIProperties;
import com.wontlost.ckeditor.service.AIUsageService;
import com.wontlost.ckeditor.service.AIUsageService.ConsumeResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * AI 代理端点
 *
 * 接收 CKEditor AI Assistant 发来的请求，转发到 OpenAI API。
 * API Key 仅在服务端注入，前端无需持有密钥。
 * 按 Session 进行使用量限速，认证用户和预览用户使用独立配额。
 */
@RestController
@RequestMapping("/api/ai")
public class AIProxyController {

    private static final Logger log = LoggerFactory.getLogger(AIProxyController.class);

    private final AIProperties aiProperties;
    private final AIUsageService aiUsageService;
    private final RestClient restClient;

    public AIProxyController(AIProperties aiProperties, AIUsageService aiUsageService) {
        this.aiProperties = aiProperties;
        this.aiUsageService = aiUsageService;
        this.restClient = RestClient.create();
    }

    /**
     * 认证用户的 AI 代理端点（需登录）
     */
    @PostMapping(value = "/proxy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> proxy(@RequestBody String body, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        ConsumeResult result = aiUsageService.tryConsume(sessionId, false);
        if (!result.allowed()) {
            return buildRateLimitResponse(result);
        }
        return forwardToAI(body, result);
    }

    /**
     * 预览用户的 AI 代理端点（无需认证，更严格配额）
     */
    @PostMapping(value = "/preview-proxy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> previewProxy(@RequestBody String body, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        ConsumeResult result = aiUsageService.tryConsume(sessionId, true);
        if (!result.allowed()) {
            return buildRateLimitResponse(result);
        }
        return forwardToAI(body, result);
    }

    /**
     * 转发请求到 AI API 并附加限速响应头
     */
    private ResponseEntity<String> forwardToAI(String body, ConsumeResult consumeResult) {
        if (!aiProperties.isConfigured()) {
            log.warn("AI 功能未配置，缺少 AI_API_KEY");
            return ResponseEntity.badRequest().body("{\"error\":\"AI not configured\"}");
        }

        try {
            String response = restClient.post()
                .uri(aiProperties.getApiUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .body(String.class);

            return ResponseEntity.ok()
                .header("X-RateLimit-Remaining", String.valueOf(consumeResult.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(consumeResult.resetAtMs() / 1000))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
        } catch (Exception e) {
            log.error("AI 代理请求失败: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .header("X-RateLimit-Remaining", String.valueOf(consumeResult.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(consumeResult.resetAtMs() / 1000))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"AI proxy request failed\"}");
        }
    }

    /**
     * 构建 429 限速响应
     */
    private ResponseEntity<String> buildRateLimitResponse(ConsumeResult result) {
        String resetAt = Instant.ofEpochMilli(result.resetAtMs()).toString();
        String body = String.format(
            "{\"error\":\"Rate limit exceeded\",\"limit\":%d,\"remaining\":0,\"resetAt\":\"%s\"}",
            result.limit(), resetAt
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("X-RateLimit-Remaining", "0")
            .header("X-RateLimit-Reset", String.valueOf(result.resetAtMs() / 1000))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }
}
