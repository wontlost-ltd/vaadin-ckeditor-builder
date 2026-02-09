package com.wontlost.ckeditor.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 同步审计日志
 * 记录每次同步操作的详细信息
 */
@Entity
@Table(name = "sync_audit_log", indexes = {
    @Index(name = "idx_sync_time", columnList = "syncTime"),
    @Index(name = "idx_sync_type", columnList = "syncType"),
    @Index(name = "idx_status", columnList = "status")
})
public class SyncAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime syncTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncType syncType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private int totalRecords;
    private int successCount;
    private int failCount;
    private int skippedCount;

    private long durationMs;

    @Column(length = 2000)
    private String errorMessage;

    @Column(length = 500)
    private String triggeredBy;

    // Constructors
    public SyncAuditLog() {
        this.syncTime = LocalDateTime.now();
    }

    public SyncAuditLog(SyncType syncType, String triggeredBy) {
        this.syncTime = LocalDateTime.now();
        this.syncType = syncType;
        this.triggeredBy = triggeredBy;
        this.status = Status.RUNNING;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime syncTime) { this.syncTime = syncTime; }

    public SyncType getSyncType() { return syncType; }
    public void setSyncType(SyncType syncType) { this.syncType = syncType; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }

    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage != null && errorMessage.length() > 2000
            ? errorMessage.substring(0, 2000) : errorMessage;
    }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    // 便捷方法
    public void markCompleted(int success, int fail, int skipped, long durationMs) {
        this.successCount = success;
        this.failCount = fail;
        this.skippedCount = skipped;
        this.durationMs = durationMs;
        this.status = fail > 0 ? Status.PARTIAL : Status.SUCCESS;
    }

    public void markFailed(String error, long durationMs) {
        this.status = Status.FAILED;
        setErrorMessage(error);
        this.durationMs = durationMs;
    }

    /**
     * 同步类型
     */
    public enum SyncType {
        REALTIME_SINGLE,    // 实时单条同步
        SCHEDULED_FULL,     // 定时全量同步
        SCHEDULED_INCR,     // 定时增量同步
        MANUAL_FULL,        // 手动全量同步
        MANUAL_INCR,        // 手动增量同步
        RETRY_FAILED,       // 重试失败记录
        RESTORE             // 从 Oracle 恢复
    }

    /**
     * 同步状态
     */
    public enum Status {
        RUNNING,    // 运行中
        SUCCESS,    // 全部成功
        PARTIAL,    // 部分成功
        FAILED      // 全部失败
    }
}
