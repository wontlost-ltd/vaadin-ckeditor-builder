package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import com.wontlost.ckeditor.config.DataSourceMode;
import com.wontlost.ckeditor.domain.entity.SyncAuditLog;
import com.wontlost.ckeditor.service.DataSourceHealthService;
import com.wontlost.ckeditor.service.DataSyncService;
import com.wontlost.ckeditor.service.SubscriberService;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 同步监控仪表盘视图
 * 提供数据同步状态的实时可视化监控
 */
@Route("admin/sync-monitor")
@PageTitle("同步监控 | CKEditor Builder")
@RolesAllowed("ADMIN")
public class SyncMonitorView extends VerticalLayout {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SubscriberService subscriberService;
    private final DataSyncService syncService;
    private final DataSourceHealthService healthService;

    // UI 组件
    private Div statusCard;
    private Span modeLabel;
    private Span h2StatusLabel;
    private Span oracleStatusLabel;
    private Span queueSizeLabel;
    private ProgressBar syncProgressBar;
    private Span syncProgressLabel;

    // 统计卡片
    private Span h2CountLabel;
    private Span oracleCountLabel;
    private Span pendingCountLabel;
    private Span failedCountLabel;
    private Span syncedCountLabel;

    // 审计日志表格
    private Grid<SyncAuditLog> auditLogGrid;

    // 自动刷新
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;

    public SyncMonitorView(
            SubscriberService subscriberService,
            DataSyncService syncService,
            DataSourceHealthService healthService) {
        this.subscriberService = subscriberService;
        this.syncService = syncService;
        this.healthService = healthService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(createHeader());
        add(createStatusSection());
        add(createStatsSection());
        add(createActionsSection());
        add(createAuditLogSection());

        refreshData();
    }

    private HorizontalLayout createHeader() {
        H2 title = new H2("同步监控仪表盘");
        title.getStyle().set("margin", "0");

        Button refreshButton = new Button("刷新", new Icon(VaadinIcon.REFRESH));
        refreshButton.addClickListener(e -> refreshData());

        HorizontalLayout header = new HorizontalLayout(title, refreshButton);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        return header;
    }

    private Div createStatusSection() {
        statusCard = new Div();
        statusCard.addClassName("status-card");
        statusCard.getStyle()
            .set("padding", "20px")
            .set("border-radius", "8px")
            .set("background", "#f5f5f5")
            .set("margin-bottom", "20px");

        H3 sectionTitle = new H3("系统状态");
        sectionTitle.getStyle().set("margin-top", "0");

        // 状态指标
        modeLabel = createStatusLabel("模式", "NORMAL");
        h2StatusLabel = createStatusLabel("H2", "正常");
        oracleStatusLabel = createStatusLabel("Oracle", "未启用");
        queueSizeLabel = createStatusLabel("回写队列", "0");

        HorizontalLayout statusRow = new HorizontalLayout(modeLabel, h2StatusLabel, oracleStatusLabel, queueSizeLabel);
        statusRow.setSpacing(true);
        statusRow.getStyle().set("gap", "40px");

        // 同步进度
        syncProgressBar = new ProgressBar();
        syncProgressBar.setWidth("100%");
        syncProgressLabel = new Span("同步状态: 正常");

        statusCard.add(sectionTitle, statusRow, syncProgressLabel, syncProgressBar);
        return statusCard;
    }

    private Span createStatusLabel(String label, String value) {
        Span span = new Span(label + ": " + value);
        span.getStyle()
            .set("font-weight", "500")
            .set("padding", "8px 16px")
            .set("background", "#fff")
            .set("border-radius", "4px");
        return span;
    }

    private HorizontalLayout createStatsSection() {
        h2CountLabel = createStatCard("H2 记录数", "0", "#4CAF50");
        oracleCountLabel = createStatCard("Oracle 记录数", "0", "#2196F3");
        pendingCountLabel = createStatCard("待同步", "0", "#FF9800");
        failedCountLabel = createStatCard("同步失败", "0", "#f44336");
        syncedCountLabel = createStatCard("已同步", "0", "#8BC34A");

        HorizontalLayout statsRow = new HorizontalLayout(
            h2CountLabel.getParent().get(),
            oracleCountLabel.getParent().get(),
            pendingCountLabel.getParent().get(),
            failedCountLabel.getParent().get(),
            syncedCountLabel.getParent().get()
        );
        statsRow.setWidthFull();
        statsRow.setSpacing(true);
        return statsRow;
    }

