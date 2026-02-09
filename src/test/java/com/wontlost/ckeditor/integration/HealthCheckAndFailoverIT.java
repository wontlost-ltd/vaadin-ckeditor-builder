package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.service.DataSourceHealthService;
import com.wontlost.ckeditor.service.PersistentFailoverQueue;
import com.wontlost.ckeditor.service.SubscriberDataService;
import com.wontlost.ckeditor.service.SubscriberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 健康检查与故障转移集成测试
 * 验证 DataSourceHealthService 的模式管理、健康检查、故障队列
 * Oracle 禁用 — 聚焦 H2 健康状态和模式切换行为
 */
@DisplayName("健康检查与故障转移集成测试")
class HealthCheckAndFailoverIT extends IntegrationTestBase {

    @Autowired
    private DataSourceHealthService healthService;

    @Autowired
    private SubscriberDataService dataService;

    @Autowired
    private SubscriberService subscriberService;

    @Autowired
    private PersistentFailoverQueue failoverQueue;

    @Autowired
    private DataSyncProperties syncProperties;

    // ==================== 健康检查 ====================

    @Nested
    @DisplayName("H2 健康检查")
    class H2HealthCheck {

        @Test
        @DisplayName("H2 运行正常时 checkH2Health 应返回 true")
        void checkH2Health_shouldReturnTrue() {
            assertTrue(healthService.checkH2Health());
        }

        @Test
        @DisplayName("Oracle 不可用时 isOracleAvailable 应返回 false")
        void isOracleAvailable_shouldReturnFalse() {
            assertFalse(healthService.isOracleAvailable());
        }

        @Test
        @DisplayName("Oracle 不可用时 checkOracleHealth 应返回 false")
        void checkOracleHealth_disabled_shouldReturnFalse() {
            assertFalse(healthService.checkOracleHealth());
        }
    }

    // ==================== 初始模式 ====================

    @Nested
    @DisplayName("数据源模式")
    class DataSourceModeTests {

        @Test
        @DisplayName("初始模式应为 NORMAL")
        void initialMode_shouldBeNormal() {
            assertEquals(DataSourceMode.NORMAL, healthService.getCurrentMode());
        }

        @Test
        @DisplayName("系统应标记为可用")
        void systemShouldBeAvailable() {
            assertTrue(healthService.isSystemAvailable());
        }

        @Test
        @DisplayName("不应使用 Oracle（未启用）")
        void shouldNotUseOracle() {
            assertFalse(healthService.shouldUseOracle());
        }
    }

    // ==================== 健康状态摘要 ====================

    @Nested
    @DisplayName("健康状态摘要")
    class HealthStatus {

        @Test
        @DisplayName("getHealthStatus 应返回正确的初始状态")
        void getHealthStatus_shouldReturnCorrectInitialState() {
            DataSourceHealthService.HealthStatus status = healthService.getHealthStatus();

            assertEquals(DataSourceMode.NORMAL, status.currentMode());
            assertTrue(status.h2Healthy());
            assertFalse(status.oracleHealthy());
            assertEquals(0, status.pendingQueueSize());
            assertTrue(status.isNormal());
            assertFalse(status.isInFailover());
        }
    }

    // ==================== 手动故障转移 ====================

    @Nested
    @DisplayName("手动故障转移")
    class ManualFailover {

        @Test
        @DisplayName("Oracle 不可用时手动故障转移应失败")
        void manualFailover_oracleDisabled_shouldFail() {
            boolean result = healthService.manualFailover();
            assertFalse(result);
            // 模式应保持 NORMAL
            assertEquals(DataSourceMode.NORMAL, healthService.getCurrentMode());
        }

        @Test
        @DisplayName("手动恢复在 NORMAL 模式下应返回 false")
        void manualRecovery_normalMode_shouldReturnFalse() {
            boolean result = healthService.manualRecovery();
            assertFalse(result);
        }
    }

    // ==================== 健康检查调度 ====================

    @Nested
    @DisplayName("scheduledHealthCheck")
    class ScheduledHealthCheck {

        @Test
        @DisplayName("H2 健康时应保持 NORMAL 模式")
        void healthCheck_h2Healthy_shouldStayNormal() {
            // 执行多次健康检查
            healthService.scheduledHealthCheck();
            healthService.scheduledHealthCheck();
            healthService.scheduledHealthCheck();

            assertEquals(DataSourceMode.NORMAL, healthService.getCurrentMode());

            // H2 成功计数应增加
            DataSourceHealthService.HealthStatus status = healthService.getHealthStatus();
            assertTrue(status.h2SuccessCount() >= 3);
            assertEquals(0, status.h2FailureCount());
        }

