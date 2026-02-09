package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.config.*;
import com.wontlost.ckeditor.service.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 本地集成测试配置
 * 在 IntegrationTestConfig 基础上额外导入 Oracle 相关 Bean
 * 连接真实 H2 文件数据库和 ckeditorlocal Oracle ATP
 */
@Configuration
@Import({
    H2DataSourceConfig.class,
    H2SchemaMigration.class,
    OracleDataSourceConfig.class,
    OracleSchemaMigration.class,
    SubscriberService.class,
    SubscriberDataService.class,
    DataSyncService.class,
    DataSourceHealthService.class,
    PersistentFailoverQueue.class,
    OracleSyncHelper.class
})
@EnableConfigurationProperties(DataSyncProperties.class)
@EnableAsync
class LocalIntegrationTestConfig {
}
