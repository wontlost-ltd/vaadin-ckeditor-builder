package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订阅者字段映射工具类
 * 集中管理 Subscriber 实体的字段更新和拷贝逻辑
 * 消除 SubscriberDataService、DataSyncService、DataSourceHealthService 中的重复代码
 */
public final class SubscriberFieldMapper {

    private static final Logger log = LoggerFactory.getLogger(SubscriberFieldMapper.class);

    private SubscriberFieldMapper() {}

    /**
     * 将 source 的业务字段更新到 target（不含 id、email、同步状态字段）
     */
    public static void updateFields(Subscriber target, Subscriber source) {
        target.setConfigSnapshot(source.getConfigSnapshot());
        target.setSource(source.getSource());
        target.setLastActiveAt(source.getLastActiveAt());
        target.setActive(source.isActive());
        // 计数器使用 max 合并，避免同步时回退较新的值
        target.setCopyCount(Math.max(target.getCopyCount(), source.getCopyCount()));
        target.setDownloadCount(Math.max(target.getDownloadCount(), source.getDownloadCount()));
    }

    /**
     * 拷贝 source 创建新的 Subscriber（不含 id 和同步状态字段）
     */
    public static Subscriber copy(Subscriber source) {
        Subscriber copy = new Subscriber();
        copy.setEmail(source.getEmail());
        copy.setConfigSnapshot(source.getConfigSnapshot());
        copy.setSource(source.getSource());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setLastActiveAt(source.getLastActiveAt());
        copy.setActive(source.isActive());
        copy.setCopyCount(source.getCopyCount());
        copy.setDownloadCount(source.getDownloadCount());
        return copy;
    }

    /**
     * 从 FailoverEntry 应用字段到 Subscriber
     */
    public static void applyFailoverEntry(Subscriber target, PersistentFailoverQueue.FailoverEntry entry) {
        target.setConfigSnapshot(entry.configSnapshot());
        if (entry.source() != null) {
            try {
                target.setSource(SubscriptionSource.valueOf(entry.source()));
            } catch (IllegalArgumentException e) {
                // 枚举值不匹配（可能数据损坏或版本不兼容），保留原值
                log.warn("枚举值转换失败，保留原值: source='{}', email='{}'", entry.source(), entry.email());
            }
        }
        target.setLastActiveAt(entry.lastActiveAt());
        target.setActive(entry.active());
        target.setCopyCount(Math.max(target.getCopyCount(), entry.copyCount()));
        target.setDownloadCount(Math.max(target.getDownloadCount(), entry.downloadCount()));
    }

    /**
     * 从 FailoverEntry 创建新的 Subscriber
     */
    public static Subscriber createFromFailoverEntry(PersistentFailoverQueue.FailoverEntry entry) {
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(entry.email());
        // 使用原始创建时间（如果可用），否则使用当前时间
        subscriber.setCreatedAt(entry.createdAt() != null ? entry.createdAt() : java.time.LocalDateTime.now());
        applyFailoverEntry(subscriber, entry);
        return subscriber;
    }
}