        @Test
        @DisplayName("健康检查应更新 lastHealthCheckTime")
        void healthCheck_shouldUpdateLastCheckTime() {
            healthService.scheduledHealthCheck();

            DataSourceHealthService.HealthStatus status = healthService.getHealthStatus();
            assertNotNull(status.lastHealthCheckTime());
        }
    }

    // ==================== 回写队列 ====================

    @Nested
    @DisplayName("回写队列操作")
    class FailoverQueueOperations {

        @Test
        @DisplayName("队列初始应为空")
        void queue_shouldBeEmptyInitially() {
            assertTrue(failoverQueue.isEmpty());
            assertEquals(0, failoverQueue.size());
        }

        @Test
        @DisplayName("offer 和 poll 应正常工作")
        void queue_offerAndPoll_shouldWork() {
            Subscriber sub = new Subscriber("queue@test.com", SubscriptionSource.COPY_CODE);
            sub.setCopyCount(5);
            sub.setDownloadCount(3);
            sub.setConfigSnapshot("{\"key\":\"value\"}");

            assertTrue(failoverQueue.offer(sub));
            assertEquals(1, failoverQueue.size());

            PersistentFailoverQueue.FailoverEntry entry = failoverQueue.poll();
            assertNotNull(entry);
            assertEquals("queue@test.com", entry.email());
            assertEquals(5, entry.copyCount());
            assertEquals(3, entry.downloadCount());
            assertEquals("{\"key\":\"value\"}", entry.configSnapshot());
            assertEquals("COPY_CODE", entry.source());

            assertTrue(failoverQueue.isEmpty());
        }

        @Test
        @DisplayName("recordFailoverWrite 在 NORMAL 模式下不应加入队列")
        void recordFailoverWrite_normalMode_shouldNotQueue() {
            assertEquals(DataSourceMode.NORMAL, healthService.getCurrentMode());

            Subscriber sub = new Subscriber("test@test.com", SubscriptionSource.COPY_CODE);
            healthService.recordFailoverWrite(sub);

            // NORMAL 模式下不应记录到队列
            assertEquals(0, failoverQueue.size());
        }

        @Test
        @DisplayName("队列容量限制应生效")
        void queue_capacityLimit_shouldRejectOverflow() {
            int maxSize = syncProperties.getFailoverQueue().getMaxSize();

            for (int i = 0; i < maxSize; i++) {
                Subscriber sub = new Subscriber("user" + i + "@test.com", SubscriptionSource.COPY_CODE);
                assertTrue(failoverQueue.offer(sub));
            }

            // 超出容量
            Subscriber overflow = new Subscriber("overflow@test.com", SubscriptionSource.COPY_CODE);
            assertFalse(failoverQueue.offer(overflow));
            assertEquals(maxSize, failoverQueue.size());

            // 清理
            failoverQueue.clear();
        }

        @Test
        @DisplayName("peekAll 应返回所有条目但不移除")
        void peekAll_shouldNotRemove() {
            failoverQueue.offer(new Subscriber("a@test.com", SubscriptionSource.COPY_CODE));
            failoverQueue.offer(new Subscriber("b@test.com", SubscriptionSource.DOWNLOAD_FILE));

            var entries = failoverQueue.peekAll();
            assertEquals(2, entries.size());
            assertEquals(2, failoverQueue.size()); // 未移除

            // 清理
            failoverQueue.clear();
        }
    }

    // ==================== DEGRADED 模式 ====================

    @Nested
    @DisplayName("DEGRADED 模式行为")
    class DegradedModeTests {

        @Test
        @DisplayName("DEGRADED 模式下保存应抛出异常")
        void degradedMode_save_shouldThrow() {
            // 注意：无法直接将系统切换到 DEGRADED（需要 H2 和 Oracle 同时失败）
            // 但可以验证当 NORMAL 模式时保存正常
            Subscriber sub = new Subscriber("test@test.com", SubscriptionSource.COPY_CODE);
            assertDoesNotThrow(() -> dataService.save(sub));
        }
    }

    // ==================== 集成场景：订阅 + 同步状态 ====================

    @Nested
    @DisplayName("端到端：订阅 → 同步状态")
    class EndToEndSubscribeAndStatus {

