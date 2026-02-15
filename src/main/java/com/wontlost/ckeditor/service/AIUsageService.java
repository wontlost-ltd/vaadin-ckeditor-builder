package com.wontlost.ckeditor.service;

import com.wontlost.ckeditor.config.AIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 使用量限速服务
 *
 * 按 HTTP Session 跟踪 AI API 调用次数，在时间窗口内限制请求量。
 * 认证用户与预览用户使用独立的配额和前缀，互不干扰。
 * 窗口到期后配额自动重置。
 */
@Service
public class AIUsageService {

    private static final Logger log = LoggerFactory.getLogger(AIUsageService.class);

    private final AIProperties.UsageLimit config;
    private final ConcurrentHashMap<String, SessionUsage> usageMap = new ConcurrentHashMap<>();

    public AIUsageService(AIProperties aiProperties) {
        this.config = aiProperties.getUsageLimit();
    }

    /**
     * 尝试消费一次 AI 配额
     *
     * @param sessionId HTTP Session ID
     * @param preview   是否为预览用户
     * @return 消费结果（是否允许、剩余次数、窗口重置时间）
     */
    public ConsumeResult tryConsume(String sessionId, boolean preview) {
        String key = (preview ? "preview:" : "auth:") + sessionId;
        int maxLimit = preview ? config.getPreviewSessionMax() : config.getSessionMax();

        SessionUsage usage = usageMap.computeIfAbsent(key, k -> new SessionUsage(maxLimit));
        usage.touch();

        // 窗口过期则重置配额
        long now = System.currentTimeMillis();
        if (now - usage.windowStart >= config.getWindowMs()) {
            usage.reset(maxLimit);
        }

        long resetAtMs = usage.windowStart + config.getWindowMs();

        // 配额已耗尽
        if (usage.remaining.get() <= 0) {
            return new ConsumeResult(false, 0, maxLimit, resetAtMs);
        }

        int newRemaining = usage.remaining.decrementAndGet();
        // 并发竞争导致负数时补偿
        if (newRemaining < 0) {
            usage.remaining.incrementAndGet();
            return new ConsumeResult(false, 0, maxLimit, resetAtMs);
        }

        return new ConsumeResult(true, newRemaining, maxLimit, resetAtMs);
    }

    /**
     * 定期清理非活跃 session，释放内存
     */
    @Scheduled(fixedDelayString = "${ai.usage-limit.cleanup-interval-ms:300000}")
    public void cleanupInactiveSessions() {
        long now = System.currentTimeMillis();
        long timeout = config.getInactiveTimeoutMs();
        int removed = 0;

        var iterator = usageMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().lastAccessTime > timeout) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("清理了 {} 个非活跃 AI session", removed);
        }
    }

    /** 仅用于测试：获取当前跟踪的 session 数量 */
    int getTrackedSessionCount() {
        return usageMap.size();
    }

    /**
     * 消费结果
     *
     * @param allowed    是否允许本次请求
     * @param remaining  剩余配额
     * @param limit      窗口内总配额
     * @param resetAtMs  窗口重置时间戳（毫秒）
     */
    public record ConsumeResult(boolean allowed, int remaining, int limit, long resetAtMs) {}

    /**
     * 单个 session 的使用量跟踪
     */
    static class SessionUsage {
        final AtomicInteger remaining;
        volatile long windowStart;
        volatile long lastAccessTime;

        SessionUsage(int maxLimit) {
            this.remaining = new AtomicInteger(maxLimit);
            this.windowStart = System.currentTimeMillis();
            this.lastAccessTime = this.windowStart;
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        void reset(int maxLimit) {
            this.remaining.set(maxLimit);
            this.windowStart = System.currentTimeMillis();
        }
    }
}
