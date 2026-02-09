# Oracle ATP 集成设计文档

## 1. 概述

### 1.1 目标

将 Oracle Autonomous Transaction Processing (ATP) 集成到现有项目，实现 H2 + Oracle 双数据库并行模式，支持：

- 数据实时/定时同步
- H2 故障时自动切换到 Oracle
- H2 恢复后自动回写数据并切回

### 1.2 设计原则

- **H2 优先**：H2 作为主数据源，本地访问速度快
- **Oracle 备份**：Oracle 作为云端备份，提供灾难恢复能力
- **自动故障转移**：H2 故障时无需人工干预，自动切换
- **数据零丢失**：故障转移和恢复过程中不丢失任何数据

---

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Application Layer                              │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                      SubscriberService                          │    │
│  │                    (业务逻辑层)                                  │    │
│  └─────────────────────────────┬───────────────────────────────────┘    │
│                                │                                         │
│  ┌─────────────────────────────▼───────────────────────────────────┐    │
│  │                   SubscriberDataService                         │    │
│  │                  (智能数据源路由层)                              │    │
│  │  ┌─────────────────────────────────────────────────────────┐   │    │
│  │  │  根据 DataSourceMode 自动路由读写操作                    │   │    │
│  │  │  NORMAL → H2 | FAILOVER → Oracle | RECOVERY → 双写      │   │    │
│  │  └─────────────────────────────────────────────────────────┘   │    │
│  └──────────┬──────────────────┬──────────────────┬────────────────┘    │
│             │                  │                  │                      │
│  ┌──────────▼──────────┐ ┌────▼────────────┐ ┌───▼───────────────┐     │
│  │   DataSyncService   │ │DataSourceHealth │ │   回写队列         │     │
│  │    (同步服务)       │ │    Service      │ │ (ConcurrentQueue) │     │
│  │                     │ │  (健康检查)     │ │                   │     │
│  │ - 实时同步          │ │                 │ │ 故障转移期间      │     │
│  │ - 定时增量同步      │ │ - 每30秒检查    │ │ 暂存写入操作      │     │
│  │ - 失败重试          │ │ - 故障转移触发  │ │ 恢复时回写        │     │
│  │ - 审计日志          │ │ - 恢复触发      │ │                   │     │
│  └──────────┬──────────┘ └────┬────────────┘ └───────────────────┘     │
│             │                  │                                         │
└─────────────┼──────────────────┼─────────────────────────────────────────┘
              │                  │
┌─────────────┼──────────────────┼─────────────────────────────────────────┐
│             │   Repository Layer                                         │
│  ┌──────────▼──────────┐ ┌────▼────────────┐                            │
│  │ H2SubscriberRepo    │ │OracleSubscriber │                            │
│  │ SyncAuditLogRepo    │ │    Repo         │                            │
│  └──────────┬──────────┘ └────┬────────────┘                            │
│             │                  │                                         │
└─────────────┼──────────────────┼─────────────────────────────────────────┘
              │                  │
