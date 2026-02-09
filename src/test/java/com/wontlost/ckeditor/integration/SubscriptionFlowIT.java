package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.service.SubscriberDataService;
import com.wontlost.ckeditor.service.SubscriberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订阅流程端到端集成测试
 * 使用真实 H2 数据库，验证 SubscriberService → SubscriberDataService → H2Repository 的完整链路
 */
@DisplayName("订阅流程集成测试")
class SubscriptionFlowIT extends IntegrationTestBase {

    @Autowired
    private SubscriberService subscriberService;

    @Autowired
    private SubscriberDataService dataService;

    // ==================== 新用户订阅 ====================

    @Nested
    @DisplayName("新用户订阅")
    class NewSubscription {

        @Test
        @DisplayName("完整订阅流程：创建 → 持久化 → 查询")
        void subscribe_newUser_shouldPersistAndRetrieve() {
            Optional<Subscriber> result = subscriberService.subscribe(
                "alice@example.com", SubscriptionSource.COPY_CODE, null);

            assertTrue(result.isPresent());
            Subscriber subscriber = result.get();
            assertNotNull(subscriber.getId());
            assertEquals("alice@example.com", subscriber.getEmail());
            assertEquals(SubscriptionSource.COPY_CODE, subscriber.getSource());
            assertEquals(1, subscriber.getCopyCount());
            assertEquals(0, subscriber.getDownloadCount());
            assertTrue(subscriber.isActive());

            // 验证数据库持久化
            Optional<Subscriber> fromDb = h2Repository.findByEmail("alice@example.com");
            assertTrue(fromDb.isPresent());
            assertEquals(subscriber.getId(), fromDb.get().getId());
        }

        @Test
        @DisplayName("下载来源订阅应设置 downloadCount=1")
        void subscribe_downloadSource_shouldSetDownloadCount() {
            subscriberService.subscribe("bob@example.com", SubscriptionSource.DOWNLOAD_FILE, null);

            Optional<Subscriber> fromDb = h2Repository.findByEmail("bob@example.com");
            assertTrue(fromDb.isPresent());
            assertEquals(0, fromDb.get().getCopyCount());
            assertEquals(1, fromDb.get().getDownloadCount());
        }

        @Test
        @DisplayName("邮箱应自动规范化为小写")
        void subscribe_shouldNormalizeEmail() {
            subscriberService.subscribe("Alice@EXAMPLE.COM", SubscriptionSource.COPY_CODE, null);

            assertTrue(h2Repository.findByEmail("alice@example.com").isPresent());
            assertFalse(h2Repository.findByEmail("Alice@EXAMPLE.COM").isPresent());
        }

        @Test
        @DisplayName("无效邮箱应返回 empty")
        void subscribe_invalidEmail_shouldReturnEmpty() {
            assertTrue(subscriberService.subscribe(null, SubscriptionSource.COPY_CODE, null).isEmpty());
            assertTrue(subscriberService.subscribe("", SubscriptionSource.COPY_CODE, null).isEmpty());
            assertTrue(subscriberService.subscribe("not-an-email", SubscriptionSource.COPY_CODE, null).isEmpty());

            assertEquals(0, h2Repository.count());
        }
    }

    // ==================== 已有用户再次订阅 ====================

    @Nested
    @DisplayName("已有用户再次订阅")
    class ReSubscription {

        @Test
        @DisplayName("已存在用户复制代码应递增 copyCount")
        void reSubscribe_copyCode_shouldIncrementCopyCount() {
            subscriberService.subscribe("alice@example.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("alice@example.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("alice@example.com", SubscriptionSource.COPY_CODE, null);

            Optional<Subscriber> fromDb = h2Repository.findByEmail("alice@example.com");
            assertTrue(fromDb.isPresent());
            assertEquals(3, fromDb.get().getCopyCount());
            // 数据库中只有一条记录
            assertEquals(1, h2Repository.count());
        }

        @Test
        @DisplayName("已存在用户下载文件应递增 downloadCount")
        void reSubscribe_download_shouldIncrementDownloadCount() {
            subscriberService.subscribe("alice@example.com", SubscriptionSource.DOWNLOAD_FILE, null);
            subscriberService.subscribe("alice@example.com", SubscriptionSource.DOWNLOAD_FILE, null);

            Optional<Subscriber> fromDb = h2Repository.findByEmail("alice@example.com");
            assertTrue(fromDb.isPresent());
            assertEquals(2, fromDb.get().getDownloadCount());
        }
    }

