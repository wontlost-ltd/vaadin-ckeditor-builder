package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.config.SyncMode;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.h2.SyncAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DataSyncService 单元测试
 * 覆盖同步模式、增量同步、重试机制、审计日志等
 */
@ExtendWith(MockitoExtension.class)
class DataSyncServiceTest {

    @Mock
    private H2SubscriberRepository h2Repository;
    @Mock
    private SyncAuditLogRepository auditLogRepository;
    @Mock
    private OracleSyncHelper oracleSyncHelper;

    private DataSyncProperties syncProperties;
    private DataSyncService syncService;

    @BeforeEach
    void setUp() {
        syncProperties = new DataSyncProperties();
        syncProperties.setOracleEnabled(false);
        syncProperties.setMode(SyncMode.REALTIME);
        syncProperties.getRetry().setMaxCount(3);

        syncService = new DataSyncService(h2Repository, auditLogRepository, syncProperties, oracleSyncHelper);
    }

    // ==================== 同步模式 ====================

    @Test
    @DisplayName("getSyncMode 应返回配置的模式")
    void getSyncMode_shouldReturnConfiguredMode() {
        assertEquals(SyncMode.REALTIME, syncService.getSyncMode());

        syncProperties.setMode(SyncMode.SCHEDULED);
        assertEquals(SyncMode.SCHEDULED, syncService.getSyncMode());
    }

    @Test
    @DisplayName("Oracle 未启用时 isOracleAvailable 应返回 false")
    void isOracleAvailable_disabled_shouldReturnFalse() {
        assertFalse(syncService.isOracleAvailable());
    }

    // ==================== saveWithSync ====================

    @Nested
    @DisplayName("saveWithSync")
    class SaveWithSync {

        @Test
        @DisplayName("Oracle 未启用时应仅保存到 H2")
        void oracleDisabled_shouldSaveToH2Only() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            when(h2Repository.save(any())).thenReturn(sub);

            Subscriber result = syncService.saveWithSync(sub);

            assertNotNull(result);
            assertEquals(Subscriber.SyncStatus.PENDING, result.getSyncStatus());
            verify(h2Repository).save(sub);
        }
    }

    // ==================== 增量同步 ====================

    @Nested
    @DisplayName("增量同步")
    class IncrementalSync {

        @Test
        @DisplayName("Oracle 未启用时应跳过同步")
        void oracleDisabled_shouldSkipSync() {
            DataSyncService.SyncResult result = syncService.incrementalSync("TEST");

            assertEquals(0, result.successCount());
            assertEquals(0, result.failCount());
            assertTrue(result.hasError());
            assertEquals("Oracle 未启用", result.error());
        }

        @Test
        @DisplayName("Oracle 启用但无 repository 时应跳过")
        void oracleEnabledButNoRepo_shouldSkip() {
            syncProperties.setOracleEnabled(true);
            // oracleRepository 仍为 null（未注入）
            assertFalse(syncService.isOracleAvailable());
        }
    }

    // ==================== 全量同步 ====================

    @Nested
    @DisplayName("全量同步")
    class FullSync {

        @Test
        @DisplayName("Oracle 未启用时应跳过")
        void oracleDisabled_shouldSkip() {
            DataSyncService.SyncResult result = syncService.manualFullSync();

            assertTrue(result.hasError());
            assertEquals("Oracle 未启用", result.error());
        }
    }

    // ==================== 从 Oracle 恢复 ====================

    @Nested
    @DisplayName("从 Oracle 恢复")
    class RestoreFromOracle {

        @Test
        @DisplayName("Oracle 未启用时应返回错误")
        void oracleDisabled_shouldReturnError() {
            DataSyncService.SyncResult result = syncService.restoreFromOracle();

            assertTrue(result.hasError());
            assertEquals("Oracle 未启用", result.error());
        }
    }

    // ==================== 同步状态查询 ====================

    @Test
    @DisplayName("getSyncStatus 应返回正确统计")
    void getSyncStatus_shouldReturnCorrectStats() {
        when(h2Repository.countByActiveTrue()).thenReturn(100L);
        when(h2Repository.countByActiveTrueAndSyncStatus(Subscriber.SyncStatus.PENDING)).thenReturn(10L);
        when(h2Repository.countByActiveTrueAndSyncStatus(Subscriber.SyncStatus.FAILED)).thenReturn(2L);
        when(h2Repository.countByActiveTrueAndSyncStatus(Subscriber.SyncStatus.SYNCED)).thenReturn(88L);
        when(auditLogRepository.findFirstByStatusOrderBySyncTimeDesc(SyncAuditLog.Status.SUCCESS))
            .thenReturn(Optional.empty());

        DataSyncService.SyncStatus status = syncService.getSyncStatus();

        assertEquals(100L, status.h2Count());
        assertEquals(-1L, status.oracleCount()); // Oracle 不可用
        assertEquals(10L, status.pendingCount());
        assertEquals(2L, status.failedCount());
        assertEquals(88L, status.syncedCount());
        assertFalse(status.isInSync()); // Oracle 不可用
    }

    // ==================== SyncResult 记录类 ====================

    @Test
    @DisplayName("SyncResult 应正确计算总处理数")
    void syncResult_shouldCalculateTotal() {
        var result = new DataSyncService.SyncResult(8, 2, 3, null);
        assertEquals(10, result.totalProcessed());
        assertFalse(result.hasError());
    }

    @Test
    @DisplayName("SyncResult 有错误时 hasError 应返回 true")
    void syncResult_withError_shouldHaveError() {
        var result = new DataSyncService.SyncResult(0, 0, 0, "connection failed");
        assertTrue(result.hasError());
    }
}
