package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SubscriberService 单元测试
 * 覆盖订阅流程的正常路径和异常情况
 */
@ExtendWith(MockitoExtension.class)
class SubscriberServiceTest {

    @Mock
    private SubscriberDataService dataService;
    @Mock
    private DataSyncService dataSyncService;
    @Mock
    private DataSourceHealthService healthService;

    private SubscriberService subscriberService;

    @BeforeEach
    void setUp() {
        subscriberService = new SubscriberService(dataService, dataSyncService, healthService);
    }

    // ==================== 订阅流程 ====================

    @Nested
    @DisplayName("新用户订阅")
    class NewSubscription {

        @Test
        @DisplayName("有效邮箱应创建新订阅者")
        void validEmail_shouldCreateNewSubscriber() {
            when(dataService.findByEmail("test@example.com")).thenReturn(Optional.empty());
            when(dataService.save(any(Subscriber.class))).thenAnswer(inv -> {
                Subscriber s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });

            Optional<Subscriber> result = subscriberService.subscribe(
                "test@example.com", SubscriptionSource.COPY_CODE, null);

            assertTrue(result.isPresent());
            assertEquals("test@example.com", result.get().getEmail());
            assertEquals(SubscriptionSource.COPY_CODE, result.get().getSource());
            assertEquals(1, result.get().getCopyCount());
            verify(dataService).save(any(Subscriber.class));
        }

        @Test
        @DisplayName("邮箱应标准化为小写并去除空白")
        void email_shouldBeNormalizedToLowerCase() {
            when(dataService.findByEmail("test@example.com")).thenReturn(Optional.empty());
            when(dataService.save(any(Subscriber.class))).thenAnswer(inv -> inv.getArgument(0));

            subscriberService.subscribe(" Test@Example.COM ", SubscriptionSource.MANUAL, null);

            verify(dataService).findByEmail("test@example.com");
            verify(dataService).save(argThat(s -> "test@example.com".equals(s.getEmail())));
        }

