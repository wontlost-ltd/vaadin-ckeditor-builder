package com.wontlost.ckeditor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wontlost.ckeditor.config.DataSyncProperties;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 持久化回写队列
 * 将故障转移期间的写入操作持久化到文件，防止重启丢失
 */
@Component
public class PersistentFailoverQueue {

    private static final Logger log = LoggerFactory.getLogger(PersistentFailoverQueue.class);

    private final DataSyncProperties syncProperties;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<FailoverEntry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicInteger queueSize = new java.util.concurrent.atomic.AtomicInteger(0);

    public PersistentFailoverQueue(DataSyncProperties syncProperties) {
        this.syncProperties = syncProperties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        if (syncProperties.getFailoverQueue().isPersistent()) {
            loadFromFile();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (syncProperties.getFailoverQueue().isPersistent() && dirty.get()) {
            persistToFile();
        }
    }

    /**
     * 添加条目到队列（原子容量检查）
     * 使用 synchronized 确保容量检查与入队操作的原子性，防止并发超出限制
     */
    public synchronized boolean offer(Subscriber subscriber) {
        int maxSize = syncProperties.getFailoverQueue().getMaxSize();
        if (queueSize.get() >= maxSize) {
            log.warn("回写队列已满 ({}), 拒绝添加: {}", maxSize, subscriber.getEmail());
            return false;
        }

        FailoverEntry entry = new FailoverEntry(
            subscriber.getEmail(),
            subscriber.getConfigSnapshot(),
            subscriber.getSource() != null ? subscriber.getSource().name() : null,
            subscriber.getLastActiveAt(),
            subscriber.getCreatedAt(),
            subscriber.isActive(),
            subscriber.getCopyCount(),
            subscriber.getDownloadCount(),
            System.currentTimeMillis()
        );

        boolean added = queue.offer(entry);
        if (added) {
            queueSize.incrementAndGet();
            dirty.set(true);
            log.debug("添加到回写队列: {}", subscriber.getEmail());
        }
        return added;
    }

    /**
     * 从队列取出条目
     */
    public synchronized FailoverEntry poll() {
        FailoverEntry entry = queue.poll();
        if (entry != null) {
            queueSize.decrementAndGet();
            dirty.set(true);
        }
        return entry;
    }

    /**
     * 获取队列大小
     */
    public int size() {
        return queueSize.get();
    }

    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        return queueSize.get() == 0;
    }

    /**
     * 清空队列
     */
    public synchronized void clear() {
        queue.clear();
        queueSize.set(0);
        dirty.set(true);
    }

    /**
     * 获取所有条目（不移除）
     */
    public List<FailoverEntry> peekAll() {
        return new ArrayList<>(queue);
    }

    /**
     * 将条目重新入队（用于处理失败后恢复）
     * 不受 maxSize 限制，确保不丢失数据
     */
    public synchronized void requeue(FailoverEntry entry) {
        queue.offer(entry);
        queueSize.incrementAndGet();
        dirty.set(true);
    }

    /**
     * 定时持久化（每 5 秒）
     */
    @Scheduled(fixedRateString = "${app.sync.failover-queue.persist-interval:5000}")
    public void scheduledPersist() {
        if (syncProperties.getFailoverQueue().isPersistent() && dirty.compareAndSet(true, false)) {
            persistToFile();
        }
    }

    /**
     * 持久化到文件
     */
    private synchronized void persistToFile() {
        String filePath = syncProperties.getFailoverQueue().getFilePath();
        Path path = Path.of(filePath);
        Path tempPath = Path.of(filePath + ".tmp");

        try {
            // 确保目录存在
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            // 先写入临时文件
            List<FailoverEntry> entries = new ArrayList<>(queue);
            objectMapper.writeValue(tempPath.toFile(), entries);

            // 原子替换，部分文件系统不支持 ATOMIC_MOVE 时降级为普通替换
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                log.warn("文件系统不支持 ATOMIC_MOVE，降级为普通替换");
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }

            log.debug("回写队列已持久化: {} 条记录", entries.size());
        } catch (IOException e) {
            log.error("持久化回写队列失败", e);
            // 恢复 dirty 标记以便重试
            dirty.set(true);
            // 清理临时文件
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // 忽略清理失败
            }
        }
    }

    /**
     * 从文件加载
     */
    private synchronized void loadFromFile() {
        String filePath = syncProperties.getFailoverQueue().getFilePath();
        File file = new File(filePath);

        if (!file.exists()) {
            log.info("回写队列文件不存在，跳过加载: {}", filePath);
            return;
        }

        try {
            List<FailoverEntry> entries = objectMapper.readValue(
                file,
                new TypeReference<List<FailoverEntry>>() {}
            );

            queue.clear();
            queue.addAll(entries);
            queueSize.set(entries.size());

            log.info("从文件加载回写队列: {} 条记录", entries.size());
        } catch (IOException e) {
            log.error("加载回写队列文件失败", e);
        }
    }

    /**
     * 回写队列条目
     */
    public record FailoverEntry(
        String email,
        String configSnapshot,
        String source,
        java.time.LocalDateTime lastActiveAt,
        java.time.LocalDateTime createdAt,
        boolean active,
        int copyCount,
        int downloadCount,
        long timestamp
    ) {}
}