    // ==================== 活动记录 ====================

    @Nested
    @DisplayName("活动记录")
    class ActivityRecording {

        @Test
        @DisplayName("recordActivity 应递增对应计数")
        void recordActivity_shouldIncrementCount() {
            subscriberService.subscribe("alice@example.com", SubscriptionSource.COPY_CODE, null);

            subscriberService.recordActivity("alice@example.com", SubscriptionSource.COPY_CODE);
            subscriberService.recordActivity("alice@example.com", SubscriptionSource.DOWNLOAD_FILE);
            subscriberService.recordActivity("alice@example.com", SubscriptionSource.DOWNLOAD_FILE);

            Optional<Subscriber> fromDb = h2Repository.findByEmail("alice@example.com");
            assertTrue(fromDb.isPresent());
            assertEquals(2, fromDb.get().getCopyCount());   // 初始1 + recordActivity 1
            assertEquals(2, fromDb.get().getDownloadCount()); // recordActivity 2
        }

        @Test
        @DisplayName("recordActivity 对不存在的用户应无操作")
        void recordActivity_nonExistentUser_shouldDoNothing() {
            subscriberService.recordActivity("nobody@example.com", SubscriptionSource.COPY_CODE);
            assertEquals(0, h2Repository.count());
        }
    }

    // ==================== 查询与统计 ====================

    @Nested
    @DisplayName("查询与统计")
    class QueryAndStats {

        @Test
        @DisplayName("isSubscribed 应正确反映订阅状态")
        void isSubscribed_shouldReflectState() {
            assertFalse(subscriberService.isSubscribed("alice@example.com"));

            subscriberService.subscribe("alice@example.com", SubscriptionSource.COPY_CODE, null);

            assertTrue(subscriberService.isSubscribed("alice@example.com"));
            // 大小写不敏感
            assertTrue(subscriberService.isSubscribed("ALICE@example.com"));
        }

