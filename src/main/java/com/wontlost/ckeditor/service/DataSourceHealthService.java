package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.h2.SyncAuditLogRepository;
import com.wontlost.ckeditor.repository.oracle.OracleSubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 数据源健康检查与故障转移服务
 *
 * 职责：
 * 1. 定期检查 H2 和 Oracle 的健康状态
 * 2. H2 故障时自动切换到 Oracle
 * 3. H2 恢复后自动回写数据并切回
 * 4. 提供当前数据源模式查询
 */
@Service
public class DataSourceHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthService.class);

    private final H2SubscriberRepository h2Repository;
    private final SyncAuditLogRepository auditLogRepository;
    private final DataSyncProperties syncProperties;
    private final PersistentFailoverQueue failoverQueue;

    @Autowired(required = false)
    private OracleSubscriberRepository oracleRepository;

    // 当前数据源模式
    private final AtomicReference<DataSourceMode> currentMode = new AtomicReference<>(DataSourceMode.NORMAL);

    // H2 连续失败计数
    private final AtomicInteger h2FailureCount = new AtomicInteger(0);
    // H2 连续成功计数（用于恢复判断）
    private final AtomicInteger h2SuccessCount = new AtomicInteger(0);

    // 故障转移开始时间
    private volatile LocalDateTime failoverStartTime;
    // 最后一次健康检查时间
    private volatile LocalDateTime lastHealthCheckTime;
    // 缓存最近的健康检查结果，避免 getHealthStatus() 重复查询
    private volatile boolean lastH2Healthy = true;
    private volatile boolean lastOracleHealthy = false;

    public DataSourceHealthService(
            H2SubscriberRepository h2Repository,
            SyncAuditLogRepository auditLogRepository,
            DataSyncProperties syncProperties,
            PersistentFailoverQueue failoverQueue) {
        this.h2Repository = h2Repository;
        this.auditLogRepository = auditLogRepository;
        this.syncProperties = syncProperties;
        this.failoverQueue = failoverQueue;
    }

    // ==================== 健康检查 ====================

    /**
     * 定时健康检查（每 30 秒）
     */
    @Scheduled(fixedRate = 30000)
    public void scheduledHealthCheck() {
        lastHealthCheckTime = LocalDateTime.now();

        boolean h2Healthy = checkH2Health();
        boolean oracleHealthy = isOracleAvailable() && checkOracleHealth();
        // 缓存健康检查结果供 getHealthStatus() 使用
        this.lastH2Healthy = h2Healthy;
        this.lastOracleHealthy = oracleHealthy;

        // 使用局部变量缓存模式快照，避免多次读取的 TOCTOU 问题
        DataSourceMode mode = currentMode.get();
        DataSourceMode previousMode = mode;

        // 根据健康状态决定模式
        if (h2Healthy) {
            h2SuccessCount.incrementAndGet();
            h2FailureCount.set(0);

            if (mode == DataSourceMode.FAILOVER) {
                // H2 恢复，检查是否达到恢复阈值
                int threshold = syncProperties.getHealthCheck().getRecoveryThreshold();
                if (h2SuccessCount.get() >= threshold) {
                    log.info("H2 已连续 {} 次健康检查成功，开始恢复流程", threshold);
                    initiateRecovery();
                }
            } else if (mode == DataSourceMode.RECOVERY) {
                // 恢复模式下继续保持
                log.debug("H2 健康，恢复模式进行中");
            } else {
                if (!currentMode.compareAndSet(mode, DataSourceMode.NORMAL)) {
                    log.debug("模式 CAS 失败: 预期={}, 实际={}", mode, currentMode.get());
                }
            }
        } else {
            h2FailureCount.incrementAndGet();
            h2SuccessCount.set(0);

            int failureThreshold = syncProperties.getHealthCheck().getFailureThreshold();
            if (h2FailureCount.get() >= failureThreshold) {
                if (oracleHealthy) {
                    if (mode != DataSourceMode.FAILOVER) {
                        log.warn("H2 连续 {} 次健康检查失败，切换到 Oracle 故障转移模式", failureThreshold);
                        initiateFailover();
                    }
                } else {
                    log.error("H2 和 Oracle 都不可用，进入降级模式");
                    currentMode.compareAndSet(mode, DataSourceMode.DEGRADED);
                }
            }
        }

        // 记录模式变更
        DataSourceMode newMode = currentMode.get();
        if (previousMode != newMode) {
            logModeChange(previousMode, newMode);
        }
    }

    /**
     * 检查 H2 健康状态
     */
    public boolean checkH2Health() {
        try {
            // 执行简单查询测试连接
            h2Repository.count();
            return true;
        } catch (Exception e) {
            log.warn("H2 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Oracle 健康状态
     */
    public boolean checkOracleHealth() {
        if (!isOracleAvailable()) {
            return false;
        }
        try {
            oracleRepository.count();
            return true;
        } catch (Exception e) {
            log.warn("Oracle 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 故障转移 ====================

    /**
     * 启动故障转移
     */
    private void initiateFailover() {
        DataSourceMode prev = currentMode.get();
        if (prev == DataSourceMode.FAILOVER) {
            log.debug("已在故障转移模式，跳过");
            return;
        }
        if (!currentMode.compareAndSet(prev, DataSourceMode.FAILOVER)) {
            log.debug("故障转移 CAS 失败，其他线程已修改模式");
            return;
        }
        failoverStartTime = LocalDateTime.now();

        // 记录审计日志
        SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.RESTORE, "FAILOVER_INITIATED");
        audit.setStatus(SyncAuditLog.Status.RUNNING);
        audit.setErrorMessage("H2 故障，切换到 Oracle 作为主数据源");
        try {
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("无法记录故障转移审计日志（H2 不可用）");
        }

        log.warn("============================================");
        log.warn("故障转移已启动");
        log.warn("时间: {}", failoverStartTime);
        log.warn("模式: H2 -> Oracle");
        log.warn("============================================");
    }

    /**
     * 启动恢复流程（异步执行，避免阻塞健康检查线程）
     */
    private void initiateRecovery() {
        if (!currentMode.compareAndSet(DataSourceMode.FAILOVER, DataSourceMode.RECOVERY)) {
            log.debug("恢复 CAS 失败: 当前模式={}, 预期=FAILOVER", currentMode.get());
            return;
        }
        log.info("============================================");
        log.info("开始恢复流程：将 Oracle 数据回写到 H2");
        log.info("============================================");

        CompletableFuture.runAsync(() -> {
            try {
                RecoveryResult result = performRecovery();

                if (result.success()) {
                    currentMode.set(DataSourceMode.NORMAL);
                    failoverStartTime = null;
                    // 队列中可能仍有处理失败的条目（已在 performRecovery 中重新入队）
                    if (!failoverQueue.isEmpty()) {
                        log.warn("恢复完成但仍有 {} 条队列记录处理失败，将在下次恢复时重试", failoverQueue.size());
                    }
                    log.info("============================================");
                    log.info("恢复完成，切回 H2 正常模式");
                    log.info("回写记录数: {}", result.recordsRecovered());
                    log.info("队列回写数: {}", result.queueProcessed());
                    log.info("============================================");

                    // 记录审计日志
                    SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.RESTORE, "RECOVERY_COMPLETED");
                    audit.markCompleted(result.recordsRecovered() + result.queueProcessed(), 0, 0, result.durationMs());
                    auditLogRepository.save(audit);
                } else {
                    log.error("恢复失败: {}，保留故障转移队列数据", result.error());
                    currentMode.compareAndSet(DataSourceMode.RECOVERY, DataSourceMode.FAILOVER);
                    // 不清空 failoverQueue，保留未处理数据以防丢失
                }
            } catch (Exception e) {
                log.error("恢复过程异常，保留故障转移队列数据", e);
                currentMode.compareAndSet(DataSourceMode.RECOVERY, DataSourceMode.FAILOVER);
                // 不清空 failoverQueue，保留未处理数据以防丢失
            }
        });
    }

    /**
     * 执行恢复操作
     */
    private RecoveryResult performRecovery() {
        long startTime = System.currentTimeMillis();
        int recordsRecovered = 0;
        int queueProcessed = 0;

        try {
            // 1. 分页回写故障转移期间 Oracle 中的新增/修改数据（按时间过滤）
            if (failoverStartTime != null && isOracleAvailable()) {
                int pageSize = 200;
                int pageNum = 0;
                Page<Subscriber> page;

                do {
                    page = oracleRepository.findByLastActiveAtAfter(
                        failoverStartTime, PageRequest.of(pageNum, pageSize));

                    for (Subscriber oracleSubscriber : page.getContent()) {
                        try {
                            Optional<Subscriber> existing = h2Repository.findByEmail(oracleSubscriber.getEmail());

                            if (existing.isPresent()) {
                                Subscriber h2Subscriber = existing.get();
                                // 仅当 Oracle 数据更新时才覆盖
                                if (oracleSubscriber.getLastActiveAt() != null &&
                                    (h2Subscriber.getLastActiveAt() == null ||
                                     oracleSubscriber.getLastActiveAt().isAfter(h2Subscriber.getLastActiveAt()))) {
                                    updateSubscriberFields(h2Subscriber, oracleSubscriber);
                                    h2Subscriber.markSyncSuccess();
                                    h2Repository.save(h2Subscriber);
                                    recordsRecovered++;
                                }
                            } else {
                                // H2 中不存在，是故障转移期间新增的
                                Subscriber newSubscriber = copySubscriber(oracleSubscriber);
                                newSubscriber.setId(null);
                                newSubscriber.markSyncSuccess();
                                h2Repository.save(newSubscriber);
                                recordsRecovered++;
                            }
                        } catch (Exception e) {
                            log.error("恢复记录失败: {}", oracleSubscriber.getEmail(), e);
                        }
                    }

                    pageNum++;
                } while (page.hasNext());
            }

            // 2. 处理持久化队列中的待写入数据
            // 逐条 poll 处理，成功则移除，失败则重新入队，避免 JVM 崩溃导致数据丢失
            List<PersistentFailoverQueue.FailoverEntry> failedEntries = new ArrayList<>();
            PersistentFailoverQueue.FailoverEntry entry;

            while ((entry = failoverQueue.poll()) != null) {

                try {
                    Optional<Subscriber> existing = h2Repository.findByEmail(entry.email());
                    if (existing.isPresent()) {
                        Subscriber h2Subscriber = existing.get();
                        applyFailoverEntry(h2Subscriber, entry);
                        h2Repository.save(h2Subscriber);
                    } else {
                        Subscriber newSubscriber = createFromFailoverEntry(entry);
                        h2Repository.save(newSubscriber);
                    }
                    queueProcessed++;
                } catch (Exception e) {
                    log.error("处理队列记录失败: {}", entry.email(), e);
                    failedEntries.add(entry);
                }
            }

            // 将失败条目重新入队，防止数据丢失
            for (PersistentFailoverQueue.FailoverEntry failed : failedEntries) {
                failoverQueue.requeue(failed);
            }

            long duration = System.currentTimeMillis() - startTime;
            return new RecoveryResult(true, recordsRecovered, queueProcessed, duration, null);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new RecoveryResult(false, recordsRecovered, queueProcessed, duration, e.getMessage());
        }
    }

    // ==================== 数据源路由 ====================

    /**
     * 获取当前数据源模式
     */
    public DataSourceMode getCurrentMode() {
        return currentMode.get();
    }

    /**
     * 检查当前是否应该使用 Oracle
     */
    public boolean shouldUseOracle() {
        DataSourceMode mode = currentMode.get();
        return mode == DataSourceMode.FAILOVER && isOracleAvailable();
    }

    /**
     * 检查系统是否可用
     */
    public boolean isSystemAvailable() {
        return currentMode.get() != DataSourceMode.DEGRADED;
    }

    /**
     * Oracle 是否可用
     */
    public boolean isOracleAvailable() {
        return syncProperties.isOracleEnabled() && oracleRepository != null;
    }

    /**
     * 记录故障转移期间的写入（用于恢复时回写）
     */
    public void recordFailoverWrite(Subscriber subscriber) {
        if (currentMode.get() == DataSourceMode.FAILOVER) {
            failoverQueue.offer(subscriber);
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 获取健康状态摘要
     */
    public HealthStatus getHealthStatus() {
        return new HealthStatus(
            currentMode.get(),
            lastH2Healthy,
            lastOracleHealthy,
            h2FailureCount.get(),
            h2SuccessCount.get(),
            failoverStartTime,
            lastHealthCheckTime,
            failoverQueue.size()
        );
    }

    /**
     * 手动触发故障转移（用于测试或紧急情况）
     */
    public boolean manualFailover() {
        if (!isOracleAvailable()) {
            log.error("无法手动故障转移：Oracle 不可用");
            return false;
        }
        initiateFailover();
        return true;
    }

    /**
     * 手动触发恢复（用于测试或紧急情况）
     */
    public boolean manualRecovery() {
        if (!checkH2Health()) {
            log.error("无法手动恢复：H2 不可用");
            return false;
        }
        if (currentMode.get() != DataSourceMode.FAILOVER) {
            log.warn("当前不在故障转移模式，无需恢复");
            return false;
        }
        initiateRecovery();
        return true;
    }

    // ==================== 辅助方法 ====================

    private void logModeChange(DataSourceMode from, DataSourceMode to) {
        log.info("数据源模式变更: {} -> {}", from, to);

        // 尝试记录审计日志
        try {
            SyncAuditLog audit = new SyncAuditLog(SyncAuditLog.SyncType.RESTORE, "MODE_CHANGE");
            audit.setStatus(SyncAuditLog.Status.SUCCESS);
            audit.setErrorMessage(String.format("模式变更: %s -> %s", from, to));
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.debug("无法记录模式变更审计日志");
        }
    }

    private void updateSubscriberFields(Subscriber target, Subscriber source) {
        SubscriberFieldMapper.updateFields(target, source);
    }

    private Subscriber copySubscriber(Subscriber source) {
        return SubscriberFieldMapper.copy(source);
    }

    private void applyFailoverEntry(Subscriber target, PersistentFailoverQueue.FailoverEntry entry) {
        SubscriberFieldMapper.applyFailoverEntry(target, entry);
    }

    private Subscriber createFromFailoverEntry(PersistentFailoverQueue.FailoverEntry entry) {
        return SubscriberFieldMapper.createFromFailoverEntry(entry);
    }

    // ==================== 记录类型 ====================

    public record HealthStatus(
        DataSourceMode currentMode,
        boolean h2Healthy,
        boolean oracleHealthy,
        int h2FailureCount,
        int h2SuccessCount,
        LocalDateTime failoverStartTime,
        LocalDateTime lastHealthCheckTime,
        int pendingQueueSize
    ) {
        public boolean isNormal() {
            return currentMode == DataSourceMode.NORMAL && h2Healthy;
        }

        public boolean isInFailover() {
            return currentMode == DataSourceMode.FAILOVER;
        }
    }

    public record RecoveryResult(
        boolean success,
        int recordsRecovered,
        int queueProcessed,
        long durationMs,
        String error
    ) {}
}