┌─────────────▼──────────┐ ┌────▼────────────┐
│   H2 Database          │ │  Oracle ATP     │
│   (Primary)            │ │  (Secondary)    │
│                        │ │                 │
│   本地嵌入式数据库     │ │  云端数据库     │
│   ./data/ckeditor-     │ │  Always Free    │
│   builder.mv.db        │ │                 │
└────────────────────────┘ └─────────────────┘
```

### 2.2 数据流向

#### 正常模式 (NORMAL)

```
写入请求 → SubscriberDataService → H2 (同步) → Oracle (异步)
读取请求 → SubscriberDataService → H2
```

#### 故障转移模式 (FAILOVER)

```
写入请求 → SubscriberDataService → Oracle (同步) → 回写队列 (记录)
读取请求 → SubscriberDataService → Oracle
```

#### 恢复模式 (RECOVERY)

```
写入请求 → SubscriberDataService → H2 + Oracle (双写)
读取请求 → SubscriberDataService → H2
回写任务 → Oracle 数据 → H2 (增量回写)
```

---

## 3. 数据源模式

### 3.1 模式定义

| 模式 | 说明 | 读操作 | 写操作 |
|------|------|--------|--------|
| `NORMAL` | 正常模式，H2 为主 | H2 | H2 + 异步同步 Oracle |
| `FAILOVER` | 故障转移，Oracle 为主 | Oracle | Oracle + 记录回写队列 |
| `RECOVERY` | 恢复中，双写模式 | H2 | H2 + Oracle |
| `DEGRADED` | 降级模式，都不可用 | 抛异常 | 抛异常 |

### 3.2 模式转换状态机

```
                    ┌─────────────────────────────────────┐
                    │                                     │
                    ▼                                     │
              ┌──────────┐                                │
              │  NORMAL  │◄───────────────────────┐       │
              └────┬─────┘                        │       │
                   │                              │       │
                   │ H2 连续3次                   │       │
                   │ 健康检查失败                 │       │
                   ▼                              │       │
              ┌──────────┐    H2 连续3次    ┌─────┴────┐  │
              │ FAILOVER │───健康检查成功──►│ RECOVERY │──┘
              └────┬─────┘                  └──────────┘
                   │                           恢复完成
                   │ Oracle 也不可用
                   ▼
              ┌──────────┐
              │ DEGRADED │
              └──────────┘
```

---

## 4. 同步策略

### 4.1 同步类型

| 类型 | 触发方式 | 行为 | 适用场景 |
|------|---------|------|---------|
| **实时同步** | 每次写入后 | 异步同步单条记录到 Oracle | REALTIME 模式 |
| **增量同步** | 定时 (5分钟) | 同步 PENDING 和可重试 FAILED | SCHEDULED 模式 |
| **全量同步** | 手动触发 | 遍历所有记录，跳过已同步未修改 | 初始化或修复 |
| **失败重试** | 定时 (15分钟) | 重试 FAILED 且 retryCount < 3 | 自动修复 |

### 4.2 同步状态（记录级别）

每条 `Subscriber` 记录维护独立的同步状态：

```java
public enum SyncStatus {
    PENDING,    // 待同步
    SYNCED,     // 已同步
    FAILED      // 同步失败
}

// Subscriber 实体新增字段
private SyncStatus syncStatus;      // 同步状态
private LocalDateTime lastSyncedAt; // 最后同步成功时间
private int syncRetryCount;         // 重试次数
private String lastSyncError;       // 最后错误信息
```

### 4.3 同步流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        写入记录                                  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ syncStatus =    │
                    │ PENDING         │
                    └────────┬────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ 保存到 H2       │
                    └────────┬────────┘
                              │
                              ▼
              ┌───────────────┴───────────────┐
              │ 是否 REALTIME 模式且 Oracle 可用?│
              └───────────────┬───────────────┘
                     YES      │       NO
              ┌───────────────┴───────────────┐
              ▼                               ▼
    ┌─────────────────┐             ┌─────────────────┐
    │ 异步同步到 Oracle│             │ 等待定时同步    │
    └────────┬────────┘             └─────────────────┘
              │
              ▼
    ┌─────────────────┐
    │ 同步成功?       │
    └────────┬────────┘
        YES  │   NO
    ┌────────┴────────┐
    ▼                 ▼
┌────────┐    ┌──────────────┐
│SYNCED  │    │FAILED        │
│        │    │retryCount++  │
│lastSync│    │lastSyncError │
│= now   │    │= errorMsg    │
└────────┘    └──────────────┘
                    │
                    ▼ (定时重试，最多3次)
              ┌─────────────────┐
              │ retryCount < 3? │
              └────────┬────────┘
                  YES  │   NO
              ┌────────┴────────┐
              ▼                 ▼
        ┌──────────┐    ┌──────────────┐
        │ 重试同步 │    │ 保持 FAILED  │
        └──────────┘    │ 需人工干预   │
                        └──────────────┘
```

### 4.4 审计日志

