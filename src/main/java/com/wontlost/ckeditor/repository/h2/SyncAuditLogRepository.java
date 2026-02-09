package com.wontlost.ckeditor.repository.h2;

import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
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
 * 同步审计日志 Repository
 */
@Repository
public interface SyncAuditLogRepository extends JpaRepository<SyncAuditLog, Long> {

    /**
     * 获取最近的同步日志
     */
    List<SyncAuditLog> findTop10ByOrderBySyncTimeDesc();

    /**
     * 分页获取同步日志
     */
    Page<SyncAuditLog> findByOrderBySyncTimeDesc(Pageable pageable);

    /**
     * 获取指定时间后的同步日志
     */
    List<SyncAuditLog> findBySyncTimeAfterOrderBySyncTimeDesc(LocalDateTime since);

    /**
     * 获取最后一次成功的同步
     */
    Optional<SyncAuditLog> findFirstByStatusOrderBySyncTimeDesc(SyncAuditLog.Status status);

    /**
     * 统计指定时间段内的同步次数
     */
    @Query("SELECT COUNT(s) FROM SyncAuditLog s WHERE s.syncTime >= :since")
    long countSyncsSince(@Param("since") LocalDateTime since);

    /**
     * 统计失败的同步次数
     */
    @Query("SELECT COUNT(s) FROM SyncAuditLog s WHERE s.syncTime >= :since AND s.status IN ('FAILED', 'PARTIAL')")
    long countFailedSyncsSince(@Param("since") LocalDateTime since);

    /**
     * 获取同步统计摘要
     */
    @Query("SELECT s.status, COUNT(s) FROM SyncAuditLog s WHERE s.syncTime >= :since GROUP BY s.status")
    List<Object[]> getSyncStatusSummary(@Param("since") LocalDateTime since);
}