        @Test
        @DisplayName("DOWNLOAD_FILE 来源应初始化下载计数为 1")
        void downloadSource_shouldInitDownloadCount() {
            when(dataService.findByEmail(anyString())).thenReturn(Optional.empty());
            when(dataService.save(any(Subscriber.class))).thenAnswer(inv -> inv.getArgument(0));

            Optional<Subscriber> result = subscriberService.subscribe(
                "dl@test.com", SubscriptionSource.DOWNLOAD_FILE, null);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().getDownloadCount());
            assertEquals(0, result.get().getCopyCount());
        }
    }

    @Nested
    @DisplayName("已存在用户再次订阅")
    class ExistingSubscription {

        @Test
        @DisplayName("已订阅用户应更新活跃时间和计数")
        void existingUser_shouldUpdateActivityAndCount() {
            Subscriber existing = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            existing.setId(1L);
            existing.setCopyCount(3);

            when(dataService.findByEmail("test@example.com")).thenReturn(Optional.of(existing));
            when(dataService.save(any(Subscriber.class))).thenAnswer(inv -> inv.getArgument(0));

            Optional<Subscriber> result = subscriberService.subscribe(
                "test@example.com", SubscriptionSource.COPY_CODE, null);

            assertTrue(result.isPresent());
            assertEquals(4, result.get().getCopyCount());
            verify(dataService).save(existing);
        }

        @Test
        @DisplayName("已订阅用户使用不同来源应更新对应计数")
        void existingUser_differentSource_shouldUpdateCorrectCount() {
            Subscriber existing = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            existing.setId(1L);
            existing.setCopyCount(2);
            existing.setDownloadCount(1);

            when(dataService.findByEmail("test@example.com")).thenReturn(Optional.of(existing));
            when(dataService.save(any(Subscriber.class))).thenAnswer(inv -> inv.getArgument(0));

            subscriberService.subscribe("test@example.com", SubscriptionSource.DOWNLOAD_FILE, null);

            assertEquals(2, existing.getCopyCount());
            assertEquals(2, existing.getDownloadCount());
        }
    }

    // ==================== 邮箱验证 ====================

    @Nested
    @DisplayName("邮箱验证")
    class EmailValidation {

        @Test
        @DisplayName("null 邮箱应返回空")
        void nullEmail_shouldReturnEmpty() {
            Optional<Subscriber> result = subscriberService.subscribe(null, SubscriptionSource.MANUAL, null);
            assertTrue(result.isEmpty());
            verify(dataService, never()).save(any());
        }

        @Test
        @DisplayName("空字符串邮箱应返回空")
        void emptyEmail_shouldReturnEmpty() {
            Optional<Subscriber> result = subscriberService.subscribe("", SubscriptionSource.MANUAL, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("缺少 @ 的邮箱应返回空")
        void noAtSign_shouldReturnEmpty() {
            Optional<Subscriber> result = subscriberService.subscribe("invalid-email", SubscriptionSource.MANUAL, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("缺少域名的邮箱应返回空")
        void noDomain_shouldReturnEmpty() {
            Optional<Subscriber> result = subscriberService.subscribe("user@", SubscriptionSource.MANUAL, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("有效的复杂邮箱应通过验证")
        void complexValidEmail_shouldPass() {
            when(dataService.findByEmail(anyString())).thenReturn(Optional.empty());
            when(dataService.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<Subscriber> result = subscriberService.subscribe(
                "user.name-tag@sub.domain.com", SubscriptionSource.MANUAL, null);
            assertTrue(result.isPresent());
        }
    }

    // ==================== 活动记录 ====================

    @Nested
    @DisplayName("活动记录")
    class RecordActivity {

        @Test
        @DisplayName("已存在用户记录活动应更新计数")
        void existingUser_shouldRecordActivity() {
            Subscriber existing = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
            existing.setCopyCount(5);

            when(dataService.findByEmail("test@example.com")).thenReturn(Optional.of(existing));

            subscriberService.recordActivity("test@example.com", SubscriptionSource.COPY_CODE);

            assertEquals(6, existing.getCopyCount());
            verify(dataService).save(existing);
        }

        @Test
        @DisplayName("不存在的用户记录活动应不做任何操作")
        void nonExistingUser_shouldDoNothing() {
            when(dataService.findByEmail(anyString())).thenReturn(Optional.empty());

            subscriberService.recordActivity("nonexist@test.com", SubscriptionSource.COPY_CODE);

            verify(dataService, never()).save(any());
        }
    }

    // ==================== 订阅检查 ====================

    @Test
    @DisplayName("isSubscribed 应委托给 dataService")
    void isSubscribed_shouldDelegateToDataService() {
        when(dataService.existsByEmail("test@example.com")).thenReturn(true);
        assertTrue(subscriberService.isSubscribed("test@example.com"));

        when(dataService.existsByEmail("unknown@test.com")).thenReturn(false);
        assertFalse(subscriberService.isSubscribed("unknown@test.com"));
    }

    // ==================== 同步操作 ====================

    @Test
    @DisplayName("triggerFullSync 应委托给 dataSyncService")
    void triggerFullSync_shouldDelegate() {
        var expected = new DataSyncService.SyncResult(10, 0, 2, null);
        when(dataSyncService.manualFullSync()).thenReturn(expected);

        var result = subscriberService.triggerFullSync();
        assertEquals(10, result.successCount());
        assertEquals(0, result.failCount());
    }

    // ==================== 高可用操作 ====================

    @Test
    @DisplayName("manualFailover 应委托给 healthService")
    void manualFailover_shouldDelegate() {
        when(healthService.manualFailover()).thenReturn(true);
        assertTrue(subscriberService.manualFailover());
    }

    @Test
    @DisplayName("getCurrentDataSourceMode 应返回正确模式")
    void getCurrentMode_shouldReturnCorrectMode() {
        when(healthService.getCurrentMode()).thenReturn(DataSourceMode.NORMAL);
        assertEquals(DataSourceMode.NORMAL, subscriberService.getCurrentDataSourceMode());

        when(healthService.getCurrentMode()).thenReturn(DataSourceMode.FAILOVER);
        assertEquals(DataSourceMode.FAILOVER, subscriberService.getCurrentDataSourceMode());
    }
}