每次同步操作记录到 `SyncAuditLog`：

```java
public class SyncAuditLog {
    private Long id;
    private LocalDateTime syncTime;     // 同步时间
    private SyncType syncType;          // 同步类型
    private Status status;              // 执行状态
    private int totalRecords;           // 总记录数
    private int successCount;           // 成功数
    private int failCount;              // 失败数
    private int skippedCount;           // 跳过数
    private long durationMs;            // 耗时
    private String errorMessage;        // 错误信息
    private String triggeredBy;         // 触发者
}

public enum SyncType {
    REALTIME_SINGLE,    // 实时单条
    SCHEDULED_INCR,     // 定时增量
    MANUAL_FULL,        // 手动全量
    RETRY_FAILED,       // 重试失败
    RESTORE             // 恢复
}
```

---

## 5. 故障转移

### 5.1 健康检查

```java
// 每 30 秒执行一次
@Scheduled(fixedRate = 30000)
public void scheduledHealthCheck() {
    boolean h2Healthy = checkH2Health();      // SELECT count(*) FROM subscribers
    boolean oracleHealthy = checkOracleHealth();

    // 根据健康状态决定模式转换
    ...
}
```

### 5.2 故障转移触发条件

- H2 连续 **3 次**健康检查失败
- Oracle 可用

### 5.3 故障转移流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     H2 健康检查失败                              │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ h2FailureCount++│
                    └────────┬────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ h2FailureCount >= 3 ?         │
              └───────────────┬───────────────┘
                     NO       │       YES
              ┌───────────────┴───────────────┐
              ▼                               ▼
    ┌─────────────────┐             ┌─────────────────┐
    │ 保持 NORMAL     │             │ Oracle 可用?    │
    │ 继续监控        │             └────────┬────────┘
    └─────────────────┘                 YES  │   NO
                                ┌────────────┴────────────┐
                                ▼                         ▼
                      ┌─────────────────┐       ┌─────────────────┐
                      │ 切换到 FAILOVER │       │ 切换到 DEGRADED │
                      │                 │       │ 系统不可用      │
                      │ 1. 记录开始时间 │       └─────────────────┘
                      │ 2. 清空回写队列 │
                      │ 3. 记录审计日志 │
                      └─────────────────┘
```

### 5.4 故障转移期间的数据处理

```java
// FAILOVER 模式下的写入
private Subscriber saveToOracle(Subscriber subscriber) {
    // 1. 写入 Oracle
    Subscriber saved = oracleRepository.save(subscriber);

    // 2. 记录到回写队列（恢复时使用）
    healthService.recordFailoverWrite(saved);

    return saved;
}
```

---

## 6. 自动恢复

### 6.1 恢复触发条件

- 当前处于 FAILOVER 模式
- H2 连续 **3 次**健康检查成功

### 6.2 恢复流程

```
┌─────────────────────────────────────────────────────────────────┐
│                   触发恢复 (RECOVERY)                            │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              步骤 1: 从 Oracle 回写数据到 H2                     │
│                                                                 │
│  for each record in Oracle:                                     │
│      if (record.lastActiveAt > h2Record.lastActiveAt):          │
│          update h2Record                                        │
│      else if (record not in H2):                                │
│          insert into H2 (故障期间新增)                          │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              步骤 2: 处理回写队列                                │
│                                                                 │
│  while (failoverWriteQueue.poll() != null):                     │
│      save to H2                                                 │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              步骤 3: 切回 NORMAL 模式                            │
│                                                                 │
│  1. currentMode = NORMAL                                        │
│  2. 清空 failoverStartTime                                      │
│  3. 清空 failoverWriteQueue                                     │
│  4. 记录审计日志                                                │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 数据一致性保证

| 场景 | 处理方式 |
|------|---------|
| Oracle 有更新，H2 有旧数据 | 用 Oracle 覆盖 H2（基于 lastActiveAt 比较） |
| Oracle 有新记录，H2 没有 | 在 H2 中创建（故障期间新增的） |
| H2 有记录，Oracle 没有 | 保留 H2 数据（故障前已存在） |
| 回写队列中有记录 | 逐条写入 H2 |

