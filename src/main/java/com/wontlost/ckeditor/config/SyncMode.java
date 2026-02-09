package com.wontlost.ckeditor.config;

/**
 * 数据同步模式
 */
public enum SyncMode {
    /**
     * 实时同步：每次写入 H2 后立即同步到 Oracle
     */
    REALTIME,

    /**
     * 定时同步：按固定间隔全量同步
     */
    SCHEDULED,

    /**
     * 手动同步：仅通过手动触发同步
     */
    MANUAL
}
