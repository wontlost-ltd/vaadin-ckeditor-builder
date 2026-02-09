package com.wontlost.ckeditor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for CKEditor.
 *
 * Properties can be set in application.properties or via environment variables:
 * - ckeditor.license-key or CKEDITOR_LICENSE_KEY
 */
@Component
@ConfigurationProperties(prefix = "ckeditor")
public class CKEditorProperties {

    /**
     * CKEditor license key.
     * Get your license key from https://ckeditor.com/pricing
     * Use "GPL" for open source license (limited features).
     */
    private String licenseKey = "GPL";

    public String getLicenseKey() {
        return licenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    /**
     * Check if a premium license key is configured.
     * @return true if a non-GPL license key is set
     */
    public boolean hasPremiumLicense() {
        return licenseKey != null
            && !licenseKey.trim().isEmpty()
            && !licenseKey.equalsIgnoreCase("GPL");
    }
}
