package com.wontlost.ckeditor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 代理配置
 *
 * 配置项（application.properties 或环境变量）：
 * - ai.api-key / AI_API_KEY — OpenAI API Key
 * - ai.model / AI_MODEL — 模型名称（默认 gpt-4o）
 * - ai.api-url / AI_API_URL — API 端点
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProperties {

    /**
     * OpenAI API Key（必需）
     */
    private String apiKey = "";

    /**
     * 模型名称
     */
    private String model = "gpt-4o";

    /**
     * API 端点 URL
     */
    private String apiUrl = "https://api.openai.com/v1/chat/completions";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * AI 使用量限制配置
     */
    private UsageLimit usageLimit = new UsageLimit();

    public UsageLimit getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(UsageLimit usageLimit) {
        this.usageLimit = usageLimit;
    }

    /**
     * 检查 AI 功能是否已配置
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * AI 使用量限制配置（按 session，时间窗口内）
     */
    public static class UsageLimit {

        /** 认证用户每窗口最大请求数 */
        private int sessionMax = 10;

        /** 预览用户每窗口最大请求数 */
        private int previewSessionMax = 3;

        /** 时间窗口大小（毫秒），默认 5 小时 */
        private long windowMs = 5 * 3600_000L;

        /** 过期 session 清理间隔（毫秒） */
        private long cleanupIntervalMs = 300_000L;

        /** 非活跃 session 超时（毫秒），默认等于窗口大小 */
        private long inactiveTimeoutMs = 5 * 3600_000L;

        public int getSessionMax() {
            return sessionMax;
        }

        public void setSessionMax(int sessionMax) {
            this.sessionMax = sessionMax;
        }

        public int getPreviewSessionMax() {
            return previewSessionMax;
        }

        public void setPreviewSessionMax(int previewSessionMax) {
            this.previewSessionMax = previewSessionMax;
        }

        public long getWindowMs() {
            return windowMs;
        }

        public void setWindowMs(long windowMs) {
            this.windowMs = windowMs;
        }

        public long getCleanupIntervalMs() {
            return cleanupIntervalMs;
        }

        public void setCleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs;
        }

        public long getInactiveTimeoutMs() {
            return inactiveTimeoutMs;
        }

        public void setInactiveTimeoutMs(long inactiveTimeoutMs) {
            this.inactiveTimeoutMs = inactiveTimeoutMs;
        }
    }
}
