package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
import com.wontlost.ckeditor.service.DataSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSyncService 集成测试
 * 验证同步服务与 H2 数据库的交互、审计日志写入、同步状态查询
 * Oracle 禁用 — 聚焦 H2 侧的行为
 */
@DisplayName("数据同步服务集成测试")
class DataSyncServiceIT extends IntegrationTestBase {

    @Autowired
    private DataSyncService syncService;

    // ==================== saveWithSync ====================

    @Nested
    @DisplayName("saveWithSync")
    class SaveWithSync {

        @Test
        @DisplayName("应保存到 H2 并标记为 PENDING（Oracle 不可用时）")
        void saveWithSync_oracleDisabled_shouldSaveToH2AsPending() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);

            Subscriber saved = syncService.saveWithSync(sub);

            assertNotNull(saved.getId());
            assertEquals("test@example.com", saved.getEmail());
            assertEquals(Subscriber.SyncStatus.PENDING, saved.getSyncStatus());

            // 验证数据库中确实存在
            assertTrue(h2Repository.findByEmail("test@example.com").isPresent());
        }

        @Test
        @DisplayName("多次保存不同用户应全部持久化")
        void saveWithSync_multipleUsers_shouldPersistAll() {
            for (int i = 0; i < 10; i++) {
                Subscriber sub = new Subscriber("user" + i + "@test.com", SubscriptionSource.COPY_CODE);
                syncService.saveWithSync(sub);
            }

            assertEquals(10, h2Repository.count());
            // Oracle 不可用，全部应为 PENDING
            assertEquals(10, h2Repository.countBySyncStatus(Subscriber.SyncStatus.PENDING));
        }
    }

    // ==================== 增量同步 ====================

    @Nested
    @DisplayName("增量同步（Oracle 不可用）")
    class IncrementalSyncDisabled {

        @Test
        @DisplayName("Oracle 不可用时应返回错误结果")
        void incrementalSync_oracleDisabled_shouldReturnError() {
            DataSyncService.SyncResult result = syncService.incrementalSync("TEST");

            assertTrue(result.hasError());
            assertEquals("Oracle 未启用", result.error());
            assertEquals(0, result.successCount());
            assertEquals(0, result.failCount());
        }
    }

    // ==================== 全量同步 ====================

    @Nested
    @DisplayName("全量同步（Oracle 不可用）")
    class FullSyncDisabled {

        @Test
        @DisplayName("Oracle 不可用时应返回错误结果")
        void fullSync_oracleDisabled_shouldReturnError() {
            DataSyncService.SyncResult result = syncService.manualFullSync();

            assertTrue(result.hasError());
            assertEquals("Oracle 未启用", result.error());
        }
    }

    // ==================== 从 Oracle 恢复 ====================

    @Nested
    @DisplayName("从 Oracle 恢复（Oracle 不可用）")
    class RestoreDisabled {

        @Test
        @DisplayName("Oracle 不可用时应返回错误结果")
        void restore_oracleDisabled_shouldReturnError() {
            DataSyncService.SyncResult result = syncService.restoreFromOracle();

            assertTrue(result.hasError());
            assertEquals("Oracle 未启用", result.error());
        }
    }

    // ==================== 同步状态查询 ====================

    @Nested
    @DisplayName("同步状态查询")
    class SyncStatusQuery {

        @Test
        @DisplayName("getSyncStatus 应正确统计各状态记录数")
        void getSyncStatus_shouldReturnCorrectCounts() {
            // 创建不同同步状态的记录
            Subscriber pending1 = new Subscriber("p1@test.com", SubscriptionSource.COPY_CODE);
            pending1.markSyncPending();
            h2Repository.save(pending1);

            Subscriber pending2 = new Subscriber("p2@test.com", SubscriptionSource.COPY_CODE);
            pending2.markSyncPending();
            h2Repository.save(pending2);

            Subscriber synced = new Subscriber("s1@test.com", SubscriptionSource.COPY_CODE);
            synced.markSyncSuccess();
            h2Repository.save(synced);

            Subscriber failed = new Subscriber("f1@test.com", SubscriptionSource.COPY_CODE);
            failed.markSyncFailed("error");
            h2Repository.save(failed);

            DataSyncService.SyncStatus status = syncService.getSyncStatus();

            assertEquals(4, status.h2Count());
            assertEquals(-1, status.oracleCount()); // Oracle 不可用
            assertEquals(2, status.pendingCount());
            assertEquals(1, status.syncedCount());
            assertEquals(1, status.failedCount());
            assertFalse(status.oracleAvailable());
            assertFalse(status.isInSync()); // 有 pending/failed 记录
        }

        @Test
        @DisplayName("空数据库的 getSyncStatus 应全为 0")
        void getSyncStatus_empty_shouldReturnZeros() {
            DataSyncService.SyncStatus status = syncService.getSyncStatus();

            assertEquals(0, status.h2Count());
            assertEquals(0, status.pendingCount());
            assertEquals(0, status.failedCount());
            assertEquals(0, status.syncedCount());
        }
    }

    // ==================== 审计日志 ====================

    @Nested
    @DisplayName("审计日志")
    class AuditLog {

        @Test
        @DisplayName("getRecentAuditLogs 初始应为空")
        void getRecentAuditLogs_empty_shouldReturnEmptyList() {
            List<SyncAuditLog> logs = syncService.getRecentAuditLogs();
            assertTrue(logs.isEmpty());
        }

        @Test
        @DisplayName("审计日志应持久化到数据库")
        void auditLog_shouldBePersisted() {
            // 手动创建审计日志
            SyncAuditLog log = new SyncAuditLog(SyncAuditLog.SyncType.MANUAL_FULL, "TEST");
            log.markCompleted(5, 1, 2, 100L);
            auditLogRepository.save(log);

            List<SyncAuditLog> logs = syncService.getRecentAuditLogs();
            assertEquals(1, logs.size());

            SyncAuditLog saved = logs.get(0);
            assertEquals(SyncAuditLog.SyncType.MANUAL_FULL, saved.getSyncType());
            assertEquals(SyncAuditLog.Status.PARTIAL, saved.getStatus()); // 有 failCount
            assertEquals(5, saved.getSuccessCount());
            assertEquals(1, saved.getFailCount());
            assertEquals(2, saved.getSkippedCount());
            assertEquals(100L, saved.getDurationMs());
            assertEquals("TEST", saved.getTriggeredBy());
        }

        @Test
        @DisplayName("审计日志应按时间倒序排列，最多 10 条")
        void auditLogs_shouldBeLimitedAndOrdered() {
            for (int i = 0; i < 15; i++) {
                SyncAuditLog log = new SyncAuditLog(SyncAuditLog.SyncType.SCHEDULED_INCR, "TEST-" + i);
                log.markCompleted(i, 0, 0, i * 10L);
                auditLogRepository.save(log);
            }

            List<SyncAuditLog> logs = syncService.getRecentAuditLogs();
            assertEquals(10, logs.size());
            // 最新的在前
            assertTrue(logs.get(0).getSyncTime().isAfter(logs.get(9).getSyncTime())
                || logs.get(0).getSyncTime().isEqual(logs.get(9).getSyncTime()));
        }
    }

    // ==================== 同步状态标记 ====================

    @Nested
    @DisplayName("订阅者同步状态持久化")
    class SyncStatusPersistence {

        @Test
        @DisplayName("markSyncFailed 应持久化失败状态和重试计数")
        void syncFailed_shouldPersistStateAndRetryCount() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            sub = h2Repository.save(sub);

            sub.markSyncFailed("connection timeout");
            h2Repository.save(sub);

            Subscriber fromDb = h2Repository.findByEmail("test@example.com").orElseThrow();
            assertEquals(Subscriber.SyncStatus.FAILED, fromDb.getSyncStatus());
            assertEquals(1, fromDb.getSyncRetryCount());
            assertEquals("connection timeout", fromDb.getLastSyncError());
        }

        @Test
        @DisplayName("markSyncSuccess 应清除错误并重置重试计数")
        void syncSuccess_shouldClearErrorAndResetRetry() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            sub = h2Repository.save(sub);

            // 模拟多次失败
            sub.markSyncFailed("error1");
            sub.markSyncFailed("error2");
            sub = h2Repository.save(sub);

            // 然后成功
            sub.markSyncSuccess();
            h2Repository.save(sub);

            Subscriber fromDb = h2Repository.findByEmail("test@example.com").orElseThrow();
            assertEquals(Subscriber.SyncStatus.SYNCED, fromDb.getSyncStatus());
            assertEquals(0, fromDb.getSyncRetryCount());
            assertNull(fromDb.getLastSyncError());
            assertNotNull(fromDb.getLastSyncedAt());
        }

        @Test
        @DisplayName("findFailedForRetry 应排除超出重试次数的记录")
        void findFailedForRetry_shouldExcludeExhausted() {
            // 重试未超限
            Subscriber retryable = new Subscriber("retry@test.com", SubscriptionSource.COPY_CODE);
            retryable.markSyncFailed("err");
            h2Repository.save(retryable);

            // 重试已超限 (3次)
            Subscriber exhausted = new Subscriber("exhausted@test.com", SubscriptionSource.COPY_CODE);
            exhausted.markSyncFailed("err1");
            exhausted.markSyncFailed("err2");
            exhausted.markSyncFailed("err3");
            h2Repository.save(exhausted);

            List<Subscriber> retryList = h2Repository.findFailedForRetry(3);
            assertEquals(1, retryList.size());
            assertEquals("retry@test.com", retryList.get(0).getEmail());
        }
    }
}
