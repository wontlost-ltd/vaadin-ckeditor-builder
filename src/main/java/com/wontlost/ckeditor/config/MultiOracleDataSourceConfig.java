package com.wontlost.ckeditor.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多 Oracle 实例数据源配置
 * 支持多个 Oracle 实例的负载均衡和故障转移
 */
@Configuration
@ConditionalOnProperty(name = "app.sync.multi-oracle.enabled", havingValue = "true")
public class MultiOracleDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiOracleDataSourceConfig.class);

    private final DataSyncProperties syncProperties;
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, InstanceHealth> instanceHealth = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final AtomicReference<String> primaryInstance = new AtomicReference<>();

    public MultiOracleDataSourceConfig(DataSyncProperties syncProperties) {
        this.syncProperties = syncProperties;
    }

    @PostConstruct
    public void init() {
        List<DataSyncProperties.OracleInstance> instances = syncProperties.getMultiOracle().getInstances();

        if (instances.isEmpty()) {
            log.warn("多 Oracle 实例已启用但未配置任何实例");
            return;
        }

        for (DataSyncProperties.OracleInstance instance : instances) {
            try {
                HikariDataSource ds = createDataSource(instance);
                dataSources.put(instance.getName(), ds);
                instanceHealth.put(instance.getName(), new InstanceHealth(true, 0, System.currentTimeMillis()));

                if (instance.isPrimary()) {
                    primaryInstance.set(instance.getName());
                }

                log.info("初始化 Oracle 实例: {} (主: {})", instance.getName(), instance.isPrimary());
            } catch (Exception e) {
                log.error("初始化 Oracle 实例失败: {}", instance.getName(), e);
                instanceHealth.put(instance.getName(), new InstanceHealth(false, 1, System.currentTimeMillis()));
            }
        }

        // 如果没有指定主实例，使用第一个
        if (primaryInstance.get() == null && !instances.isEmpty()) {
            primaryInstance.set(instances.get(0).getName());
        }
    }

    private HikariDataSource createDataSource(DataSyncProperties.OracleInstance instance) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("oracle-" + instance.getName());
        ds.setJdbcUrl(instance.getUrl());
        ds.setUsername(instance.getUsername());
        ds.setPassword(instance.getPassword());
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(3);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        return ds;
    }

    /**
     * 获取当前可用的数据源
     * 根据配置的策略选择实例
     */
    public DataSource getDataSource() {
        DataSyncProperties.MultiOracle.LoadBalanceStrategy strategy = syncProperties.getMultiOracle().getStrategy();

        return switch (strategy) {
            case ROUND_ROBIN -> getByRoundRobin();
            case RANDOM -> getByRandom();
            case FAILOVER -> getByFailover();
        };
    }

    /**
     * 轮询策略
     */
    private DataSource getByRoundRobin() {
        List<String> healthyInstances = getHealthyInstances();
        if (healthyInstances.isEmpty()) {
            throw new NoHealthyInstanceException("没有可用的 Oracle 实例");
        }

        int index = roundRobinIndex.getAndIncrement() & Integer.MAX_VALUE;
        index = index % healthyInstances.size();
        String instanceName = healthyInstances.get(index);
        return dataSources.get(instanceName);
    }

    /**
     * 随机策略
     */
    private DataSource getByRandom() {
        List<String> healthyInstances = getHealthyInstances();
        if (healthyInstances.isEmpty()) {
            throw new NoHealthyInstanceException("没有可用的 Oracle 实例");
        }

        int index = ThreadLocalRandom.current().nextInt(healthyInstances.size());
        String instanceName = healthyInstances.get(index);
        return dataSources.get(instanceName);
    }

    /**
     * 故障转移策略（主备模式）
     */
    private DataSource getByFailover() {
        String primary = primaryInstance.get();

        // 优先使用主实例
        if (primary != null && isHealthy(primary)) {
            return dataSources.get(primary);
        }

        // 主实例不可用，使用备用实例
        for (String instanceName : dataSources.keySet()) {
            if (!instanceName.equals(primary) && isHealthy(instanceName)) {
                log.warn("主实例 {} 不可用，切换到备用实例 {}", primary, instanceName);
                return dataSources.get(instanceName);
            }
        }

        throw new NoHealthyInstanceException("没有可用的 Oracle 实例");
    }

    /**
     * 获取健康的实例列表
     */
    private List<String> getHealthyInstances() {
        List<String> healthy = new ArrayList<>();
        for (String instanceName : dataSources.keySet()) {
            if (isHealthy(instanceName)) {
                healthy.add(instanceName);
            }
        }
        return healthy;
    }

    /**
     * 检查实例是否健康
     */
    public boolean isHealthy(String instanceName) {
        InstanceHealth health = instanceHealth.get(instanceName);
        return health != null && health.healthy();
    }

    /**
     * 标记实例健康
     */
    public void markHealthy(String instanceName) {
        instanceHealth.put(instanceName, new InstanceHealth(true, 0, System.currentTimeMillis()));
    }

    /**
     * 标记实例不健康
     */
    public void markUnhealthy(String instanceName) {
        InstanceHealth current = instanceHealth.get(instanceName);
        int failureCount = current != null ? current.failureCount() + 1 : 1;
        instanceHealth.put(instanceName, new InstanceHealth(false, failureCount, System.currentTimeMillis()));
    }

    /**
     * 获取所有实例状态
     */
    public Map<String, InstanceHealth> getAllInstanceHealth() {
        return new HashMap<>(instanceHealth);
    }

    /**
     * 获取实例数量
     */
    public int getInstanceCount() {
        return dataSources.size();
    }

    /**
     * 获取健康实例数量
     */
    public int getHealthyInstanceCount() {
        return getHealthyInstances().size();
    }

    /**
     * 关闭所有 HikariDataSource 连接池，防止资源泄漏
     */
    @jakarta.annotation.PreDestroy
    public void destroy() {
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            try {
                entry.getValue().close();
                log.info("已关闭 Oracle 实例连接池: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭 Oracle 实例连接池失败: {}", entry.getKey(), e);
            }
        }
        dataSources.clear();
    }

    /**
     * 实例健康状态
     */
    public record InstanceHealth(boolean healthy, int failureCount, long lastCheckTime) {}

    /**
     * 无健康实例异常
     */
    public static class NoHealthyInstanceException extends RuntimeException {
        public NoHealthyInstanceException(String message) {
            super(message);
        }
    }
}
