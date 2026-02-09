package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.config.SyncMode;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.h2.SyncAuditLogRepository;
import com.wontlost.ckeditor.repository.oracle.OracleSubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


/**
 * 数据同步服务
 * 负责 H2 和 Oracle 之间的数据同步
 *
 * 特性：
 * - 增量同步：仅同步状态为 PENDING 或 FAILED 的记录
 * - 审计日志：每次同步操作都记录到 SyncAuditLog
 * - 失败重试：失败记录最多重试 3 次
 * - 同步状态：每条记录维护独立的同步状态
 */
@Service
public class DataSyncService {

    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);

    private final H2SubscriberRepository h2Repository;
    private final SyncAuditLogRepository auditLogRepository;
    private final DataSyncProperties syncProperties;
    private final OracleSyncHelper oracleSyncHelper;

    // 自引用代理，确保 @Async 等注解通过代理生效（避免同类内部调用绕过代理）
    @org.springframework.context.annotation.Lazy
    @Autowired
    private DataSyncService self;

    @Autowired(required = false)
    private OracleSubscriberRepository oracleRepository;

    public DataSyncService(
            H2SubscriberRepository h2Repository,
            SyncAuditLogRepository auditLogRepository,
            DataSyncProperties syncProperties,
            OracleSyncHelper oracleSyncHelper) {
        this.h2Repository = h2Repository;
        this.auditLogRepository = auditLogRepository;
        this.syncProperties = syncProperties;
        this.oracleSyncHelper = oracleSyncHelper;
    }

    /**
     * 获取当前同步模式
     */
    public SyncMode getSyncMode() {
        return syncProperties.getMode();
    }

    /**
     * 检查 Oracle 是否可用
     */
    public boolean isOracleAvailable() {
        return syncProperties.isOracleEnabled() && oracleRepository != null;
    }

    /**
     * 获取最大重试次数
     */
    private int getMaxRetryCount() {
        return syncProperties.getRetry().getMaxCount();
    }

    // ==================== 保存与实时同步 ====================

    /**
     * 保存订阅者并根据模式同步
     */
    public Subscriber saveWithSync(Subscriber subscriber) {
        // 标记为待同步
        subscriber.markSyncPending();
        Subscriber saved = h2Repository.save(subscriber);

        if (isOracleAvailable() && getSyncMode() == SyncMode.REALTIME) {
            // 通过代理调用，确保 @Async 注解生效
            self.syncToOracleAsync(saved);
        }

        return saved;
    }

    /**
     * 异步同步单个订阅者到 Oracle（实时同步）
     * 通过 ID 从数据库重新加载实体，避免与调用方共享同一实体引用导致并发修改
     */
    @Async
    public CompletableFuture<Boolean> syncToOracleAsync(Subscriber subscriber) {
        if (!isOracleAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        Long subscriberId = subscriber.getId();
        long startTime = System.currentTimeMillis();
        SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.REALTIME_SINGLE, "SYSTEM");
        audit.setTotalRecords(1);

        try {
            // 从数据库重新加载，避免与调用方共享实体引用
            Subscriber fresh = h2Repository.findById(subscriberId).orElse(null);
            if (fresh == null) {
                log.warn("异步同步时订阅者已不存在: id={}", subscriberId);
                return CompletableFuture.completedFuture(false);
            }

            syncSubscriberToOracle(fresh);
            fresh.markSyncSuccess();
            h2Repository.save(fresh);

            audit.markCompleted(1, 0, 0, System.currentTimeMillis() - startTime);
            auditLogRepository.save(audit);

            log.debug("订阅者已同步到 Oracle: {}", fresh.getEmail());
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            // 重新加载以获取最新版本号，避免 OptimisticLockingFailure
            Subscriber current = h2Repository.findById(subscriberId).orElse(null);
            if (current != null) {
                current.markSyncFailed(errorMsg);
                h2Repository.save(current);
            }

            audit.markFailed(errorMsg, System.currentTimeMillis() - startTime);
            auditLogRepository.save(audit);

            log.error("同步订阅者到 Oracle 失败: id={}", subscriberId, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * 同步单个订阅者到 Oracle
     * 委托给 OracleSyncHelper（独立 Bean），确保 @Transactional 代理正常工作
     */
    public void syncSubscriberToOracle(Subscriber subscriber) {
        if (!isOracleAvailable()) {
            return;
        }
        oracleSyncHelper.syncSubscriberToOracle(subscriber);
    }

    // ==================== 定时同步 ====================

    /**
     * 定时增量同步（每 5 分钟）
     * 仅同步 PENDING 和 FAILED（重试次数未超限）的记录
     */
    @Scheduled(fixedRateString = "${app.sync.scheduled-interval:300000}")
    public void scheduledIncrementalSync() {
        if (!isOracleAvailable() || getSyncMode() != SyncMode.SCHEDULED) {
            return;
        }

        log.info("开始定时增量同步...");
        SyncResult result = incrementalSync("SCHEDULED");
        log.info("定时增量同步完成: 成功 {}, 失败 {}, 跳过 {}",
            result.successCount(), result.failCount(), result.skippedCount());
    }

    /**
     * 定时重试失败记录（每 15 分钟）
     * 应用指数退避：interval * backoffMultiplier^(retryCount - 1)
     */
    @Scheduled(fixedRate = 900000)
    public void scheduledRetryFailed() {
        if (!isOracleAvailable()) {
            return;
        }

        List<Subscriber> failedRecords = h2Repository.findFailedForRetry(getMaxRetryCount());
        if (failedRecords.isEmpty()) {
            return;
        }

        // 应用退避过滤：跳过尚未到达退避时间的记录
        long baseInterval = syncProperties.getRetry().getInterval();
        double multiplier = syncProperties.getRetry().getBackoffMultiplier();
        LocalDateTime now = LocalDateTime.now();

        List<Subscriber> eligibleRecords = failedRecords.stream()
            .filter(s -> {
                if (s.getLastSyncedAt() == null) return true;
                // retryCount 从 1 开始，退避指数从 0 开始
                long backoffMs = (long) (baseInterval * Math.pow(multiplier, Math.max(0, s.getSyncRetryCount() - 1)));
                LocalDateTime nextRetryTime = s.getLastSyncedAt().plusNanos(backoffMs * 1_000_000);
                return now.isAfter(nextRetryTime);
            })
            .toList();

        if (eligibleRecords.isEmpty()) {
            log.debug("所有失败记录尚在退避等待中，跳过重试");
            return;
        }

        log.info("开始重试 {} 条失败记录（退避过滤后，原 {} 条）...", eligibleRecords.size(), failedRecords.size());
        SyncResult result = retryFailedRecords(eligibleRecords);
        log.info("重试完成: 成功 {}, 失败 {}", result.successCount(), result.failCount());
    }

    // ==================== 增量同步 ====================

    /**
     * 增量同步：仅同步待同步和可重试的失败记录
     */
    public SyncResult incrementalSync(String triggeredBy) {
        if (!isOracleAvailable()) {
            log.warn("Oracle 未启用，跳过同步");
            return new SyncResult(0, 0, 0, "Oracle 未启用");
        }

        long startTime = System.currentTimeMillis();
        SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.SCHEDULED_INCR, triggeredBy);

        int successCount = 0;
        int failCount = 0;

        // 分页同步 PENDING 记录，避免一次性加载全部到内存
        int pageSize = 200;
        int pageNum = 0;
        int totalRecords = 0;
        Page<Subscriber> pendingPage;
        do {
            pendingPage = h2Repository.findBySyncStatusOrderByCreatedAtAsc(
                Subscriber.SyncStatus.PENDING, PageRequest.of(pageNum, pageSize));
            for (Subscriber subscriber : pendingPage.getContent()) {
                if (syncSingleRecord(subscriber)) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
            totalRecords += pendingPage.getNumberOfElements();
            pageNum++;
        } while (pendingPage.hasNext());

        // 分页重试失败记录
        pageNum = 0;
        Page<Subscriber> failedPage;
        do {
            failedPage = h2Repository.findFailedForRetryPaged(
                getMaxRetryCount(), PageRequest.of(pageNum, pageSize));
            for (Subscriber subscriber : failedPage.getContent()) {
                if (syncSingleRecord(subscriber)) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
            totalRecords += failedPage.getNumberOfElements();
            pageNum++;
        } while (failedPage.hasNext());

        audit.setTotalRecords(totalRecords);

        if (totalRecords == 0) {
            log.debug("没有待同步的记录");
            audit.markCompleted(0, 0, 0, System.currentTimeMillis() - startTime);
            auditLogRepository.save(audit);
            return new SyncResult(0, 0, 0, null);
        }

        long duration = System.currentTimeMillis() - startTime;
        audit.markCompleted(successCount, failCount, 0, duration);
        auditLogRepository.save(audit);

        return new SyncResult(successCount, failCount, 0, null);
    }

    /**
     * 重试失败记录
     */
    private SyncResult retryFailedRecords(List<Subscriber> failedRecords) {
        long startTime = System.currentTimeMillis();
        SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.RETRY_FAILED, "SYSTEM");
        audit.setTotalRecords(failedRecords.size());

        int successCount = 0;
        int failCount = 0;

        for (Subscriber subscriber : failedRecords) {
            if (syncSingleRecord(subscriber)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        audit.markCompleted(successCount, failCount, 0, duration);
        auditLogRepository.save(audit);

        return new SyncResult(successCount, failCount, 0, null);
    }

    // ==================== 全量同步 ====================

    /**
     * 手动触发全量同步
     */
    public SyncResult manualFullSync() {
        return fullSync("MANUAL");
    }

    /**
     * 全量同步：分页同步所有记录，避免一次性加载全部数据到内存
     */
    public SyncResult fullSync(String triggeredBy) {
        if (!isOracleAvailable()) {
            log.warn("Oracle 未启用，跳过同步");
            return new SyncResult(0, 0, 0, "Oracle 未启用");
        }

        long startTime = System.currentTimeMillis();
        SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.MANUAL_FULL, triggeredBy);

        int successCount = 0;
        int failCount = 0;
        int skippedCount = 0;

        long totalCount = h2Repository.count();
        audit.setTotalRecords((int) totalCount);
        log.info("开始全量同步，共 {} 条记录", totalCount);

        int pageSize = 200;
        int pageNum = 0;
        Page<Subscriber> page;

        do {
            page = h2Repository.findAll(PageRequest.of(pageNum, pageSize));

            for (Subscriber subscriber : page.getContent()) {
                // 已同步且未修改的跳过
                if (subscriber.getSyncStatus() == Subscriber.SyncStatus.SYNCED
                    && subscriber.getLastSyncedAt() != null
                    && subscriber.getLastActiveAt() != null
                    && !subscriber.getLastActiveAt().isAfter(subscriber.getLastSyncedAt())) {
                    skippedCount++;
                    continue;
                }
                if (syncSingleRecord(subscriber)) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            pageNum++;
        } while (page.hasNext());

        long duration = System.currentTimeMillis() - startTime;
        audit.markCompleted(successCount, failCount, skippedCount, duration);
        auditLogRepository.save(audit);

        return new SyncResult(successCount, failCount, skippedCount, null);
    }

    // ==================== 从 Oracle 恢复 ====================

    /**
     * 从 Oracle 恢复数据到 H2
     * 每条记录独立事务，避免单条失败导致整批回滚
     */
    public SyncResult restoreFromOracle() {
        if (!isOracleAvailable()) {
            log.warn("Oracle 未启用，无法恢复");
            return new SyncResult(0, 0, 0, "Oracle 未启用");
        }

        long startTime = System.currentTimeMillis();
        SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.RESTORE, "MANUAL");

        int successCount = 0;
        int failCount = 0;

        long totalCount = oracleRepository.count();
        audit.setTotalRecords((int) totalCount);
        log.info("开始从 Oracle 恢复，共 {} 条记录", totalCount);

        int pageSize = 200;
        int pageNum = 0;
        Page<Subscriber> page;

        do {
            page = oracleRepository.findAll(PageRequest.of(pageNum, pageSize));

            for (Subscriber oracleSubscriber : page.getContent()) {
                try {
                    // 每条记录独立事务（通过 OracleSyncHelper 代理调用）
                    oracleSyncHelper.restoreSingleToH2(h2Repository, oracleSubscriber);
                    successCount++;
                } catch (Exception e) {
                    log.error("恢复失败: {}", oracleSubscriber.getEmail(), e);
                    failCount++;
                }
            }

            pageNum++;
        } while (page.hasNext());

        long duration = System.currentTimeMillis() - startTime;
        audit.markCompleted(successCount, failCount, 0, duration);
        auditLogRepository.save(audit);

        return new SyncResult(successCount, failCount, 0, null);
    }

    // ==================== 状态查询 ====================

    /**
     * 获取同步状态
     */
    public SyncStatus getSyncStatus() {
        long h2Count = h2Repository.countByActiveTrue();
        long oracleCount = isOracleAvailable() ? oracleRepository.countByActiveTrue() : -1;
        long pendingCount = h2Repository.countByActiveTrueAndSyncStatus(Subscriber.SyncStatus.PENDING);
        long failedCount = h2Repository.countByActiveTrueAndSyncStatus(Subscriber.SyncStatus.FAILED);
        long syncedCount = h2Repository.countByActiveTrueAndSyncStatus(Subscriber.SyncStatus.SYNCED);

        Optional<SyncAuditLog> lastSync = auditLogRepository.findFirstByStatusOrderBySyncTimeDesc(SyncAuditLog.Status.SUCCESS);
        LocalDateTime lastSyncTime = lastSync.map(SyncAuditLog::getSyncTime).orElse(null);

        return new SyncStatus(
            isOracleAvailable(),
            getSyncMode(),
            h2Count,
            oracleCount,
            pendingCount,
            failedCount,
            syncedCount,
            lastSyncTime
        );
    }

    /**
     * 获取最近的同步审计日志
     */
    public List<SyncAuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop10ByOrderBySyncTimeDesc();
    }

    // ==================== 辅助方法 ====================

    /**
     * 同步单条记录到 Oracle
     * @return true 表示同步成功，false 表示失败
     */
    private boolean syncSingleRecord(Subscriber subscriber) {
        try {
            syncSubscriberToOracle(subscriber);
            subscriber.markSyncSuccess();
            h2Repository.save(subscriber);
            return true;
        } catch (Exception e) {
            log.error("同步失败: {}", subscriber.getEmail(), e);
            subscriber.markSyncFailed(e.getMessage());
            h2Repository.save(subscriber);
            return false;
        }
    }

    // ==================== 记录类型 ====================

    public record SyncResult(int successCount, int failCount, int skippedCount, String error) {
        public boolean hasError() {
            return error != null && !error.isEmpty();
        }

        public int totalProcessed() {
            return successCount + failCount;
        }
    }

    public record SyncStatus(
        boolean oracleAvailable,
        SyncMode syncMode,
        long h2Count,
        long oracleCount,
        long pendingCount,
        long failedCount,
        long syncedCount,
        LocalDateTime lastSyncTime
    ) {
        public boolean isInSync() {
            return oracleAvailable && pendingCount == 0 && failedCount == 0;
        }
    }
}
