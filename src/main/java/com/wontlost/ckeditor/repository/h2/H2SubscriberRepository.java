package com.wontlost.ckeditor.repository.h2;

import com.wontlost.ckeditor.domain.entity.Subscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * H2 订阅者 Repository (Primary)
 * 本地数据库操作
 */
@Repository
public interface H2SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByActiveTrue();

    long countByActiveTrueAndCreatedAtAfter(LocalDateTime since);

    long countByActiveTrueAndLastActiveAtAfter(LocalDateTime since);

    Page<Subscriber> findByActiveTrue(Pageable pageable);

    List<Subscriber> findByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT s.source, COUNT(s) FROM Subscriber s WHERE s.active = true GROUP BY s.source")
    List<Object[]> countBySource();

    @Query("SELECT COALESCE(SUM(s.copyCount), 0), COALESCE(SUM(s.downloadCount), 0) FROM Subscriber s WHERE s.active = true")
    Object[] sumActionCounts();

    @Query("SELECT DATE(s.createdAt), COUNT(s) FROM Subscriber s WHERE s.createdAt >= :since GROUP BY DATE(s.createdAt) ORDER BY DATE(s.createdAt)")
    List<Object[]> countDailySubscribers(@Param("since") LocalDateTime since);

    /**
     * 统计活跃的匿名用户数量
     */
    @Query("SELECT COUNT(s) FROM Subscriber s WHERE s.active = true AND s.source = 'ANONYMOUS'")
    long countActiveAnonymous();

    /**
     * 删除（软删除）匿名记录：通过邮箱查找并标记为非活跃
     */
    @Query("SELECT s FROM Subscriber s WHERE s.email = :email AND s.source = 'ANONYMOUS'")
    Optional<Subscriber> findAnonymousByEmail(@Param("email") String email);

    // 同步相关查询

    /**
     * 查找待同步的记录（PENDING 状态）
     */
    List<Subscriber> findBySyncStatusOrderByCreatedAtAsc(Subscriber.SyncStatus syncStatus);

    /**
     * 查找待同步的记录（分页版本）
     */
    Page<Subscriber> findBySyncStatusOrderByCreatedAtAsc(Subscriber.SyncStatus syncStatus, Pageable pageable);

    /**
     * 查找同步失败且重试次数未超限的记录
     */
    @Query("SELECT s FROM Subscriber s WHERE s.syncStatus = 'FAILED' AND s.syncRetryCount < :maxRetry ORDER BY s.lastActiveAt DESC")
    List<Subscriber> findFailedForRetry(@Param("maxRetry") int maxRetry);

    /**
     * 查找同步失败且重试次数未超限的记录（分页版本）
     */
    @Query("SELECT s FROM Subscriber s WHERE s.syncStatus = 'FAILED' AND s.syncRetryCount < :maxRetry ORDER BY s.lastActiveAt DESC")
    Page<Subscriber> findFailedForRetryPaged(@Param("maxRetry") int maxRetry, Pageable pageable);

    /**
     * 统计各同步状态的数量
     */
    @Query("SELECT s.syncStatus, COUNT(s) FROM Subscriber s GROUP BY s.syncStatus")
    List<Object[]> countBySyncStatus();

    /**
     * 统计待同步数量
     */
    long countBySyncStatus(Subscriber.SyncStatus syncStatus);

    /**
     * 统计活跃记录中指定同步状态的数量
     */
    long countByActiveTrueAndSyncStatus(Subscriber.SyncStatus syncStatus);

    /**
     * 查找自指定时间后修改过的记录（用于增量同步）
     */
    @Query("SELECT s FROM Subscriber s WHERE s.lastActiveAt > :since OR s.syncStatus != 'SYNCED'")
    List<Subscriber> findModifiedSince(@Param("since") LocalDateTime since);
}