    private Span createStatCard(String label, String value, String color) {
        Div card = new Div();
        card.getStyle()
            .set("padding", "16px")
            .set("border-radius", "8px")
            .set("background", "#fff")
            .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
            .set("text-align", "center")
            .set("flex", "1");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
            .set("display", "block")
            .set("color", "#666")
            .set("font-size", "14px")
            .set("margin-bottom", "8px");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
            .set("display", "block")
            .set("font-size", "24px")
            .set("font-weight", "bold")
            .set("color", color);

        card.add(labelSpan, valueSpan);
        return valueSpan;
    }

    private HorizontalLayout createActionsSection() {
        Button fullSyncButton = new Button("全量同步", new Icon(VaadinIcon.DATABASE));
        fullSyncButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        fullSyncButton.addClickListener(e -> triggerFullSync());

        Button incrementalSyncButton = new Button("增量同步", new Icon(VaadinIcon.ARROW_CIRCLE_UP));
        incrementalSyncButton.addClickListener(e -> triggerIncrementalSync());

        Button restoreButton = new Button("从 Oracle 恢复", new Icon(VaadinIcon.DOWNLOAD));
        restoreButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        restoreButton.addClickListener(e -> triggerRestore());

        Button failoverButton = new Button("手动故障转移", new Icon(VaadinIcon.WARNING));
        failoverButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        failoverButton.addClickListener(e -> triggerFailover());

        Button recoveryButton = new Button("手动恢复", new Icon(VaadinIcon.CHECK));
        recoveryButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        recoveryButton.addClickListener(e -> triggerRecovery());

        HorizontalLayout actions = new HorizontalLayout(
            fullSyncButton, incrementalSyncButton, restoreButton, failoverButton, recoveryButton
        );
        actions.setSpacing(true);
        actions.getStyle().set("margin", "20px 0");
        return actions;
    }

    private VerticalLayout createAuditLogSection() {
        H3 title = new H3("最近同步日志");

        auditLogGrid = new Grid<>(SyncAuditLog.class, false);
        auditLogGrid.addColumn(log -> log.getSyncTime().format(TIME_FORMATTER))
            .setHeader("时间").setWidth("180px");
        auditLogGrid.addColumn(log -> log.getSyncType().name())
            .setHeader("类型").setWidth("150px");
        auditLogGrid.addColumn(SyncAuditLog::getTriggeredBy)
            .setHeader("触发者").setWidth("100px");
        auditLogGrid.addColumn(log -> log.getStatus().name())
            .setHeader("状态").setWidth("100px");
        auditLogGrid.addColumn(SyncAuditLog::getTotalRecords)
            .setHeader("总记录").setWidth("80px");
        auditLogGrid.addColumn(SyncAuditLog::getSuccessCount)
            .setHeader("成功").setWidth("80px");
        auditLogGrid.addColumn(SyncAuditLog::getFailCount)
            .setHeader("失败").setWidth("80px");
        auditLogGrid.addColumn(log -> log.getDurationMs() + " ms")
            .setHeader("耗时").setWidth("100px");
        auditLogGrid.addColumn(SyncAuditLog::getErrorMessage)
            .setHeader("错误信息").setAutoWidth(true);

        auditLogGrid.setHeight("300px");

        VerticalLayout section = new VerticalLayout(title, auditLogGrid);
        section.setPadding(false);
        section.setSpacing(false);
        return section;
    }

