package com.wontlost.ckeditor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CKEditor Cloud Services 协作配置
 *
 * 配置项（application.properties 或环境变量）：
 * - ckeditor.collaboration.environment-id / CKEDITOR_CS_ENVIRONMENT_ID
 * - ckeditor.collaboration.api-secret / CKEDITOR_CS_API_SECRET
 * - ckeditor.collaboration.web-socket-url / CKEDITOR_CS_WS_URL
 */
@Component
@ConfigurationProperties(prefix = "ckeditor.collaboration")
public class CollaborationProperties {

    /**
     * CKEditor Cloud Services 环境 ID
     * 从 CKEditor Dashboard (https://dashboard.ckeditor.com) 获取
     */
    private String environmentId = "";

    /**
     * CKEditor Cloud Services API Secret（用于签署 JWT token）
     * 从 CKEditor Dashboard 获取，务必保密
     */
    private String apiSecret = "";

    /**
     * CKEditor Cloud Services WebSocket URL
     * 通常为 wss://{environmentId}.cke-cs.com/ws
     */
    private String webSocketUrl = "";

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getWebSocketUrl() {
        return webSocketUrl;
    }

    public void setWebSocketUrl(String webSocketUrl) {
        this.webSocketUrl = webSocketUrl;
    }

    /**
     * 检查协作功能是否已配置
     */
    public boolean isConfigured() {
        return environmentId != null && !environmentId.isBlank()
            && apiSecret != null && !apiSecret.isBlank()
            && webSocketUrl != null && !webSocketUrl.isBlank();
    }
}
