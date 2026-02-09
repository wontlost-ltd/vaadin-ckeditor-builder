package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.service.DataSyncService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地全链路集成测试
 * 验证真实 H2 文件数据库 + ckeditorlocal Oracle ATP 双数据源同步
 *
 * 运行方式: ./gradlew localTest
 * 前提条件: .env 中配置 ORACLE_PASSWORD，wallet/dev 目录存在有效钱包文件
 */
@DisplayName("本地双数据源同步测试")
@Tag("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalDataSyncIT extends LocalIntegrationTestBase {

    @Autowired
    private DataSyncService syncService;

    @Test
    @Order(1)
    @DisplayName("Oracle 应已启用且可用")
    void oracleShouldBeAvailable() {
        assertTrue(syncService.isOracleAvailable(),
            "Oracle 应已启用。请确认 .env 中配置了 ORACLE_PASSWORD 且 wallet/dev 钱包有效");
    }

    @Nested
    @DisplayName("saveWithSync — 实时同步")
    class SaveWithSyncTests {

        @Test
        @DisplayName("保存后应同步到 H2 和 Oracle")
        void saveWithSync_shouldSyncToBothDatabases() throws Exception {
            Subscriber sub = new Subscriber("local-sync@test.com", SubscriptionSource.COPY_CODE);

            Subscriber saved = syncService.saveWithSync(sub);

            assertNotNull(saved.getId());
            assertEquals("local-sync@test.com", saved.getEmail());

            // H2 应有记录
            assertTrue(h2Repository.findByEmail("local-sync@test.com").isPresent());

            // 等待异步同步完成（REALTIME 模式通过 @Async 同步）
            Thread.sleep(3000);

            // Oracle 应有记录
            Optional<Subscriber> oracleSub = oracleRepository.findByEmail("local-sync@test.com");
            assertTrue(oracleSub.isPresent(), "Oracle 中应有同步过来的记录");
            assertEquals("local-sync@test.com", oracleSub.get().getEmail());

            // H2 中同步状态应为 SYNCED
            Subscriber h2Sub = h2Repository.findByEmail("local-sync@test.com").orElseThrow();
            assertEquals(Subscriber.SyncStatus.SYNCED, h2Sub.getSyncStatus(),
                "实时同步完成后状态应为 SYNCED");
        }

        @Test
        @DisplayName("多条记录保存后应全部同步")
        void saveMultiple_shouldSyncAll() throws Exception {
            for (int i = 0; i < 5; i++) {
                Subscriber sub = new Subscriber("multi-" + i + "@test.com", SubscriptionSource.COPY_CODE);
                syncService.saveWithSync(sub);
            }

            // 等待异步同步
            Thread.sleep(5000);

            assertEquals(5, h2Repository.count());
            assertEquals(5, oracleRepository.count());
        }
    }

    @Nested
    @DisplayName("incrementalSync — 增量同步")
    class IncrementalSyncTests {

        @Test
        @DisplayName("增量同步应同步 PENDING 记录到 Oracle")
        void incrementalSync_shouldSyncPendingRecords() {
            // 直接在 H2 创建 PENDING 记录（绕过 saveWithSync 的实时同步）
            for (int i = 0; i < 3; i++) {
                Subscriber sub = new Subscriber("incr-" + i + "@test.com", SubscriptionSource.DOWNLOAD_FILE);
                sub.markSyncPending();
                h2Repository.save(sub);
            }

            assertEquals(3, h2Repository.countBySyncStatus(Subscriber.SyncStatus.PENDING));

            DataSyncService.SyncResult result = syncService.incrementalSync("LOCAL-TEST");

            assertFalse(result.hasError());
            assertEquals(3, result.successCount());
            assertEquals(0, result.failCount());

            // Oracle 应有全部 3 条
            assertEquals(3, oracleRepository.count());

            // H2 中状态应全部变为 SYNCED
            assertEquals(0, h2Repository.countBySyncStatus(Subscriber.SyncStatus.PENDING));
            assertEquals(3, h2Repository.countBySyncStatus(Subscriber.SyncStatus.SYNCED));
        }
    }

    @Nested
    @DisplayName("getSyncStatus — 同步状态查询")
    class SyncStatusTests {

        @Test
        @DisplayName("同步完成后 H2 和 Oracle 计数应一致")
        void getSyncStatus_afterSync_shouldBeInSync() {
            // 保存并增量同步
            for (int i = 0; i < 3; i++) {
                Subscriber sub = new Subscriber("status-" + i + "@test.com", SubscriptionSource.COPY_CODE);
                sub.markSyncPending();
                h2Repository.save(sub);
            }
            syncService.incrementalSync("LOCAL-TEST");

            DataSyncService.SyncStatus status = syncService.getSyncStatus();

            assertTrue(status.oracleAvailable());
            assertEquals(status.h2Count(), status.oracleCount(),
                "同步完成后 H2 和 Oracle 活跃记录数应一致");
            assertEquals(0, status.pendingCount());
            assertEquals(0, status.failedCount());
            assertTrue(status.isInSync());
        }
    }

    @Nested
    @DisplayName("restoreFromOracle — 从 Oracle 恢复")
    class RestoreFromOracleTests {

        @Test
        @DisplayName("从 Oracle 恢复到 H2 应成功")
        void restoreFromOracle_shouldRestoreData() {
            // 先同步数据到 Oracle
            for (int i = 0; i < 3; i++) {
                Subscriber sub = new Subscriber("restore-" + i + "@test.com", SubscriptionSource.COPY_CODE);
                sub.markSyncPending();
                h2Repository.save(sub);
            }
            DataSyncService.SyncResult syncResult = syncService.incrementalSync("LOCAL-TEST");
            assertEquals(3, syncResult.successCount());

            // 清空 H2（模拟 H2 数据丢失）
            h2Repository.deleteAll();
            assertEquals(0, h2Repository.count());

            // Oracle 仍有数据
            assertEquals(3, oracleRepository.count());

            // 从 Oracle 恢复
            DataSyncService.SyncResult restoreResult = syncService.restoreFromOracle();

            assertFalse(restoreResult.hasError());
            assertEquals(3, restoreResult.successCount());
            assertEquals(0, restoreResult.failCount());

            // H2 应恢复全部数据
            assertEquals(3, h2Repository.count());
            for (int i = 0; i < 3; i++) {
                assertTrue(h2Repository.findByEmail("restore-" + i + "@test.com").isPresent(),
                    "restore-" + i + "@test.com 应已恢复到 H2");
            }
        }
    }
}
