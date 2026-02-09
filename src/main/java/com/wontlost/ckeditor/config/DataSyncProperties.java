package com.wontlost.ckeditor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据同步配置属性
 * 集中管理所有同步、故障转移、读写分离相关配置
 */
@Component
@ConfigurationProperties(prefix = "app.sync")
public class DataSyncProperties {

    // ==================== 基础配置 ====================

    /**
     * 同步模式: REALTIME, SCHEDULED, MANUAL
     */
    private SyncMode mode = SyncMode.REALTIME;

    /**
     * 是否启用 Oracle 同步
     */
    private boolean oracleEnabled = false;

    /**
     * 定时同步间隔 (毫秒)，默认 5 分钟
     */
    private long scheduledInterval = 300000;

    // ==================== 健康检查配置 ====================

    private HealthCheck healthCheck = new HealthCheck();

    public static class HealthCheck {
        /**
         * 健康检查间隔 (毫秒)，默认 30 秒
         */
        private long interval = 30000;

        /**
         * H2 连续失败多少次触发故障转移
         */
        private int failureThreshold = 3;

        /**
         * H2 连续成功多少次触发恢复
         */
        private int recoveryThreshold = 3;

        /**
         * 健康检查超时时间 (毫秒)
         */
        private long timeout = 5000;

        public long getInterval() { return interval; }
        public void setInterval(long interval) { this.interval = interval; }
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public int getRecoveryThreshold() { return recoveryThreshold; }
        public void setRecoveryThreshold(int recoveryThreshold) { this.recoveryThreshold = recoveryThreshold; }
        public long getTimeout() { return timeout; }
        public void setTimeout(long timeout) { this.timeout = timeout; }
    }

    // ==================== 重试配置 ====================

    private Retry retry = new Retry();

    public static class Retry {
        /**
         * 最大重试次数
         */
        private int maxCount = 3;

        /**
         * 重试间隔 (毫秒)，默认 15 分钟
         */
        private long interval = 900000;

        /**
         * 重试延迟递增因子
         */
        private double backoffMultiplier = 1.5;

        public int getMaxCount() { return maxCount; }
        public void setMaxCount(int maxCount) { this.maxCount = maxCount; }
        public long getInterval() { return interval; }
        public void setInterval(long interval) { this.interval = interval; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    }

    // ==================== 回写队列配置 ====================

    private FailoverQueue failoverQueue = new FailoverQueue();

    public static class FailoverQueue {
        /**
         * 是否持久化回写队列
         */
        private boolean persistent = true;

        /**
         * 持久化文件路径
         */
        private String filePath = "./data/failover-queue.json";

        /**
         * 队列最大容量
         */
        private int maxSize = 10000;

        /**
         * 持久化间隔 (毫秒)
         */
        private long persistInterval = 5000;

        public boolean isPersistent() { return persistent; }
        public void setPersistent(boolean persistent) { this.persistent = persistent; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public long getPersistInterval() { return persistInterval; }
        public void setPersistInterval(long persistInterval) { this.persistInterval = persistInterval; }
    }

    // ==================== 读写分离配置 ====================

    private ReadWriteSplit readWriteSplit = new ReadWriteSplit();

    public static class ReadWriteSplit {
        /**
         * 是否启用读写分离
         */
        private boolean enabled = false;

        /**
         * 读操作策略: H2_FIRST, ORACLE_FIRST, ROUND_ROBIN
         */
        private ReadStrategy readStrategy = ReadStrategy.H2_FIRST;

        /**
         * 写操作策略: H2_ONLY, DUAL_WRITE
         */
        private WriteStrategy writeStrategy = WriteStrategy.H2_ONLY;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public ReadStrategy getReadStrategy() { return readStrategy; }
        public void setReadStrategy(ReadStrategy readStrategy) { this.readStrategy = readStrategy; }
        public WriteStrategy getWriteStrategy() { return writeStrategy; }
        public void setWriteStrategy(WriteStrategy writeStrategy) { this.writeStrategy = writeStrategy; }

        public enum ReadStrategy {
            H2_FIRST,       // H2 优先，失败回退 Oracle
            ORACLE_FIRST,   // Oracle 优先，失败回退 H2
            ROUND_ROBIN     // 轮询
        }

        public enum WriteStrategy {
            H2_ONLY,        // 仅写 H2，异步同步 Oracle
            DUAL_WRITE      // 双写（同步写两边）
        }
    }

    // ==================== 多 Oracle 实例配置 ====================

    private MultiOracle multiOracle = new MultiOracle();

    public static class MultiOracle {
        /**
         * 是否启用多实例
         */
        private boolean enabled = false;

        /**
         * 负载均衡策略: ROUND_ROBIN, RANDOM, FAILOVER
         */
        private LoadBalanceStrategy strategy = LoadBalanceStrategy.FAILOVER;

        /**
         * Oracle 实例列表
         */
        private List<OracleInstance> instances = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public LoadBalanceStrategy getStrategy() { return strategy; }
        public void setStrategy(LoadBalanceStrategy strategy) { this.strategy = strategy; }
        public List<OracleInstance> getInstances() { return instances; }
        public void setInstances(List<OracleInstance> instances) { this.instances = instances; }

        public enum LoadBalanceStrategy {
            ROUND_ROBIN,    // 轮询
            RANDOM,         // 随机
            FAILOVER        // 故障转移（主备）
        }
    }

    public static class OracleInstance {
        private String name;
        private String url;
        private String username;
        private String password;
        private int weight = 1;
        private boolean primary = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
        public boolean isPrimary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }
    }

    // ==================== Getters and Setters ====================

    public SyncMode getMode() { return mode; }
    public void setMode(SyncMode mode) { this.mode = mode; }
    public boolean isOracleEnabled() { return oracleEnabled; }
    public void setOracleEnabled(boolean oracleEnabled) { this.oracleEnabled = oracleEnabled; }
    public long getScheduledInterval() { return scheduledInterval; }
    public void setScheduledInterval(long scheduledInterval) { this.scheduledInterval = scheduledInterval; }
    public HealthCheck getHealthCheck() { return healthCheck; }
    public void setHealthCheck(HealthCheck healthCheck) { this.healthCheck = healthCheck; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
    public FailoverQueue getFailoverQueue() { return failoverQueue; }
    public void setFailoverQueue(FailoverQueue failoverQueue) { this.failoverQueue = failoverQueue; }
    public ReadWriteSplit getReadWriteSplit() { return readWriteSplit; }
    public void setReadWriteSplit(ReadWriteSplit readWriteSplit) { this.readWriteSplit = readWriteSplit; }
    public MultiOracle getMultiOracle() { return multiOracle; }
    public void setMultiOracle(MultiOracle multiOracle) { this.multiOracle = multiOracle; }
}
