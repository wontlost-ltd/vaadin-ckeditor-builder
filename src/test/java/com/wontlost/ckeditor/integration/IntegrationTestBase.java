package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.h2.SyncAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 * 使用 IntegrationTestConfig 加载精简的 Spring 上下文（无 Web/Security/Vaadin）
 * H2 内存数据库，Oracle 禁用
 */
@SpringBootTest(
    classes = IntegrationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired
    protected H2SubscriberRepository h2Repository;

    @Autowired
    protected SyncAuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        h2Repository.deleteAll();
    }
}