    private void refreshData() {
        try {
            // 更新健康状态
            DataSourceHealthService.HealthStatus health = healthService.getHealthStatus();
            updateStatusCard(health);

            // 更新同步状态
            DataSyncService.SyncStatus syncStatus = syncService.getSyncStatus();
            updateSyncStats(syncStatus);

            // 更新审计日志
            auditLogGrid.setItems(subscriberService.getRecentSyncLogs());

        } catch (Exception e) {
            Notification.show("刷新数据失败: " + e.getMessage(), 3000, Notification.Position.TOP_END)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateStatusCard(DataSourceHealthService.HealthStatus health) {
        DataSourceMode mode = health.currentMode();

        // 更新模式标签
        modeLabel.setText("模式: " + mode.name());
        modeLabel.getStyle().set("background", getModeColor(mode));

        // 更新 H2 状态
        h2StatusLabel.setText("H2: " + (health.h2Healthy() ? "正常" : "异常"));
        h2StatusLabel.getStyle().set("background", health.h2Healthy() ? "#c8e6c9" : "#ffcdd2");

        // 更新 Oracle 状态
        oracleStatusLabel.setText("Oracle: " + (health.oracleHealthy() ? "正常" : "未启用/异常"));
        oracleStatusLabel.getStyle().set("background", health.oracleHealthy() ? "#c8e6c9" : "#fff9c4");

        // 更新队列大小
        queueSizeLabel.setText("回写队列: " + health.pendingQueueSize());

        // 更新状态卡片背景色
        String bgColor = switch (mode) {
            case NORMAL -> "#e8f5e9";
            case FAILOVER -> "#fff3e0";
            case RECOVERY -> "#e3f2fd";
            case DEGRADED -> "#ffebee";
        };
        statusCard.getStyle().set("background", bgColor);
    }

    private String getModeColor(DataSourceMode mode) {
        return switch (mode) {
            case NORMAL -> "#c8e6c9";
            case FAILOVER -> "#ffe0b2";
            case RECOVERY -> "#bbdefb";
            case DEGRADED -> "#ffcdd2";
        };
    }

    private void updateSyncStats(DataSyncService.SyncStatus status) {
        h2CountLabel.setText(String.valueOf(status.h2Count()));
        oracleCountLabel.setText(status.oracleCount() >= 0 ? String.valueOf(status.oracleCount()) : "N/A");
        pendingCountLabel.setText(String.valueOf(status.pendingCount()));
        failedCountLabel.setText(String.valueOf(status.failedCount()));
        syncedCountLabel.setText(String.valueOf(status.syncedCount()));

        // 更新进度条
        long total = status.h2Count();
        long synced = status.syncedCount();
        double progress = total > 0 ? (double) synced / total : 1.0;
        syncProgressBar.setValue(progress);

        if (status.isInSync()) {
            syncProgressLabel.setText("同步状态: 已同步");
            syncProgressLabel.getStyle().set("color", "#4CAF50");
        } else {
            syncProgressLabel.setText(String.format("同步状态: 待同步 %d, 失败 %d", status.pendingCount(), status.failedCount()));
            syncProgressLabel.getStyle().set("color", status.failedCount() > 0 ? "#f44336" : "#FF9800");
        }
    }

    private void triggerFullSync() {
        DataSyncService.SyncResult result = subscriberService.triggerFullSync();
        showSyncResult("全量同步", result);
        refreshData();
    }

    private void triggerIncrementalSync() {
        DataSyncService.SyncResult result = subscriberService.triggerIncrementalSync();
        showSyncResult("增量同步", result);
        refreshData();
    }

    private void triggerRestore() {
        DataSyncService.SyncResult result = subscriberService.restoreFromOracle();
        showSyncResult("Oracle 恢复", result);
        refreshData();
    }

    private void triggerFailover() {
        boolean success = subscriberService.manualFailover();
        if (success) {
            Notification.show("已切换到故障转移模式", 3000, Notification.Position.TOP_END)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        } else {
            Notification.show("故障转移失败: Oracle 不可用", 3000, Notification.Position.TOP_END)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
        refreshData();
    }

    private void triggerRecovery() {
        boolean success = subscriberService.manualRecovery();
        if (success) {
            Notification.show("已恢复到正常模式", 3000, Notification.Position.TOP_END)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Notification.show("恢复失败: H2 不可用或当前不在故障转移模式", 3000, Notification.Position.TOP_END)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
        refreshData();
    }

    private void showSyncResult(String operation, DataSyncService.SyncResult result) {
        String message = String.format("%s完成: 成功 %d, 失败 %d, 跳过 %d",
            operation, result.successCount(), result.failCount(), result.skippedCount());

        NotificationVariant variant = result.failCount() > 0
            ? NotificationVariant.LUMO_WARNING
            : NotificationVariant.LUMO_SUCCESS;

        Notification.show(message, 3000, Notification.Position.TOP_END)
            .addThemeVariants(variant);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // 防止重复 attach 时泄漏旧 scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            if (refreshTask != null) refreshTask.cancel(true);
            scheduler.shutdownNow();
        }

        // 启动自动刷新（每 10 秒），使用守护线程防止阻塞 JVM 关闭
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sync-monitor-refresh");
            t.setDaemon(true);
            return t;
        });
        UI ui = attachEvent.getUI();
        refreshTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                ui.access(this::refreshData);
            } catch (Exception e) {
                // UI 已断开或其他异常，停止定时任务
                if (refreshTask != null) refreshTask.cancel(false);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);

        // 停止自动刷新并等待终止
        if (refreshTask != null) {
            refreshTask.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
