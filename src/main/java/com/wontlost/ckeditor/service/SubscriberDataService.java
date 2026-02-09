package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.config.DataSyncProperties.ReadWriteSplit.ReadStrategy;
import com.wontlost.ckeditor.config.DataSyncProperties.ReadWriteSplit.WriteStrategy;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.repository.h2.H2SubscriberRepository;
import com.wontlost.ckeditor.repository.oracle.OracleSubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 订阅者数据服务 - 智能数据源路由
 *
 * 根据当前数据源模式自动路由到 H2 或 Oracle：
 * - NORMAL: 读写都走 H2，异步同步到 Oracle
 * - FAILOVER: 读写都走 Oracle
 * - RECOVERY: 读走 H2，写入两边
 * - DEGRADED: 抛出异常或使用内存缓存
 */
@Service
public class SubscriberDataService {

    private static final Logger log = LoggerFactory.getLogger(SubscriberDataService.class);

    private final H2SubscriberRepository h2Repository;
    private final DataSourceHealthService healthService;
    private final DataSyncService syncService;
    private final DataSyncProperties syncProperties;

    // 轮询计数器
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    @Autowired(required = false)
    private OracleSubscriberRepository oracleRepository;

    public SubscriberDataService(
            H2SubscriberRepository h2Repository,
            DataSourceHealthService healthService,
            @Lazy DataSyncService syncService,
            DataSyncProperties syncProperties) {
        this.h2Repository = h2Repository;
        this.healthService = healthService;
        this.syncService = syncService;
        this.syncProperties = syncProperties;
    }

    // ==================== 写操作 ====================

    /**
     * 保存订阅者（自动路由）
     */
    public Subscriber save(Subscriber subscriber) {
        DataSourceMode mode = healthService.getCurrentMode();

        // 检查是否启用读写分离
        if (isReadWriteSplitEnabled() && mode == DataSourceMode.NORMAL) {
            return saveWithReadWriteSplit(subscriber);
        }

        switch (mode) {
            case NORMAL:
                return saveToH2WithSync(subscriber);

            case FAILOVER:
                return saveToOracle(subscriber);

            case RECOVERY:
                // 恢复模式：写入两边
                Subscriber saved = saveToH2(subscriber);
                try {
                    syncService.syncSubscriberToOracle(saved);
                } catch (Exception e) {
                    log.warn("恢复模式下同步到 Oracle 失败: {}", e.getMessage());
                }
                return saved;

            case DEGRADED:
                throw new DataSourceUnavailableException("系统处于降级模式，H2 和 Oracle 都不可用");

            default:
                return saveToH2WithSync(subscriber);
        }
    }

    /**
     * 读写分离模式下的保存
     */
    private Subscriber saveWithReadWriteSplit(Subscriber subscriber) {
        WriteStrategy writeStrategy = syncProperties.getReadWriteSplit().getWriteStrategy();

        switch (writeStrategy) {
            case DUAL_WRITE:
                // 双写：同步写入两边
                subscriber.markSyncPending();
                Subscriber h2Saved = h2Repository.save(subscriber);
                if (healthService.isOracleAvailable()) {
                    try {
                        syncService.syncSubscriberToOracle(h2Saved);
                        h2Saved.markSyncSuccess();
                        h2Repository.save(h2Saved);
                    } catch (Exception e) {
                        log.error("双写模式下 Oracle 写入失败: {}", e.getMessage());
                        h2Saved.markSyncFailed(e.getMessage());
                        h2Repository.save(h2Saved);
                    }
                }
                return h2Saved;

            case H2_ONLY:
            default:
                // 仅写 H2，异步同步 Oracle
                return saveToH2WithSync(subscriber);
        }
    }

    /**
     * 保存到 H2 并触发同步
     */
    private Subscriber saveToH2WithSync(Subscriber subscriber) {
        subscriber.markSyncPending();
        Subscriber saved = h2Repository.save(subscriber);
        syncService.syncToOracleAsync(saved);
        return saved;
    }

    /**
     * 仅保存到 H2
     */
    private Subscriber saveToH2(Subscriber subscriber) {
        return h2Repository.save(subscriber);
    }

    /**
     * 保存到 Oracle（故障转移模式）
     * 委托给 OracleSyncHelper 确保事务保护
     */
    private Subscriber saveToOracle(Subscriber subscriber) {
        if (oracleRepository == null) {
            throw new DataSourceUnavailableException("Oracle 不可用");
        }

        // 委托给 OracleSyncHelper（有 @Transactional("oracleTransactionManager") 保护）
        syncService.syncSubscriberToOracle(subscriber);

        // 记录到回写队列
        healthService.recordFailoverWrite(subscriber);

        return subscriber;
    }

    // ==================== 读操作 ====================

    /**
     * 根据邮箱查找
     */
    public Optional<Subscriber> findByEmail(String email) {
        // 检查是否启用读写分离（仅 NORMAL 模式）
        DataSourceMode mode = healthService.getCurrentMode();
        if (isReadWriteSplitEnabled() && mode == DataSourceMode.NORMAL) {
            return findByEmailWithReadSplit(email);
        }

        return routeRead(
            () -> h2Repository.findByEmail(email),
            () -> oracleRepository.findByEmail(email));
    }

