package com.wontlost.ckeditor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 订阅者服务
 * 处理邮箱订阅、统计查询等业务逻辑
 *
 * 使用 SubscriberDataService 进行智能数据源路由：
 * - 正常模式：H2 为主，Oracle 为备份
 * - 故障转移：自动切换到 Oracle
 * - 恢复模式：H2 恢复后自动回写数据
 */
@Service
public class SubscriberService {

    private static final Logger log = LoggerFactory.getLogger(SubscriberService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    private final SubscriberDataService dataService;
    private final DataSyncService dataSyncService;
    private final DataSourceHealthService healthService;
    private final ObjectMapper objectMapper;

    public SubscriberService(
            SubscriberDataService dataService,
            DataSyncService dataSyncService,
            DataSourceHealthService healthService) {
        this.dataService = dataService;
        this.dataSyncService = dataSyncService;
        this.healthService = healthService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 订阅或更新订阅者信息
     * 不使用 @Transactional：下游的 h2Repository.save() 和 OracleSyncHelper 各自管理事务。
     * 移除 @Transactional 确保 FAILOVER 模式下不会因 H2 事务创建失败而阻塞，
     * 同时让 save() 立即提交，避免 @Async 线程 findById 时实体尚未可见。
     * 捕获 DataIntegrityViolationException 处理并发重复插入
     */
    public Optional<Subscriber> subscribe(String email, SubscriptionSource source, BuilderState config) {
        if (!isValidEmail(email)) {
            log.warn("无效的邮箱格式: {}", email);
            return Optional.empty();
        }

        String normalizedEmail = email.trim().toLowerCase();

        // 检查是否已订阅（智能路由）
        Optional<Subscriber> existing = dataService.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            Subscriber subscriber = existing.get();
            subscriber.updateLastActive();
            // 更新操作计数
            if (source == SubscriptionSource.COPY_CODE) {
                subscriber.incrementCopyCount();
            } else if (source == SubscriptionSource.DOWNLOAD_FILE) {
                subscriber.incrementDownloadCount();
            }
            // 使用智能路由保存
            return Optional.of(dataService.save(subscriber));
        }

        // 创建新订阅
        Subscriber subscriber = new Subscriber(normalizedEmail, source);

        // 保存配置快照
        if (config != null) {
            try {
                Map<String, Object> configMap = buildConfigSnapshot(config);
                String snapshot = objectMapper.writeValueAsString(configMap);
                // 限制快照大小
                if (snapshot.length() <= 2048) {
                    subscriber.setConfigSnapshot(snapshot);
                }
            } catch (JsonProcessingException e) {
                log.warn("配置序列化失败", e);
            }
        }

        // 初始化计数
        if (source == SubscriptionSource.COPY_CODE) {
            subscriber.setCopyCount(1);
        } else if (source == SubscriptionSource.DOWNLOAD_FILE) {
            subscriber.setDownloadCount(1);
        }

        // 使用智能路由保存，捕获并发重复插入
        try {
            Subscriber saved = dataService.save(subscriber);
            log.info("新订阅者: {} (来源: {}, 数据源: {})",
                normalizedEmail, source, dataService.getCurrentDataSourceDescription());
            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            // 并发插入导致唯一约束冲突 → 重试更新路径
            log.debug("并发订阅检测到重复邮箱，切换到更新路径: {}", normalizedEmail);
            return dataService.findByEmail(normalizedEmail).map(existingSub -> {
                existingSub.updateLastActive();
                if (source == SubscriptionSource.COPY_CODE) {
                    existingSub.incrementCopyCount();
                } else if (source == SubscriptionSource.DOWNLOAD_FILE) {
                    existingSub.incrementDownloadCount();
                }
                return dataService.save(existingSub);
            });
        }
    }

    /**
     * 创建或更新匿名用户记录
     * @param anonymousId 匿名标识，格式: anon-<uuid>@anonymous.local
     * @param source 触发来源（COPY_CODE 或 DOWNLOAD_FILE）
     * @param config 当前编辑器配置
     */
    public Optional<Subscriber> createOrUpdateAnonymous(String anonymousId, SubscriptionSource source, BuilderState config) {
        if (anonymousId == null || anonymousId.isEmpty()) {
            return Optional.empty();
        }

        String normalizedId = anonymousId.trim().toLowerCase();

        // 查找已有匿名记录
        Optional<Subscriber> existing = dataService.findByEmail(normalizedId);
        if (existing.isPresent()) {
            Subscriber subscriber = existing.get();
            subscriber.updateLastActive();
            if (source == SubscriptionSource.COPY_CODE) {
                subscriber.incrementCopyCount();
            } else if (source == SubscriptionSource.DOWNLOAD_FILE) {
                subscriber.incrementDownloadCount();
            }
            return Optional.of(dataService.save(subscriber));
        }

        // 创建新匿名记录
        Subscriber subscriber = new Subscriber(normalizedId, SubscriptionSource.ANONYMOUS);

        // 保存配置快照
        if (config != null) {
            try {
                Map<String, Object> configMap = buildConfigSnapshot(config);
                String snapshot = objectMapper.writeValueAsString(configMap);
                if (snapshot.length() <= 2048) {
                    subscriber.setConfigSnapshot(snapshot);
                }
            } catch (JsonProcessingException e) {
                log.warn("配置序列化失败", e);
            }
        }

        // 初始化计数
        if (source == SubscriptionSource.COPY_CODE) {
            subscriber.setCopyCount(1);
        } else if (source == SubscriptionSource.DOWNLOAD_FILE) {
            subscriber.setDownloadCount(1);
        }

        try {
            Subscriber saved = dataService.save(subscriber);
            log.info("新匿名用户: {} (触发来源: {}, 数据源: {})",
                normalizedId, source, dataService.getCurrentDataSourceDescription());
            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            // 并发插入 → 重试更新路径
            log.debug("并发匿名用户检测到重复，切换到更新路径: {}", normalizedId);
            return dataService.findByEmail(normalizedId).map(existingSub -> {
                existingSub.updateLastActive();
                if (source == SubscriptionSource.COPY_CODE) {
                    existingSub.incrementCopyCount();
                } else if (source == SubscriptionSource.DOWNLOAD_FILE) {
                    existingSub.incrementDownloadCount();
                }
                return dataService.save(existingSub);
            });
        }
    }

    /**
     * 将匿名用户数据合并到真实订阅记录
     * @param anonymousId 匿名标识
     * @param realEmail 真实订阅邮箱
     */
    public void mergeAnonymousToSubscriber(String anonymousId, String realEmail) {
        if (anonymousId == null || realEmail == null) return;

        String normalizedAnonymousId = anonymousId.trim().toLowerCase();
        String normalizedEmail = realEmail.trim().toLowerCase();

        Optional<Subscriber> anonymousOpt = dataService.findByEmail(normalizedAnonymousId);
        Optional<Subscriber> realOpt = dataService.findByEmail(normalizedEmail);

        if (anonymousOpt.isEmpty() || realOpt.isEmpty()) {
            log.debug("合并跳过: 匿名记录={}, 真实记录={}", anonymousOpt.isPresent(), realOpt.isPresent());
            return;
        }

        Subscriber anonymous = anonymousOpt.get();
        Subscriber real = realOpt.get();

        // 累加计数
        real.setCopyCount(real.getCopyCount() + anonymous.getCopyCount());
        real.setDownloadCount(real.getDownloadCount() + anonymous.getDownloadCount());

        // 取较早的创建时间
        if (anonymous.getCreatedAt().isBefore(real.getCreatedAt())) {
            real.setCreatedAt(anonymous.getCreatedAt());
        }

        dataService.save(real);

        // 软删除匿名记录
        anonymous.setActive(false);
        dataService.save(anonymous);

        log.info("匿名用户数据已合并: {} → {} (复制+{}, 下载+{})",
            normalizedAnonymousId, normalizedEmail,
            anonymous.getCopyCount(), anonymous.getDownloadCount());
    }

    /**
     * 获取活跃匿名用户数量
     */
    public long getAnonymousCount() {
        return dataService.countActiveAnonymous();
    }

    /**
     * 记录用户活动（已订阅用户）
     */
    public void recordActivity(String email, SubscriptionSource action) {
        if (email == null) return;
        dataService.findByEmail(email.trim().toLowerCase()).ifPresent(subscriber -> {
            subscriber.updateLastActive();
            if (action == SubscriptionSource.COPY_CODE) {
                subscriber.incrementCopyCount();
            } else if (action == SubscriptionSource.DOWNLOAD_FILE) {
                subscriber.incrementDownloadCount();
            }
            dataService.save(subscriber);
        });
    }

    /**
     * 检查邮箱是否已订阅
     */
    public boolean isSubscribed(String email) {
        if (email == null) return false;
        return dataService.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * 获取总订阅数
     */
    public long getTotalCount() {
        return dataService.countActive();
    }

    /**
     * 获取指定日期之后的新增订阅数
     */
    public long getNewCountSince(LocalDate date) {
        return dataService.countCreatedAfter(date.atStartOfDay());
    }

    /**
     * 获取指定日期之后的活跃用户数
     */
    public long getActiveCountSince(LocalDate date) {
        return dataService.countActiveAfter(date.atStartOfDay());
    }

    /**
     * 分页获取订阅者列表
     */
    public Page<Subscriber> getSubscribers(Pageable pageable) {
        return dataService.findActiveSubscribers(pageable);
    }

    /**
     * 获取所有订阅者（用于导出）
     */
    public List<Subscriber> getAllSubscribers() {
        return dataService.findAllActive();
    }

    /**
     * 获取订阅来源统计（按注册来源分组的订阅者数量）
     */
    public Map<SubscriptionSource, Long> getSourceStats() {
        Map<SubscriptionSource, Long> stats = new EnumMap<>(SubscriptionSource.class);
        dataService.countBySource().forEach(row -> {
            SubscriptionSource source = (SubscriptionSource) row[0];
            Long count = (Long) row[1];
            stats.put(source, count);
        });
        return stats;
    }

    /**
     * 获取复制和下载操作总计
     * @return long[]{totalCopyCount, totalDownloadCount}
     */
    public long[] getActionCounts() {
        Object[] result = dataService.sumActionCounts();
        if (result == null) return new long[]{0L, 0L};
        // 查询返回单行，result 可能是 Object[]{copySum, downloadSum}
        // 或包裹在外层数组中
        Object[] row = result;
        if (result.length > 0 && result[0] instanceof Object[]) {
            row = (Object[]) result[0];
        }
        long totalCopy = row.length > 0 && row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long totalDownload = row.length > 1 && row[1] != null ? ((Number) row[1]).longValue() : 0L;
        return new long[]{totalCopy, totalDownload};
    }

    /**
     * 导出订阅者为 CSV 格式（分页加载，避免大数据集 OOM）
     */
    public String exportToCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("邮箱,来源,订阅时间,最后活跃,复制次数,下载次数,同步状态\n");

        int pageNum = 0;
        int pageSize = 500;
        Page<Subscriber> page;
        do {
            page = dataService.findActiveSubscribers(
                org.springframework.data.domain.PageRequest.of(pageNum, pageSize));
            for (Subscriber s : page.getContent()) {
                csv.append(String.format("%s,%s,%s,%s,%d,%d,%s\n",
                    escapeCsvField(s.getEmail()),
                    escapeCsvField(s.getSource() != null ? s.getSource().getDisplayName() : ""),
                    escapeCsvField(s.getCreatedAt().toString()),
                    escapeCsvField(s.getLastActiveAt() != null ? s.getLastActiveAt().toString() : ""),
                    s.getCopyCount(),
                    s.getDownloadCount(),
                    escapeCsvField(s.getSyncStatus() != null ? s.getSyncStatus().name() : "N/A")
                ));
            }
            pageNum++;
        } while (page.hasNext());

        return csv.toString();
    }

    // ==================== 同步相关 ====================

    /**
     * 获取同步状态
     */
    public DataSyncService.SyncStatus getSyncStatus() {
        return dataSyncService.getSyncStatus();
    }

    /**
     * 手动触发全量同步
     */
    public DataSyncService.SyncResult triggerFullSync() {
        return dataSyncService.manualFullSync();
    }

    /**
     * 手动触发增量同步
     */
    public DataSyncService.SyncResult triggerIncrementalSync() {
        return dataSyncService.incrementalSync("MANUAL");
    }

    /**
     * 从 Oracle 恢复数据
     */
    public DataSyncService.SyncResult restoreFromOracle() {
        return dataSyncService.restoreFromOracle();
    }

    /**
     * 获取最近的同步审计日志
     */
    public List<SyncAuditLog> getRecentSyncLogs() {
        return dataSyncService.getRecentAuditLogs();
    }

    // ==================== 高可用相关 ====================

    /**
     * 获取健康状态
     */
    public DataSourceHealthService.HealthStatus getHealthStatus() {
        return healthService.getHealthStatus();
    }

    /**
     * 获取当前数据源模式
     */
    public DataSourceMode getCurrentDataSourceMode() {
        return healthService.getCurrentMode();
    }

    /**
     * 获取当前数据源描述
     */
    public String getCurrentDataSourceDescription() {
        return dataService.getCurrentDataSourceDescription();
    }

    /**
     * 手动触发故障转移
     */
    public boolean manualFailover() {
        return healthService.manualFailover();
    }

    /**
     * 手动触发恢复
     */
    public boolean manualRecovery() {
        return healthService.manualRecovery();
    }

    /**
     * 系统是否可用
     */
    public boolean isSystemAvailable() {
        return healthService.isSystemAvailable();
    }

    // ==================== 私有方法 ====================

    /**
     * CSV 字段转义：引用字段并转义内嵌引号，防止 CSV 注入
     */
    private String escapeCsvField(String field) {
        if (field == null) return "";
        // 防止 CSV 公式注入（=, +, -, @, \t, \r 开头）
        String safe = field;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        // 包含逗号、引号或换行时，用双引号包裹并转义内嵌引号
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private Map<String, Object> buildConfigSnapshot(BuilderState config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (config.getPreset() != null) {
            snapshot.put("preset", config.getPreset().name());
        }
        if (config.getEditorType() != null) {
            snapshot.put("editorType", config.getEditorType().name());
        }
        if (config.getTheme() != null) {
            snapshot.put("theme", config.getTheme().name());
        }
        snapshot.put("language", config.getLanguage());
        if (config.getSelectedPlugins() != null) {
            snapshot.put("pluginCount", config.getSelectedPlugins().size());
        }
        if (config.getToolbarItems() != null) {
            snapshot.put("toolbarItemCount", config.getToolbarItems().size());
        }
        return snapshot;
    }
}