---

## 7. 配置

### 7.1 application.properties

```properties
# ================================
# H2 Database Configuration (Primary)
# ================================
spring.datasource.h2.url=jdbc:h2:file:./data/ckeditor-builder;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.h2.driver-class-name=org.h2.Driver
spring.datasource.h2.username=sa
spring.datasource.h2.password=

# ================================
# Oracle ATP Database Configuration (Secondary)
# ================================
spring.datasource.oracle.url=jdbc:oracle:thin:@${ORACLE_TNS_ALIAS:ckeditor_low}?TNS_ADMIN=${ORACLE_WALLET_PATH:./wallet}
spring.datasource.oracle.driver-class-name=oracle.jdbc.OracleDriver
spring.datasource.oracle.username=${ORACLE_USERNAME:ADMIN}
spring.datasource.oracle.password=${ORACLE_PASSWORD:}

# ================================
# Data Sync Configuration
# ================================
# 同步模式: REALTIME, SCHEDULED, MANUAL
app.sync.mode=${SYNC_MODE:REALTIME}

# 定时同步间隔 (毫秒)，默认 5 分钟
app.sync.scheduled-interval=300000

# 启用 Oracle 同步
app.sync.oracle-enabled=${ORACLE_ENABLED:false}
```

### 7.2 环境变量 (.env)

```bash
# Oracle ATP Configuration
ORACLE_ENABLED=true
ORACLE_TNS_ALIAS=ckeditor_low
ORACLE_WALLET_PATH=./wallet
ORACLE_USERNAME=ADMIN
ORACLE_PASSWORD=your-password

# Sync mode
SYNC_MODE=REALTIME
```

### 7.3 硬编码常量

```java
// DataSourceHealthService
private static final int FAILURE_THRESHOLD = 3;    // 故障阈值
private static final int RECOVERY_THRESHOLD = 3;   // 恢复阈值
private static final int HEALTH_CHECK_INTERVAL = 30000; // 健康检查间隔 (ms)

// DataSyncService
private static final int MAX_RETRY_COUNT = 3;      // 最大重试次数
```

---

## 8. API 接口

### 8.1 同步相关

```java
// 获取同步状态
DataSyncService.SyncStatus getSyncStatus();
// 返回: oracleAvailable, syncMode, h2Count, oracleCount,
//       pendingCount, failedCount, syncedCount, lastSyncTime

// 手动全量同步
DataSyncService.SyncResult triggerFullSync();

// 手动增量同步
DataSyncService.SyncResult triggerIncrementalSync();

// 从 Oracle 恢复
DataSyncService.SyncResult restoreFromOracle();

// 获取审计日志
List<SyncAuditLog> getRecentSyncLogs();
```

### 8.2 高可用相关

```java
// 获取健康状态
DataSourceHealthService.HealthStatus getHealthStatus();
// 返回: currentMode, h2Healthy, oracleHealthy, h2FailureCount,
//       h2SuccessCount, failoverStartTime, lastHealthCheckTime, pendingQueueSize

// 获取当前数据源模式
DataSourceMode getCurrentDataSourceMode();

// 获取当前数据源描述
String getCurrentDataSourceDescription();

// 手动故障转移（测试用）
boolean manualFailover();

// 手动恢复（测试用）
boolean manualRecovery();

// 系统是否可用
boolean isSystemAvailable();
```

---

## 9. 文件清单

### 9.1 新增文件

