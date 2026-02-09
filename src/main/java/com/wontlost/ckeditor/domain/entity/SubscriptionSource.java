package com.wontlost.ckeditor.domain.entity;

/**
 * 订阅来源枚举
 */
public enum SubscriptionSource {
    COPY_CODE("复制代码"),
    DOWNLOAD_FILE("下载文件"),
    MANUAL("手动订阅"),
    ANONYMOUS("匿名用户");

    private final String displayName;

    SubscriptionSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
