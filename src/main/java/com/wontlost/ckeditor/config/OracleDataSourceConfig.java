package com.wontlost.ckeditor.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Oracle ATP 数据源配置 (Secondary)
 * 云端数据库，用于数据备份和同步
 * 仅在 app.sync.oracle-enabled=true 时启用
 */
@Configuration
@ConditionalOnProperty(name = "app.sync.oracle-enabled", havingValue = "true")
@EnableJpaRepositories(
    basePackages = "com.wontlost.ckeditor.repository.oracle",
    entityManagerFactoryRef = "oracleEntityManagerFactory",
    transactionManagerRef = "oracleTransactionManager"
)
public class OracleDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(OracleDataSourceConfig.class);

    @Value("${spring.datasource.oracle.url:}")
    private String url;

    @Value("${spring.datasource.oracle.driver-class-name:oracle.jdbc.OracleDriver}")
    private String driverClassName;

    @Value("${spring.datasource.oracle.username:ADMIN}")
    private String username;

    @Value("${spring.datasource.oracle.password:}")
    private String password;

    @Value("${spring.jpa.oracle.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @Bean
    public DataSource oracleDataSource() {
        log.info("初始化 Oracle ATP 数据源: {}", url);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("Oracle-Pool");
        // 保守设置，适合免费层
        dataSource.setMinimumIdle(1);
        dataSource.setMaximumPoolSize(3);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        return dataSource;
    }

    @Bean
    @org.springframework.context.annotation.DependsOn("oracleSchemaMigration")
    public LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory(
            @Qualifier("oracleDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.wontlost.ckeditor.domain.entity");
        em.setPersistenceUnitName("oracle");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        properties.put("hibernate.show_sql", "false");
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