| 文件路径 | 说明 |
|---------|------|
| `config/DataSourceMode.java` | 数据源模式枚举 |
| `config/SyncMode.java` | 同步模式枚举 |
| `config/H2DataSourceConfig.java` | H2 数据源配置 |
| `config/OracleDataSourceConfig.java` | Oracle 数据源配置 |
| `domain/entity/SyncAuditLog.java` | 同步审计日志实体 |
| `repository/h2/H2SubscriberRepository.java` | H2 订阅者仓库 |
| `repository/h2/SyncAuditLogRepository.java` | 审计日志仓库 |
| `repository/oracle/OracleSubscriberRepository.java` | Oracle 订阅者仓库 |
| `service/DataSyncService.java` | 数据同步服务 |
| `service/DataSourceHealthService.java` | 健康检查与故障转移服务 |
| `service/SubscriberDataService.java` | 智能数据源路由服务 |

### 9.2 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `build.gradle` | 添加 Oracle JDBC 依赖 |
| `application.properties` | 双数据源配置 |
| `.gitignore` | 排除 wallet 目录 |
| `.env.example` | Oracle 配置模板 |
| `domain/entity/Subscriber.java` | 添加同步状态字段 |
| `service/SubscriberService.java` | 集成智能路由和高可用 |
| `Application.java` | 启用定时任务和异步 |

---

## 10. 部署指南

### 10.1 Oracle ATP 设置

1. 登录 Oracle Cloud Console
2. 创建 ATP 实例（选择 Always Free）
3. 下载 Wallet ZIP
4. 解压到项目 `./wallet` 目录
5. 查看 `tnsnames.ora` 获取 TNS 别名

### 10.2 配置步骤

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 编辑 .env，配置 Oracle 连接
ORACLE_ENABLED=true
ORACLE_TNS_ALIAS=ckeditor_low
ORACLE_WALLET_PATH=./wallet
ORACLE_USERNAME=ADMIN
ORACLE_PASSWORD=your-password

# 3. 启动应用
./gradlew bootRun
```

### 10.3 验证步骤

1. 启动应用，确认两个数据源都连接成功
2. 在主页订阅，验证数据写入 H2
3. 检查 Oracle ATP 中是否同步了数据
4. 访问 `/h2-console` 确认 H2 数据正常
5. 测试从 Oracle 恢复数据到 H2

---

## 11. 监控与运维

### 11.1 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `currentMode` | 当前数据源模式 | != NORMAL |
| `pendingCount` | 待同步记录数 | > 100 |
| `failedCount` | 同步失败记录数 | > 10 |
| `h2FailureCount` | H2 连续失败次数 | >= 2 |
| `pendingQueueSize` | 回写队列大小 | > 1000 |

### 11.2 日志关键字

```
# 故障转移
"故障转移已启动"
"切换到 Oracle 故障转移模式"

# 恢复
"开始恢复流程"
"恢复完成，切回 H2 正常模式"

# 同步失败
"同步订阅者到 Oracle 失败"
"同步失败:"

# 降级
"H2 和 Oracle 都不可用，进入降级模式"
```

### 11.3 运维操作

```java
// 查看当前状态
HealthStatus status = subscriberService.getHealthStatus();
System.out.println("模式: " + status.currentMode());
System.out.println("H2: " + status.h2Healthy());
System.out.println("Oracle: " + status.oracleHealthy());

// 手动触发同步
subscriberService.triggerIncrementalSync();

// 紧急故障转移
subscriberService.manualFailover();

