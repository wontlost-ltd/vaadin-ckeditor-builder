package com.wontlost.ckeditor.integration;

import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.h2.SyncAuditLogRepository;
import com.wontlost.ckeditor.repository.oracle.OracleSubscriberRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 本地集成测试基类
 * 连接真实 H2 文件数据库和 ckeditorlocal Oracle ATP
 * BeforeAll 清空两个数据库，BeforeEach 保证测试间隔离
 */
@SpringBootTest(
    classes = LocalIntegrationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class LocalIntegrationTestBase {

    @Autowired
    protected H2SubscriberRepository h2Repository;

    @Autowired
    protected SyncAuditLogRepository auditLogRepository;

    @Autowired
    protected OracleSubscriberRepository oracleRepository;

    /**
     * 测试类启动前清空 H2 和 Oracle 全部数据
     * 确保本地 bootRun 残留的脏数据不影响测试
     */
    @BeforeAll
    void cleanAllDatabases() {
        auditLogRepository.deleteAll();
        h2Repository.deleteAll();
        oracleRepository.deleteAll();
    }

    /**
     * 每个测试方法前清空数据，保证测试间隔离
     */
    @BeforeEach
    void cleanDatabaseBetweenTests() {
        auditLogRepository.deleteAll();
        h2Repository.deleteAll();
        oracleRepository.deleteAll();
    }
}