        @Test
        @DisplayName("getTotalCount 应返回活跃订阅数")
        void getTotalCount_shouldReturnActiveCount() {
            subscriberService.subscribe("a@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("b@test.com", SubscriptionSource.DOWNLOAD_FILE, null);
            subscriberService.subscribe("c@test.com", SubscriptionSource.MANUAL, null);

            assertEquals(3, subscriberService.getTotalCount());
        }

        @Test
        @DisplayName("getNewCountSince 应返回指定日期后新增数")
        void getNewCountSince_shouldReturnNewSubscribers() {
            subscriberService.subscribe("a@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("b@test.com", SubscriptionSource.DOWNLOAD_FILE, null);

            // 今天之后的应该有 2 条
            long count = subscriberService.getNewCountSince(LocalDate.now().minusDays(1));
            assertEquals(2, count);

            // 明天之后应该有 0 条
            count = subscriberService.getNewCountSince(LocalDate.now().plusDays(1));
            assertEquals(0, count);
        }

        @Test
        @DisplayName("getSubscribers 分页查询应正确返回")
        void getSubscribers_shouldReturnPaged() {
            for (int i = 0; i < 15; i++) {
                subscriberService.subscribe("user" + i + "@test.com", SubscriptionSource.COPY_CODE, null);
            }

            Page<Subscriber> page1 = subscriberService.getSubscribers(PageRequest.of(0, 10));
            assertEquals(10, page1.getContent().size());
            assertEquals(15, page1.getTotalElements());
            assertEquals(2, page1.getTotalPages());

            Page<Subscriber> page2 = subscriberService.getSubscribers(PageRequest.of(1, 10));
            assertEquals(5, page2.getContent().size());
        }

        @Test
        @DisplayName("getSourceStats 应按来源统计")
        void getSourceStats_shouldGroupBySource() {
            subscriberService.subscribe("a@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("b@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("c@test.com", SubscriptionSource.DOWNLOAD_FILE, null);

            var stats = subscriberService.getSourceStats();
            assertEquals(2L, stats.getOrDefault(SubscriptionSource.COPY_CODE, 0L));
            assertEquals(1L, stats.getOrDefault(SubscriptionSource.DOWNLOAD_FILE, 0L));
        }
    }

    // ==================== CSV 导出 ====================

    @Nested
    @DisplayName("CSV 导出")
    class CsvExport {

        @Test
        @DisplayName("exportToCsv 应包含所有活跃订阅者")
        void exportToCsv_shouldIncludeAllActive() {
            subscriberService.subscribe("alice@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("bob@test.com", SubscriptionSource.DOWNLOAD_FILE, null);

            String csv = subscriberService.exportToCsv();

            assertTrue(csv.startsWith("邮箱,来源,订阅时间,最后活跃,复制次数,下载次数,同步状态\n"));
            assertTrue(csv.contains("alice@test.com"));
            assertTrue(csv.contains("bob@test.com"));
            // 包含 header + 2 行数据
            assertEquals(3, csv.split("\n").length);
        }

        @Test
        @DisplayName("exportToCsv 空数据应只返回 header")
        void exportToCsv_empty_shouldReturnHeaderOnly() {
            String csv = subscriberService.exportToCsv();
            assertTrue(csv.startsWith("邮箱,来源"));
            assertEquals(1, csv.split("\n").length);
        }
    }

    // ==================== 数据源路由 ====================

    @Nested
    @DisplayName("数据源路由（NORMAL 模式）")
    class DataSourceRouting {

        @Test
        @DisplayName("NORMAL 模式下应通过 H2 完成全部读写")
        void normalMode_shouldUseH2ForAll() {
            // 通过 dataService 直接操作
            Subscriber sub = new Subscriber("direct@test.com", SubscriptionSource.COPY_CODE);
            sub.setCopyCount(1);

            Subscriber saved = dataService.save(sub);
            assertNotNull(saved.getId());
            assertEquals(Subscriber.SyncStatus.PENDING, saved.getSyncStatus());

            Optional<Subscriber> found = dataService.findByEmail("direct@test.com");
            assertTrue(found.isPresent());

            assertTrue(dataService.existsByEmail("direct@test.com"));
            assertFalse(dataService.existsByEmail("nonexistent@test.com"));
        }

        @Test
        @DisplayName("countActive 应仅统计 active=true 的记录")
        void countActive_shouldOnlyCountActive() {
            Subscriber active1 = new Subscriber("a@test.com", SubscriptionSource.COPY_CODE);
            active1.setCopyCount(1);
            dataService.save(active1);

            Subscriber active2 = new Subscriber("b@test.com", SubscriptionSource.COPY_CODE);
            active2.setCopyCount(1);
            dataService.save(active2);

            Subscriber inactive = new Subscriber("c@test.com", SubscriptionSource.COPY_CODE);
            inactive.setActive(false);
            inactive.setCopyCount(1);
            h2Repository.save(inactive); // 直接存 H2，跳过 sync 逻辑

            assertEquals(2, dataService.countActive());
        }

        @Test
        @DisplayName("getCurrentDataSourceDescription 应返回 H2 描述")
        void description_shouldShowH2() {
            String desc = dataService.getCurrentDataSourceDescription();
            assertTrue(desc.contains("H2"));
        }
    }

    // ==================== 并发安全 ====================

    @Nested
    @DisplayName("并发操作")
    class ConcurrentOperations {

        @Test
        @DisplayName("多次保存同一邮箱不应创建重复记录")
        void concurrentSave_sameEmail_shouldNotDuplicate() {
            subscriberService.subscribe("alice@test.com", SubscriptionSource.COPY_CODE, null);
            subscriberService.subscribe("alice@test.com", SubscriptionSource.DOWNLOAD_FILE, null);
            subscriberService.subscribe("alice@test.com", SubscriptionSource.COPY_CODE, null);

            assertEquals(1, h2Repository.count());
        }
    }
}