// 紧急恢复
subscriberService.manualRecovery();
```

---

## 12. 风险与限制

### 12.1 已知限制

| 限制 | 说明 | 缓解措施 |
|------|------|---------|
| Oracle 免费层连接数限制 | 最多 20 个并发连接 | 连接池设置保守 (max=3) |
| 网络延迟 | Oracle 云端访问延迟 | 异步同步，不阻塞主流程 |
| 回写队列内存限制 | 队列在内存中，重启丢失 | 短期故障影响小，长期故障需人工干预 |
| ID 不一致 | H2 和 Oracle 的 ID 独立生成 | 使用 email 作为业务主键 |

### 12.2 故障场景

| 场景 | 影响 | 自动处理 |
|------|------|---------|
| H2 短暂不可用 (<90秒) | 无影响 | 健康检查容忍 |
| H2 持续不可用 | 自动切换 Oracle | 是 |
| Oracle 不可用 | 同步失败，记录 FAILED | 自动重试 |
| H2 和 Oracle 都不可用 | 系统降级 | 是，但无法处理请求 |
| 应用重启（FAILOVER期间） | 回写队列丢失 | 否，需手动触发 restoreFromOracle |

---

## 13. 已实现的改进功能

以下改进功能已全部实现：

### 13.1 持久化回写队列

**实现类**: `PersistentFailoverQueue`

- 故障转移期间的写入操作持久化到 JSON 文件
- 应用重启后自动恢复队列数据
- 定时持久化（默认 5 秒间隔）
- 支持最大队列容量配置

**配置项**:
```properties
app.sync.failover-queue.persistent=true
app.sync.failover-queue.file-path=./data/failover-queue.json
app.sync.failover-queue.max-size=10000
app.sync.failover-queue.persist-interval=5000
```

### 13.2 多 Oracle 实例负载均衡

**实现类**: `MultiOracleDataSourceConfig`

- 支持配置多个 Oracle 实例
- 三种负载均衡策略：
  - `ROUND_ROBIN`: 轮询
  - `RANDOM`: 随机
  - `FAILOVER`: 故障转移（主备模式）
- 自动实例健康监测

**配置项**:
```properties
app.sync.multi-oracle.enabled=true
app.sync.multi-oracle.strategy=FAILOVER
app.sync.multi-oracle.instances[0].name=primary
app.sync.multi-oracle.instances[0].url=jdbc:oracle:thin:@primary_low
app.sync.multi-oracle.instances[0].primary=true
app.sync.multi-oracle.instances[1].name=secondary
app.sync.multi-oracle.instances[1].url=jdbc:oracle:thin:@secondary_low
```

### 13.3 读写分离

**实现**: 集成到 `SubscriberDataService`

读策略：
- `H2_FIRST`: H2 优先，失败回退 Oracle
- `ORACLE_FIRST`: Oracle 优先，失败回退 H2
- `ROUND_ROBIN`: 轮询

写策略：
- `H2_ONLY`: 仅写 H2，异步同步 Oracle
- `DUAL_WRITE`: 双写（同步写两边）

**配置项**:
```properties
app.sync.read-write-split.enabled=true
app.sync.read-write-split.read-strategy=H2_FIRST
app.sync.read-write-split.write-strategy=H2_ONLY
```

### 13.4 配置中心化

**实现类**: `DataSyncProperties`

所有同步相关配置集中管理：
- 基础配置（同步模式、Oracle启用状态）
- 健康检查配置（间隔、阈值、超时）
- 重试配置（最大次数、间隔、退避因子）
- 回写队列配置
- 读写分离配置
- 多 Oracle 实例配置

### 13.5 监控仪表盘

**实现类**: `SyncMonitorView`

**访问路径**: `/admin/sync-monitor`

功能：
- 实时显示系统状态（H2/Oracle 健康、当前模式、队列大小）
- 数据统计卡片（记录数、待同步、失败、已同步）
- 同步进度条
- 手动操作按钮（全量同步、增量同步、恢复、故障转移）
- 最近同步审计日志表格
- 每 10 秒自动刷新

---

## 14. 更新后的风险与限制

### 14.1 已解决的限制

| 原限制 | 解决方案 |
|--------|---------|
| 回写队列内存限制，重启丢失 | `PersistentFailoverQueue` 持久化到文件 |
| 硬编码配置常量 | `DataSyncProperties` 配置中心化 |
| 缺乏可视化监控 | `SyncMonitorView` 仪表盘 |

### 14.2 仍存在的限制

| 限制 | 说明 | 缓解措施 |
|------|------|---------|
| Oracle 免费层连接数限制 | 最多 20 个并发连接 | 连接池设置保守 (max=3) |
| 网络延迟 | Oracle 云端访问延迟 | 异步同步，不阻塞主流程 |
| ID 不一致 | H2 和 Oracle 的 ID 独立生成 | 使用 email 作为业务主键 |