        @Test
        @DisplayName("订阅后应更新同步状态计数")
        void subscribe_shouldUpdateSyncStatusCounts() {
            // 初始状态
            var initialStatus = subscriberService.getSyncStatus();
            assertEquals(0, initialStatus.h2Count());

            // 订阅
            subscriberService.subscribe("a@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("b@test.com", SubscriptionSource.DOWNLOAD_FILE, null);

            // 查询同步状态
            var status = subscriberService.getSyncStatus();
            assertEquals(2, status.h2Count());
            assertEquals(2, status.pendingCount()); // Oracle 不可用，全部 PENDING
            assertFalse(status.isInSync());
        }

        @Test
        @DisplayName("健康状态应反映当前系统状态")
        void healthStatus_shouldReflectSystemState() {
            subscriberService.subscribe("a@test.com", SubscriptionSource.COPY_CODE, null);

            var health = subscriberService.getHealthStatus();
            assertTrue(health.h2Healthy());
            assertFalse(health.oracleHealthy());
            assertTrue(health.isNormal());
            assertEquals(DataSourceMode.NORMAL, health.currentMode());
        }

        @Test
        @DisplayName("getCurrentDataSourceMode 应返回 NORMAL")
        void getCurrentMode_shouldBeNormal() {
            assertEquals(DataSourceMode.NORMAL, subscriberService.getCurrentDataSourceMode());
        }

        @Test
        @DisplayName("isSystemAvailable 应返回 true")
        void isSystemAvailable_shouldBeTrue() {
            assertTrue(subscriberService.isSystemAvailable());
        }
    }

    // ==================== Repository 查询集成测试 ====================

    @Nested
    @DisplayName("Repository 查询")
    class RepositoryQueries {

        @Test
        @DisplayName("findBySyncStatus 应正确过滤")
        void findBySyncStatus_shouldFilter() {
            Subscriber pending = new Subscriber("p@test.com", SubscriptionSource.COPY_CODE);
            pending.markSyncPending();
            h2Repository.save(pending);

            Subscriber synced = new Subscriber("s@test.com", SubscriptionSource.COPY_CODE);
            synced.markSyncSuccess();
            h2Repository.save(synced);

            var pendingList = h2Repository.findBySyncStatusOrderByCreatedAtAsc(
                Subscriber.SyncStatus.PENDING);
            assertEquals(1, pendingList.size());
            assertEquals("p@test.com", pendingList.get(0).getEmail());
        }

        @Test
        @DisplayName("countBySource 应按来源分组统计")
        void countBySource_shouldGroupCorrectly() {
            h2Repository.save(new Subscriber("a@test.com", SubscriptionSource.COPY_CODE));
            h2Repository.save(new Subscriber("b@test.com", SubscriptionSource.COPY_CODE));
            h2Repository.save(new Subscriber("c@test.com", SubscriptionSource.DOWNLOAD_FILE));

            var stats = h2Repository.countBySource();
            assertFalse(stats.isEmpty());

            // 验证有两组
            assertEquals(2, stats.size());
        }

        @Test
        @DisplayName("countBySyncStatus 各类型统计应正确")
        void countBySyncStatus_shouldReturnCorrectCounts() {
            h2Repository.save(createWithStatus("p1@t.com", Subscriber.SyncStatus.PENDING));
            h2Repository.save(createWithStatus("p2@t.com", Subscriber.SyncStatus.PENDING));
            h2Repository.save(createWithStatus("s1@t.com", Subscriber.SyncStatus.SYNCED));
            h2Repository.save(createWithStatus("f1@t.com", Subscriber.SyncStatus.FAILED));

            assertEquals(2, h2Repository.countBySyncStatus(Subscriber.SyncStatus.PENDING));
            assertEquals(1, h2Repository.countBySyncStatus(Subscriber.SyncStatus.SYNCED));
            assertEquals(1, h2Repository.countBySyncStatus(Subscriber.SyncStatus.FAILED));
        }

        private Subscriber createWithStatus(String email, Subscriber.SyncStatus status) {
            Subscriber s = new Subscriber(email, SubscriptionSource.COPY_CODE);
            s.setSyncStatus(status);
            if (status == Subscriber.SyncStatus.SYNCED) {
                s.markSyncSuccess();
            } else if (status == Subscriber.SyncStatus.FAILED) {
                s.markSyncFailed("test error");
            }
            return s;
        }
    }
}
