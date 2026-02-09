package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.config.H2DataSourceConfig;
import com.wontlost.ckeditor.config.H2SchemaMigration;
import com.wontlost.ckeditor.service.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 集成测试专用配置
 * 手动导入所需组件，完全避免 Vaadin/Security 自动配置
 */
@Configuration
@Import({
    H2DataSourceConfig.class,
    H2SchemaMigration.class,
    SubscriberService.class,
    SubscriberDataService.class,
    DataSyncService.class,
    DataSourceHealthService.class,
    PersistentFailoverQueue.class,
    OracleSyncHelper.class
})
@EnableConfigurationProperties(DataSyncProperties.class)
@EnableAsync
class IntegrationTestConfig {
}
