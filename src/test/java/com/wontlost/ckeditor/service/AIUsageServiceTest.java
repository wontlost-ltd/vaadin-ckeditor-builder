package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.AIProperties;
import com.wontlost.ckeditor.service.AIUsageService.ConsumeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AIUsageService 单元测试
 * 纯单元测试，无 Spring 上下文
 */
class AIUsageServiceTest {

    private AIProperties aiProperties;
    private AIUsageService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AIProperties();
        AIProperties.UsageLimit limit = new AIProperties.UsageLimit();
        limit.setSessionMax(5);
        limit.setPreviewSessionMax(2);
        limit.setWindowMs(5 * 3600_000L);
        limit.setCleanupIntervalMs(300_000L);
        limit.setInactiveTimeoutMs(5 * 3600_000L);
        aiProperties.setUsageLimit(limit);
        service = new AIUsageService(aiProperties);
    }

    @Nested
    @DisplayName("配额消费")
    class Consumption {

        @Test
        @DisplayName("限额内允许消费，remaining 递减")
        void shouldAllowWithinLimit() {
            for (int i = 4; i >= 0; i--) {
                ConsumeResult result = service.tryConsume("session-1", false);
                assertTrue(result.allowed(), "第 " + (5 - i) + " 次应允许");
                assertEquals(i, result.remaining());
                assertEquals(5, result.limit());
            }
        }

        @Test
        @DisplayName("超限拒绝")
        void shouldRejectOverLimit() {
            // 消耗完 5 次配额
            for (int i = 0; i < 5; i++) {
                assertTrue(service.tryConsume("session-2", false).allowed());
            }

            // 第 6 次被拒绝
            ConsumeResult result = service.tryConsume("session-2", false);
            assertFalse(result.allowed());
            assertEquals(0, result.remaining());
            assertEquals(5, result.limit());
        }

        @Test
        @DisplayName("不同 session 独立计数")
        void shouldTrackSessionsIndependently() {
            // session-A 消耗完
            for (int i = 0; i < 5; i++) {
                service.tryConsume("session-A", false);
            }
            assertFalse(service.tryConsume("session-A", false).allowed());

            // session-B 不受影响
            ConsumeResult result = service.tryConsume("session-B", false);
            assertTrue(result.allowed());
            assertEquals(4, result.remaining());
        }

        @Test
        @DisplayName("预览使用更严格限额")
        void shouldApplyStricterPreviewLimit() {
            // 预览限额 = 2
            assertTrue(service.tryConsume("preview-1", true).allowed());
            assertTrue(service.tryConsume("preview-1", true).allowed());

            // 第 3 次被拒绝
            ConsumeResult result = service.tryConsume("preview-1", true);
            assertFalse(result.allowed());
            assertEquals(0, result.remaining());
            assertEquals(2, result.limit());
        }
    }

    @Nested
    @DisplayName("时间窗口")
    class TimeWindow {

        @Test
        @DisplayName("窗口到期后配额重置")
        void shouldResetAfterWindowExpires() throws InterruptedException {
            // 使用极短窗口
            aiProperties.getUsageLimit().setWindowMs(100);
            service = new AIUsageService(aiProperties);

            // 消耗完配额
            for (int i = 0; i < 5; i++) {
                service.tryConsume("session-w", false);
            }
            assertFalse(service.tryConsume("session-w", false).allowed());

            // 等待窗口过期
            Thread.sleep(150);

            // 配额重置
            ConsumeResult result = service.tryConsume("session-w", false);
            assertTrue(result.allowed());
            assertEquals(4, result.remaining());
        }
    }

    @Nested
    @DisplayName("Session 清理")
    class Cleanup {

        @Test
        @DisplayName("过期 session 被清理")
        void shouldCleanupInactiveSessions() throws InterruptedException {
            aiProperties.getUsageLimit().setInactiveTimeoutMs(100);
            service = new AIUsageService(aiProperties);

            service.tryConsume("session-old", false);
            assertEquals(1, service.getTrackedSessionCount());

            // 等待超时
            Thread.sleep(150);
            service.cleanupInactiveSessions();

            assertEquals(0, service.getTrackedSessionCount());
        }

        @Test
        @DisplayName("活跃 session 不被清理")
        void shouldNotCleanupActiveSessions() throws InterruptedException {
            aiProperties.getUsageLimit().setInactiveTimeoutMs(200);
            service = new AIUsageService(aiProperties);

            service.tryConsume("session-active", false);
            Thread.sleep(50);

            // 刷新活跃时间
            service.tryConsume("session-active", false);
            Thread.sleep(50);

            service.cleanupInactiveSessions();
            assertEquals(1, service.getTrackedSessionCount());
        }
    }

    @Nested
    @DisplayName("并发安全")
    class Concurrency {

        @Test
        @DisplayName("多线程竞争时恰好允许 sessionMax 次")
        void shouldAllowExactlyMaxConcurrently() throws InterruptedException {
            int threads = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threads);
            AtomicInteger allowedCount = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        ConsumeResult result = service.tryConsume("concurrent-session", false);
                        if (result.allowed()) {
                            allowedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            doneLatch.await();

            assertEquals(5, allowedCount.get(), "恰好允许 sessionMax(5) 次请求");
        }
    }
}
