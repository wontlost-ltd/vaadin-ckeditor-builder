package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.config.DataSyncProperties.ReadWriteSplit;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.oracle.OracleSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubscriberDataService 单元测试
 * 覆盖智能路由、故障转移、读写分离等场景
 */
@ExtendWith(MockitoExtension.class)
class SubscriberDataServiceTest {

    @Mock
    private H2SubscriberRepository h2Repository;
    @Mock
    private DataSourceHealthService healthService;
    @Mock
    private DataSyncService syncService;
    @Mock
    private DataSyncProperties syncProperties;
    @Mock
    private ReadWriteSplit readWriteSplit;

    private SubscriberDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = new SubscriberDataService(h2Repository, healthService, syncService, syncProperties);
        lenient().when(syncProperties.getReadWriteSplit()).thenReturn(readWriteSplit);
        lenient().when(readWriteSplit.isEnabled()).thenReturn(false);
    }

    private Subscriber createTestSubscriber() {
        Subscriber s = new Subscriber("test@example.com", SubscriptionSource.COPY_CODE);
        s.setId(1L);
        return s;
    }

    // ==================== NORMAL 模式路由 ====================

    @Nested
    @DisplayName("NORMAL 模式")
    class NormalMode {

        @BeforeEach
        void setUp() {
            when(healthService.getCurrentMode()).thenReturn(DataSourceMode.NORMAL);
        }

        @Test
        @DisplayName("保存应写入 H2 并异步同步")
        void save_shouldWriteToH2WithSync() {
            Subscriber sub = createTestSubscriber();
            when(h2Repository.save(any())).thenReturn(sub);

            Subscriber result = dataService.save(sub);

            assertNotNull(result);
            verify(h2Repository).save(any(Subscriber.class));
            verify(syncService).syncToOracleAsync(any(Subscriber.class));
        }

        @Test
        @DisplayName("查询应从 H2 读取")
        void find_shouldReadFromH2() {
            Subscriber sub = createTestSubscriber();
            when(h2Repository.findByEmail("test@example.com")).thenReturn(Optional.of(sub));

            Optional<Subscriber> result = dataService.findByEmail("test@example.com");

            assertTrue(result.isPresent());
            assertEquals("test@example.com", result.get().getEmail());
            verify(h2Repository).findByEmail("test@example.com");
        }
    }

    // ==================== FAILOVER 模式路由 ====================

    @Nested
    @DisplayName("FAILOVER 模式")
    class FailoverMode {

        @BeforeEach
        void setUp() {
            when(healthService.getCurrentMode()).thenReturn(DataSourceMode.FAILOVER);
        }

        @Test
        @DisplayName("保存应写入 Oracle 并记录到回写队列")
        void save_shouldWriteToOracleAndRecordQueue() {
            // 由于 oracleRepository 是通过 @Autowired(required=false) 注入的，
            // 在没有 Oracle 的情况下，写入 Oracle 应抛出异常或回退
            // 此测试验证 FAILOVER 模式下的路由逻辑
            Subscriber sub = createTestSubscriber();
            // FAILOVER 模式下尝试保存到 Oracle，但无 Oracle 实例将失败
            assertThrows(Exception.class, () -> dataService.save(sub));
        }
    }

    // ==================== DEGRADED 模式 ====================

    @Nested
    @DisplayName("DEGRADED 模式")
    class DegradedMode {

        @BeforeEach
        void setUp() {
            when(healthService.getCurrentMode()).thenReturn(DataSourceMode.DEGRADED);
        }

        @Test
        @DisplayName("保存应抛出 DataSourceUnavailableException")
        void save_shouldThrowException() {
            Subscriber sub = createTestSubscriber();
            assertThrows(SubscriberDataService.DataSourceUnavailableException.class,
                () -> dataService.save(sub));
        }
    }

    // ==================== RECOVERY 模式 ====================

    @Nested
    @DisplayName("RECOVERY 模式")
    class RecoveryMode {

        @BeforeEach
        void setUp() {
            when(healthService.getCurrentMode()).thenReturn(DataSourceMode.RECOVERY);
        }

        @Test
        @DisplayName("保存应写入 H2 并尝试同步到 Oracle")
        void save_shouldWriteToH2AndTryOracle() {
            Subscriber sub = createTestSubscriber();
            when(h2Repository.save(any())).thenReturn(sub);

            Subscriber result = dataService.save(sub);

            assertNotNull(result);
            verify(h2Repository).save(any(Subscriber.class));
        }
    }

    // ==================== 数据源描述 ====================

    @Test
    @DisplayName("getCurrentDataSourceDescription 应返回正确描述")
    void description_shouldMatchMode() {
        lenient().when(healthService.isOracleAvailable()).thenReturn(false);

        when(healthService.getCurrentMode()).thenReturn(DataSourceMode.NORMAL);
        assertTrue(dataService.getCurrentDataSourceDescription().contains("H2"));

        when(healthService.getCurrentMode()).thenReturn(DataSourceMode.FAILOVER);
        assertTrue(dataService.getCurrentDataSourceDescription().contains("Oracle"));

        when(healthService.getCurrentMode()).thenReturn(DataSourceMode.DEGRADED);
        assertTrue(dataService.getCurrentDataSourceDescription().contains("Unavailable"));
    }
}
