package com.wontlost.ckeditor.config;

/**
 * 数据源模式
 */
public enum DataSourceMode {
    /**
     * 正常模式：H2 为主，Oracle 为备份
     */
    NORMAL,

    /**
     * 故障转移模式：Oracle 为主（H2 不可用）
     */
    FAILOVER,

    /**
     * 恢复模式：正在从 Oracle 回写数据到 H2
     */
    RECOVERY,

    /**
     * 降级模式：H2 和 Oracle 都不可用，仅内存缓存
     */
    DEGRADED
}
