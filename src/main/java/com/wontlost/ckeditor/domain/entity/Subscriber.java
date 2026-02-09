package com.wontlost.ckeditor.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 订阅者实体
 * 存储用户邮箱订阅信息和使用统计
 */
@Entity
@Table(name = "subscribers", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 2048)
    private String configSnapshot;  // JSON 格式保存用户配置快照

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private SubscriptionSource source;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;

    @Column(nullable = false)
    private boolean active = true;

    // 使用统计
    private int copyCount = 0;
    private int downloadCount = 0;

    // 同步状态
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SyncStatus syncStatus = SyncStatus.PENDING;

    private LocalDateTime lastSyncedAt;

    private int syncRetryCount = 0;

    @Column(length = 500)
    private String lastSyncError;

    // Constructors
    public Subscriber() {}

    public Subscriber(String email, SubscriptionSource source) {
        java.util.Objects.requireNonNull(email, "email must not be null");
        this.email = email;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(String configSnapshot) { this.configSnapshot = configSnapshot; }

    public SubscriptionSource getSource() { return source; }
    public void setSource(SubscriptionSource source) { this.source = source; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getCopyCount() { return copyCount; }
    public void setCopyCount(int copyCount) { this.copyCount = copyCount; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    // 同步状态 Getters and Setters
    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }

    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public int getSyncRetryCount() { return syncRetryCount; }
    public void setSyncRetryCount(int syncRetryCount) { this.syncRetryCount = syncRetryCount; }

    public String getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }

    // 便捷方法
    public boolean isAnonymous() {
        return email != null && email.endsWith("@anonymous.local");
    }

    public void incrementCopyCount() { this.copyCount++; }
    public void incrementDownloadCount() { this.downloadCount++; }
    public void updateLastActive() { this.lastActiveAt = LocalDateTime.now(); }

    public void markSyncSuccess() {
        this.syncStatus = SyncStatus.SYNCED;
        this.lastSyncedAt = LocalDateTime.now();
        this.syncRetryCount = 0;
        this.lastSyncError = null;
    }

    public void markSyncFailed(String error) {
        this.syncStatus = SyncStatus.FAILED;
        this.lastSyncedAt = LocalDateTime.now();
        this.syncRetryCount++;
        this.lastSyncError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public void markSyncPending() {
        this.syncStatus = SyncStatus.PENDING;
    }

    /**
     * 同步状态枚举
     */
    public enum SyncStatus {
        PENDING,    // 待同步
        SYNCED,     // 已同步
        FAILED      // 同步失败
    }
}
