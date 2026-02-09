package com.wontlost.ckeditor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * H2 数据库 Schema 迁移
 * 在 EntityManagerFactory 初始化前修复 H2 枚举列约束
 *
 * 背景：H2 的 ddl-auto=update 不会自动扩展已有的枚举约束。
 * 当向 SubscriptionSource 枚举添加新值时，必须手动将列从 ENUM 改为 VARCHAR。
 */
@Component("h2SchemaMigration")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class H2SchemaMigration implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(H2SchemaMigration.class);

    private final DataSource h2DataSource;

    public H2SchemaMigration(@Qualifier("h2DataSource") DataSource h2DataSource) {
        this.h2DataSource = h2DataSource;
    }

    @Override
    public void afterPropertiesSet() {
        migrateSourceColumn();
    }

    /**
     * 将 subscribers.source 列从 H2 ENUM 类型改为 VARCHAR(20)
     * 确保新增的 SubscriptionSource.ANONYMOUS 值可以正常存储
     */
    private void migrateSourceColumn() {
        try (Connection conn = h2DataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 检查 subscribers 表是否存在
            ResultSet rs = conn.getMetaData().getTables(null, null, "SUBSCRIBERS", null);
            if (!rs.next()) {
                log.debug("subscribers 表不存在，跳过迁移");
                return;
            }

            // 检查 source 列的当前类型
            ResultSet columns = conn.getMetaData().getColumns(null, null, "SUBSCRIBERS", "SOURCE");
            if (!columns.next()) {
                log.debug("source 列不存在，跳过迁移");
                return;
            }

            String typeName = columns.getString("TYPE_NAME");
            if (typeName != null && typeName.toUpperCase().contains("ENUM")) {
                stmt.execute("ALTER TABLE SUBSCRIBERS ALTER COLUMN SOURCE VARCHAR(20) NOT NULL");
                log.info("H2 迁移完成: subscribers.source 列从 {} 改为 VARCHAR(20)", typeName);
            } else {
                log.debug("source 列类型为 {}，无需迁移", typeName);
            }
        } catch (Exception e) {
            log.warn("H2 schema 迁移失败（新数据库无需迁移）: {}", e.getMessage());
        }
    }
}
