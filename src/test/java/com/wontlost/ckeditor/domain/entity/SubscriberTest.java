package com.wontlost.ckeditor.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Subscriber 实体单元测试
 * 覆盖构造函数、便捷方法、同步状态转换
 */
class SubscriberTest {

    @Nested
    @DisplayName("构造函数")
    class Constructor {

        @Test
        @DisplayName("带参构造应正确初始化字段")
        void parameterizedConstructor_shouldInitFields() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);

            assertEquals("test@example.com", sub.getEmail());
            assertEquals(SubscriptionSource.COPY_CODE, sub.getSource());
            assertNotNull(sub.getCreatedAt());
            assertNotNull(sub.getLastActiveAt());
            assertTrue(sub.isActive());
            assertEquals(0, sub.getCopyCount());
            assertEquals(0, sub.getDownloadCount());
            assertEquals(Subscriber.SyncStatus.PENDING, sub.getSyncStatus());
        }

        @Test
        @DisplayName("无参构造应使用默认值")
        void defaultConstructor_shouldHaveDefaults() {
            Subscriber sub = new Subscriber();

            assertNull(sub.getId());
            assertNull(sub.getEmail());
            assertTrue(sub.isActive());
            assertEquals(Subscriber.SyncStatus.PENDING, sub.getSyncStatus());
            assertEquals(0, sub.getSyncRetryCount());
        }
    }

    @Nested
    @DisplayName("计数操作")
    class CountOperations {

        @Test
        @DisplayName("incrementCopyCount 应递增复制计数")
        void incrementCopyCount_shouldIncrement() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            assertEquals(0, sub.getCopyCount());

            sub.incrementCopyCount();
            assertEquals(1, sub.getCopyCount());

            sub.incrementCopyCount();
            assertEquals(2, sub.getCopyCount());
        }

        @Test
        @DisplayName("incrementDownloadCount 应递增下载计数")
        void incrementDownloadCount_shouldIncrement() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.DOWNLOAD_FILE);

            sub.incrementDownloadCount();
            sub.incrementDownloadCount();
            sub.incrementDownloadCount();

            assertEquals(3, sub.getDownloadCount());
        }

        @Test
        @DisplayName("updateLastActive 应更新最后活跃时间")
        void updateLastActive_shouldUpdateTimestamp() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            LocalDateTime before = sub.getLastActiveAt();

            // 等待一毫秒确保时间不同
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}

            sub.updateLastActive();

            assertNotNull(sub.getLastActiveAt());
            assertTrue(sub.getLastActiveAt().isAfter(before) || sub.getLastActiveAt().isEqual(before));
        }
    }

    @Nested
    @DisplayName("同步状态转换")
    class SyncStatusTransitions {

        @Test
        @DisplayName("markSyncSuccess 应设置 SYNCED 并清除错误")
        void markSyncSuccess_shouldSetSyncedAndClearError() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            sub.markSyncFailed("some error");
            assertEquals(Subscriber.SyncStatus.FAILED, sub.getSyncStatus());
            assertEquals(1, sub.getSyncRetryCount());

            sub.markSyncSuccess();

            assertEquals(Subscriber.SyncStatus.SYNCED, sub.getSyncStatus());
            assertNotNull(sub.getLastSyncedAt());
            assertEquals(0, sub.getSyncRetryCount());
            assertNull(sub.getLastSyncError());
        }

        @Test
        @DisplayName("markSyncFailed 应递增重试计数并记录错误")
        void markSyncFailed_shouldIncrementRetryAndRecordError() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);

            sub.markSyncFailed("error 1");
            assertEquals(Subscriber.SyncStatus.FAILED, sub.getSyncStatus());
            assertEquals(1, sub.getSyncRetryCount());
            assertEquals("error 1", sub.getLastSyncError());

            sub.markSyncFailed("error 2");
            assertEquals(2, sub.getSyncRetryCount());
            assertEquals("error 2", sub.getLastSyncError());

            sub.markSyncFailed("error 3");
            assertEquals(3, sub.getSyncRetryCount());
        }

        @Test
        @DisplayName("markSyncFailed 应截断超长错误信息")
        void markSyncFailed_shouldTruncateLongError() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            String longError = "x".repeat(1000);

            sub.markSyncFailed(longError);

            assertNotNull(sub.getLastSyncError());
            assertEquals(500, sub.getLastSyncError().length());
        }

        @Test
        @DisplayName("markSyncFailed 应处理 null 错误")
        void markSyncFailed_shouldHandleNullError() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);

            sub.markSyncFailed(null);

            assertEquals(Subscriber.SyncStatus.FAILED, sub.getSyncStatus());
            assertNull(sub.getLastSyncError());
            assertEquals(1, sub.getSyncRetryCount());
        }

        @Test
        @DisplayName("markSyncPending 应重置为 PENDING")
        void markSyncPending_shouldResetToPending() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            sub.markSyncSuccess();

            sub.markSyncPending();

            assertEquals(Subscriber.SyncStatus.PENDING, sub.getSyncStatus());
        }

        @Test
        @DisplayName("完整生命周期: PENDING → FAILED → SUCCESS")
        void fullLifecycle_pendingToFailedToSuccess() {
            Subscriber sub = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);

            // 初始状态
            assertEquals(Subscriber.SyncStatus.PENDING, sub.getSyncStatus());

            // 首次同步失败
            sub.markSyncFailed("timeout");
            assertEquals(Subscriber.SyncStatus.FAILED, sub.getSyncStatus());
            assertEquals(1, sub.getSyncRetryCount());

            // 再次失败
            sub.markSyncFailed("connection refused");
            assertEquals(2, sub.getSyncRetryCount());

            // 成功
            sub.markSyncSuccess();
            assertEquals(Subscriber.SyncStatus.SYNCED, sub.getSyncStatus());
            assertEquals(0, sub.getSyncRetryCount());
            assertNull(sub.getLastSyncError());
        }
    }
}
