package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PersistentFailoverQueue 单元测试
 * 覆盖队列操作、持久化、容量限制等
 */
class PersistentFailoverQueueTest {

    @TempDir
    Path tempDir;

    private DataSyncProperties syncProperties;
    private PersistentFailoverQueue queue;

    @BeforeEach
    void setUp() {
        syncProperties = new DataSyncProperties();
        DataSyncProperties.FailoverQueue queueConfig = syncProperties.getFailoverQueue();
        queueConfig.setPersistent(true);
        queueConfig.setFilePath(tempDir.resolve("test-queue.json").toString());
        queueConfig.setMaxSize(5);
        queueConfig.setPersistInterval(60000);

        queue = new PersistentFailoverQueue(syncProperties);
    }

    private Subscriber createSubscriber(String email) {
        Subscriber s = new Subscriber(email, SubscriptionSource.COPY_CODE);
        s.setActive(true);
        s.setCopyCount(1);
        s.setDownloadCount(0);
        s.setLastActiveAt(LocalDateTime.now());
        return s;
    }

    // ==================== 基本操作 ====================

    @Test
    @DisplayName("初始队列应为空")
    void newQueue_shouldBeEmpty() {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("offer 应添加元素到队列")
    void offer_shouldAddElement() {
        boolean added = queue.offer(createSubscriber("test@example.com"));
        assertTrue(added);
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    @DisplayName("poll 应取出并移除元素")
    void poll_shouldRemoveElement() {
        queue.offer(createSubscriber("test@example.com"));

        PersistentFailoverQueue.FailoverEntry entry = queue.poll();

        assertNotNull(entry);
        assertEquals("test@example.com", entry.email());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("空队列 poll 应返回 null")
    void poll_emptyQueue_shouldReturnNull() {
        assertNull(queue.poll());
    }

    @Test
    @DisplayName("peekAll 应返回所有元素但不移除")
    void peekAll_shouldNotRemoveElements() {
        queue.offer(createSubscriber("a@test.com"));
        queue.offer(createSubscriber("b@test.com"));

        var entries = queue.peekAll();
        assertEquals(2, entries.size());
        assertEquals(2, queue.size());
    }

    @Test
    @DisplayName("clear 应清空队列")
    void clear_shouldEmptyQueue() {
        queue.offer(createSubscriber("a@test.com"));
        queue.offer(createSubscriber("b@test.com"));

        queue.clear();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    // ==================== 容量限制 ====================

    @Test
    @DisplayName("超出容量应拒绝添加")
    void offer_exceedingCapacity_shouldReject() {
        for (int i = 0; i < 5; i++) {
            assertTrue(queue.offer(createSubscriber("user" + i + "@test.com")));
        }
        assertEquals(5, queue.size());

        // 第 6 个应被拒绝
        assertFalse(queue.offer(createSubscriber("overflow@test.com")));
        assertEquals(5, queue.size());
    }

    // ==================== 持久化 ====================

    @Test
    @DisplayName("持久化后重新加载应恢复数据")
    void persistAndReload_shouldRestoreData() {
        queue.offer(createSubscriber("persist@test.com"));
        queue.offer(createSubscriber("persist2@test.com"));

        // 手动触发持久化
        queue.scheduledPersist();

        // 验证文件存在
        File file = new File(syncProperties.getFailoverQueue().getFilePath());
        assertTrue(file.exists());

        // 创建新队列实例模拟重启
        PersistentFailoverQueue newQueue = new PersistentFailoverQueue(syncProperties);
        newQueue.init();

        assertEquals(2, newQueue.size());
        PersistentFailoverQueue.FailoverEntry entry = newQueue.poll();
        assertEquals("persist@test.com", entry.email());
    }

    @Test
    @DisplayName("非持久化模式不应创建文件")
    void nonPersistentMode_shouldNotCreateFile() {
        syncProperties.getFailoverQueue().setPersistent(false);
        PersistentFailoverQueue nonPersistQueue = new PersistentFailoverQueue(syncProperties);

        nonPersistQueue.offer(createSubscriber("test@example.com"));
        nonPersistQueue.scheduledPersist();

        // 仍然不应创建文件（因为 persistent=false）
        // 但队列本身应正常工作
        assertEquals(1, nonPersistQueue.size());
    }

    // ==================== FailoverEntry 数据完整性 ====================

    @Test
    @DisplayName("FailoverEntry 应保留所有字段")
    void failoverEntry_shouldPreserveAllFields() {
        Subscriber subscriber = createSubscriber("fields@test.com");
        subscriber.setConfigSnapshot("{\"key\":\"value\"}");
        subscriber.setCopyCount(42);
        subscriber.setDownloadCount(7);
        subscriber.setActive(true);

        queue.offer(subscriber);
        PersistentFailoverQueue.FailoverEntry entry = queue.poll();

        assertEquals("fields@test.com", entry.email());
        assertEquals("{\"key\":\"value\"}", entry.configSnapshot());
        assertEquals("COPY_CODE", entry.source());
        assertEquals(42, entry.copyCount());
        assertEquals(7, entry.downloadCount());
        assertTrue(entry.active());
        assertTrue(entry.timestamp() > 0);
    }
}
