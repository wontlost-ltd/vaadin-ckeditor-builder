package com.wontlost.ckeditor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudflare Turnstile 配置属性
 * 当 site-key 和 secret-key 均非空时启用 Turnstile 登录保护。
 */
@Component
@ConfigurationProperties(prefix = "turnstile")
public class TurnstileProperties {

    private String siteKey = "";

    private String secretKey = "";

    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getVerifyUrl() {
        return verifyUrl;
    }

    public void setVerifyUrl(String verifyUrl) {
        this.verifyUrl = verifyUrl;
    }

    /** 当 site-key 和 secret-key 均配置时返回 true */
    public boolean isConfigured() {
        return siteKey != null && !siteKey.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }
}
