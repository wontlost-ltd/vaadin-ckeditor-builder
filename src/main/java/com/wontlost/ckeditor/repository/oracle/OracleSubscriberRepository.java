package com.wontlost.ckeditor.repository.oracle;

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
 * Oracle ATP 订阅者 Repository (Secondary)
 * 云端数据库操作，用于备份和同步
 */
@Repository
public interface OracleSubscriberRepository extends JpaRepository<Subscriber, Long> {

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

    @Query(value = "SELECT TRUNC(s.created_at), COUNT(*) FROM subscribers s WHERE s.created_at >= :since GROUP BY TRUNC(s.created_at) ORDER BY TRUNC(s.created_at)", nativeQuery = true)
    List<Object[]> countDailySubscribers(@Param("since") LocalDateTime since);

    /**
     * 统计活跃的匿名用户数量
     */
    @Query("SELECT COUNT(s) FROM Subscriber s WHERE s.active = true AND s.source = 'ANONYMOUS'")
    long countActiveAnonymous();

    /**
     * 通过邮箱查找匿名记录
     */
    @Query("SELECT s FROM Subscriber s WHERE s.email = :email AND s.source = 'ANONYMOUS'")
    Optional<Subscriber> findAnonymousByEmail(@Param("email") String email);

    // 同步相关查询（与 H2SubscriberRepository 对齐）

    List<Subscriber> findBySyncStatusOrderByCreatedAtAsc(Subscriber.SyncStatus syncStatus);

    @Query("SELECT s FROM Subscriber s WHERE s.syncStatus = 'FAILED' AND s.syncRetryCount < :maxRetry ORDER BY s.lastActiveAt DESC")
    List<Subscriber> findFailedForRetry(@Param("maxRetry") int maxRetry);

    long countBySyncStatus(Subscriber.SyncStatus syncStatus);

    /**
     * 查找指定时间之后活跃的记录（分页，用于恢复时仅恢复故障期间变更）
     */
    Page<Subscriber> findByLastActiveAtAfter(LocalDateTime since, Pageable pageable);

    @Query("SELECT s FROM Subscriber s WHERE s.lastActiveAt > :since OR s.syncStatus != 'SYNCED'")
    List<Subscriber> findModifiedSince(@Param("since") LocalDateTime since);
}