    /**
     * 读写分离模式下的查询
     */
    private Optional<Subscriber> findByEmailWithReadSplit(String email) {
        ReadStrategy readStrategy = syncProperties.getReadWriteSplit().getReadStrategy();

        switch (readStrategy) {
            case ORACLE_FIRST:
                // Oracle 优先，失败回退 H2
                if (healthService.isOracleAvailable() && oracleRepository != null) {
                    try {
                        return oracleRepository.findByEmail(email);
                    } catch (Exception e) {
                        log.debug("Oracle 读取失败，回退到 H2: {}", e.getMessage());
                    }
                }
                return h2Repository.findByEmail(email);

            case ROUND_ROBIN:
                // 轮询（使用取模防止溢出偏差）
                int count = roundRobinCounter.getAndIncrement() & Integer.MAX_VALUE;
                if (count % 2 == 0 || !healthService.isOracleAvailable() || oracleRepository == null) {
                    try {
                        return h2Repository.findByEmail(email);
                    } catch (Exception e) {
                        if (healthService.isOracleAvailable() && oracleRepository != null) {
                            return oracleRepository.findByEmail(email);
                        }
                        throw e;
                    }
                } else {
                    try {
                        return oracleRepository.findByEmail(email);
                    } catch (Exception e) {
                        return h2Repository.findByEmail(email);
                    }
                }

            case H2_FIRST:
            default:
                // H2 优先，失败回退 Oracle
                try {
                    return h2Repository.findByEmail(email);
                } catch (Exception e) {
                    if (healthService.isOracleAvailable() && oracleRepository != null) {
                        log.debug("H2 读取失败，回退到 Oracle: {}", e.getMessage());
                        return oracleRepository.findByEmail(email);
                    }
                    throw e;
                }
        }
    }

    /**
     * 检查邮箱是否存在
     */
    public boolean existsByEmail(String email) {
        return routeRead(
            () -> h2Repository.existsByEmail(email),
            () -> oracleRepository.existsByEmail(email));
    }

    /**
     * 获取活跃订阅者总数
     */
    public long countActive() {
        return routeRead(
            () -> h2Repository.countByActiveTrue(),
            () -> oracleRepository.countByActiveTrue());
    }

    /**
     * 获取指定时间后新增的订阅数
     */
    public long countCreatedAfter(LocalDateTime since) {
        return routeRead(
            () -> h2Repository.countByActiveTrueAndCreatedAtAfter(since),
            () -> oracleRepository.countByActiveTrueAndCreatedAtAfter(since));
    }

    /**
     * 获取指定时间后活跃的订阅数
     */
    public long countActiveAfter(LocalDateTime since) {
        return routeRead(
            () -> h2Repository.countByActiveTrueAndLastActiveAtAfter(since),
            () -> oracleRepository.countByActiveTrueAndLastActiveAtAfter(since));
    }

    /**
     * 分页获取活跃订阅者
     */
    public Page<Subscriber> findActiveSubscribers(Pageable pageable) {
        return routeRead(
            () -> h2Repository.findByActiveTrue(pageable),
            () -> oracleRepository.findByActiveTrue(pageable));
    }

    /**
     * 获取所有活跃订阅者（导出用）
     */
    public List<Subscriber> findAllActive() {
        return routeRead(
            () -> h2Repository.findByActiveTrueOrderByCreatedAtDesc(),
            () -> oracleRepository.findByActiveTrueOrderByCreatedAtDesc());
    }

    /**
     * 获取订阅来源统计
     */
    public List<Object[]> countBySource() {
        return routeRead(
            () -> h2Repository.countBySource(),
            () -> oracleRepository.countBySource());
    }

    /**
     * 获取复制和下载操作总计
     * 返回 Object[]{totalCopyCount, totalDownloadCount}
     */
    public Object[] sumActionCounts() {
        return routeRead(
            () -> h2Repository.sumActionCounts(),
            () -> oracleRepository.sumActionCounts());
    }

    /**
     * 统计活跃的匿名用户数量
     */
    public long countActiveAnonymous() {
        return routeRead(
            () -> h2Repository.countActiveAnonymous(),
            () -> oracleRepository.countActiveAnonymous());
    }

    // ==================== 辅助方法 ====================

    /**
     * 通用读路由：FAILOVER 时走 Oracle，否则走 H2 并在失败时回退 Oracle
     */
    private <T> T routeRead(Supplier<T> h2Action, Supplier<T> oracleAction) {
        DataSourceMode mode = healthService.getCurrentMode();

        if (mode == DataSourceMode.FAILOVER && oracleRepository != null) {
            return oracleAction.get();
        }

        try {
            return h2Action.get();
        } catch (Exception e) {
            if (mode == DataSourceMode.DEGRADED) {
                throw new DataSourceUnavailableException("系统处于降级模式");
            }
            if (oracleRepository != null) {
                return oracleAction.get();
            }
            throw e;
        }
    }

    /**
     * 检查是否启用读写分离
     */
    private boolean isReadWriteSplitEnabled() {
        return syncProperties.getReadWriteSplit().isEnabled() && healthService.isOracleAvailable();
    }

    /**
     * 获取当前使用的数据源描述
     */
    public String getCurrentDataSourceDescription() {
        DataSourceMode mode = healthService.getCurrentMode();
        String baseDesc = switch (mode) {
            case NORMAL -> "H2 (Primary)";
            case FAILOVER -> "Oracle (Failover)";
            case RECOVERY -> "H2 (Recovery in progress)";
            case DEGRADED -> "Unavailable (Degraded)";
        };

        if (isReadWriteSplitEnabled() && mode == DataSourceMode.NORMAL) {
            ReadStrategy readStrategy = syncProperties.getReadWriteSplit().getReadStrategy();
            WriteStrategy writeStrategy = syncProperties.getReadWriteSplit().getWriteStrategy();
            return String.format("%s [读写分离: 读=%s, 写=%s]", baseDesc, readStrategy, writeStrategy);
        }

        return baseDesc;
    }

    // ==================== 异常类 ====================

    public static class DataSourceUnavailableException extends RuntimeException {
        public DataSourceUnavailableException(String message) {
            super(message);
        }
    }
}
