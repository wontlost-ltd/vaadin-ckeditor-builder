package com.wontlost.ckeditor.views.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wontlost.ckeditor.domain.entity.Subscriber;
import com.wontlost.ckeditor.service.AdminService;
import com.wontlost.ckeditor.service.SubscriberService;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * 订阅者管理视图
 * 仅管理员可访问
 */
@Route("admin/subscribers")
@PageTitle("订阅者管理")
@RolesAllowed("ADMIN")
public class SubscribersAdminView extends VerticalLayout {

    private final SubscriberService subscriberService;
    private final AdminService adminService;
    private final Grid<Subscriber> grid;
    private final HorizontalLayout statsLayout;

    // 记录哪些邮箱已展开显示
    private final Set<Long> expandedEmails = new HashSet<>();

    public SubscribersAdminView(SubscriberService subscriberService, AdminService adminService) {
        this.subscriberService = subscriberService;
        this.adminService = adminService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // 顶部导航栏
        HorizontalLayout headerLayout = createHeaderLayout();

        // 统计卡片
        statsLayout = new HorizontalLayout();
        statsLayout.setWidthFull();
        statsLayout.setSpacing(true);
        refreshStats();

        // 操作按钮
        HorizontalLayout actionsLayout = createActionsLayout();

        // 订阅者表格
        grid = createGrid();

        add(headerLayout, statsLayout, actionsLayout, grid);
        setFlexGrow(1, grid);
    }

    private HorizontalLayout createHeaderLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        // 标题
        H2 title = new H2("订阅者管理");
        title.getStyle().set("margin", "0");

        // 右侧操作区
        HorizontalLayout rightActions = new HorizontalLayout();
        rightActions.setAlignItems(FlexComponent.Alignment.CENTER);
        rightActions.setSpacing(true);

        // 修改密码按钮
        Button changePasswordBtn = new Button("修改密码", VaadinIcon.KEY.create());
        changePasswordBtn.addClickListener(e -> showChangePasswordDialog());

        // 退出按钮
        Button logoutBtn = new Button("退出", VaadinIcon.SIGN_OUT.create());
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        logoutBtn.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.getPage().setLocation("/logout"));
        });

        rightActions.add(changePasswordBtn, logoutBtn);
        layout.add(title, rightActions);
        return layout;
    }

    private void showChangePasswordDialog() {
        Dialog dialog = new Dialog();
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("400px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        H3 dialogTitle = new H3("修改密码");
        dialogTitle.getStyle().set("margin-top", "0");

        PasswordField currentPassword = new PasswordField("当前密码");
        currentPassword.setWidthFull();
        currentPassword.setRequired(true);

        PasswordField newPassword = new PasswordField("新密码");
        newPassword.setWidthFull();
        newPassword.setRequired(true);
        newPassword.setMinLength(6);
        newPassword.setHelperText("密码至少6个字符");

        PasswordField confirmPassword = new PasswordField("确认新密码");
        confirmPassword.setWidthFull();
        confirmPassword.setRequired(true);

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button cancelBtn = new Button("取消", e -> dialog.close());

        Button saveBtn = new Button("保存", e -> {
            // 验证输入
            if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Notification.show("请填写所有字段", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            if (!newPassword.getValue().equals(confirmPassword.getValue())) {
                Notification.show("两次输入的密码不一致", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            if (newPassword.getValue().length() < 6) {
                Notification.show("密码至少需要6个字符", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // 调用服务修改密码
            boolean success = adminService.changePassword(currentPassword.getValue(), newPassword.getValue());
            if (success) {
                Notification.show("密码修改成功，请重新登录", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                // 退出重新登录
                getUI().ifPresent(ui -> ui.getPage().setLocation("/logout"));
            } else {
                Notification.show("当前密码错误", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        buttons.add(cancelBtn, saveBtn);

        content.add(dialogTitle, currentPassword, newPassword, confirmPassword, buttons);
        dialog.add(content);
        dialog.open();
    }

    private void refreshStats() {
        statsLayout.removeAll();

        long totalCount = subscriberService.getTotalCount();
        Div totalCard = createStatCard("总用户数", String.valueOf(totalCount), VaadinIcon.USERS);

        long anonymousCount = subscriberService.getAnonymousCount();
        Div anonymousCard = createStatCard("匿名用户", String.valueOf(anonymousCount), VaadinIcon.USER);

        long todayCount = subscriberService.getNewCountSince(LocalDate.now());
        Div todayCard = createStatCard("今日新增", String.valueOf(todayCount), VaadinIcon.PLUS_CIRCLE);

        long weekActiveCount = subscriberService.getActiveCountSince(LocalDate.now().minusDays(7));
        Div weekActiveCard = createStatCard("本周活跃", String.valueOf(weekActiveCount), VaadinIcon.CHART_LINE);

        long[] actionCounts = subscriberService.getActionCounts();
        Div sourceCard = createStatCard("操作统计",
            String.format("复制: %d / 下载: %d", actionCounts[0], actionCounts[1]),
            VaadinIcon.PIE_CHART);

        statsLayout.add(totalCard, anonymousCard, todayCard, weekActiveCard, sourceCard);
    }

    private Div createStatCard(String label, String value, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("stat-card");
        card.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-m)")
            .set("flex", "1")
            .set("text-align", "center");

        Div iconWrapper = new Div();
        iconWrapper.add(icon.create());
        iconWrapper.getStyle()
            .set("font-size", "24px")
            .set("color", "var(--lumo-primary-color)")
            .set("margin-bottom", "var(--lumo-space-s)");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-xl)")
            .set("font-weight", "bold")
            .set("display", "block");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)");

        card.add(iconWrapper, valueSpan, labelSpan);
        return card;
    }

    private HorizontalLayout createActionsLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        // 刷新按钮
        Button refreshBtn = new Button("刷新", VaadinIcon.REFRESH.create());
        refreshBtn.addClickListener(e -> {
            expandedEmails.clear();
            refreshStats();
            grid.getDataProvider().refreshAll();
        });

        // 导出 CSV 按钮（使用 JavaScript 下载）
        Button exportBtn = new Button("导出 CSV", VaadinIcon.DOWNLOAD.create());
        exportBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        exportBtn.addClickListener(e -> {
            String csvContent = subscriberService.exportToCsv();
            // 使用 JavaScript 触发下载
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var blob = new Blob([$0], {type: 'text/csv;charset=utf-8'});" +
                "var url = URL.createObjectURL(blob);" +
                "var a = document.createElement('a');" +
                "a.href = url;" +
                "a.download = 'subscribers.csv';" +
                "a.click();" +
                "URL.revokeObjectURL(url);",
                csvContent
            ));
            Notification.show("CSV 导出成功", 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        layout.add(refreshBtn, exportBtn);
        return layout;
    }

    private Grid<Subscriber> createGrid() {
        Grid<Subscriber> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setWidthFull();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 邮箱列 - 匿名用户显示标签，真实用户使用隐藏/显示功能
        grid.addColumn(new ComponentRenderer<>(subscriber -> {
            HorizontalLayout emailLayout = new HorizontalLayout();
            emailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            emailLayout.setSpacing(false);
            emailLayout.getStyle().set("gap", "var(--lumo-space-xs)");

            if (subscriber.isAnonymous()) {
                // 匿名用户：显示标签
                Span badge = new Span("匿名用户");
                badge.getElement().getThemeList().add("badge");
                badge.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");

                Span timeHint = new Span(subscriber.getCreatedAt() != null
                    ? subscriber.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "");
                timeHint.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-tertiary-text-color)")
                    .set("margin-left", "var(--lumo-space-xs)");

                emailLayout.add(badge, timeHint);
            } else {
                // 真实用户：隐藏/显示邮箱
                boolean isExpanded = expandedEmails.contains(subscriber.getId());
                String displayEmail = isExpanded ? subscriber.getEmail() : maskEmail(subscriber.getEmail());

                Span emailSpan = new Span(displayEmail);
                emailSpan.getStyle().set("font-family", "monospace");

                Button toggleBtn = new Button();
                toggleBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
                toggleBtn.getStyle().set("min-width", "auto");

                Icon icon = isExpanded ? VaadinIcon.EYE_SLASH.create() : VaadinIcon.EYE.create();
                icon.setSize("16px");
                toggleBtn.setIcon(icon);
                toggleBtn.setTooltipText(isExpanded ? "隐藏邮箱" : "显示完整邮箱");

                toggleBtn.addClickListener(e -> {
                    if (expandedEmails.contains(subscriber.getId())) {
                        expandedEmails.remove(subscriber.getId());
                    } else {
                        expandedEmails.add(subscriber.getId());
                    }
                    grid.getDataProvider().refreshItem(subscriber);
                });

                emailLayout.add(emailSpan, toggleBtn);
            }
            return emailLayout;
        }))
            .setHeader("邮箱")
            .setSortable(true)
            .setFlexGrow(2);

        grid.addColumn(s -> s.getSource().getDisplayName())
            .setHeader("来源")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(s -> s.getCreatedAt().format(formatter))
            .setHeader("订阅时间")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(s -> s.getLastActiveAt() != null ? s.getLastActiveAt().format(formatter) : "-")
            .setHeader("最后活跃")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(Subscriber::getCopyCount)
            .setHeader("复制次数")
            .setSortable(true)
            .setFlexGrow(0);

        grid.addColumn(Subscriber::getDownloadCount)
            .setHeader("下载次数")
            .setSortable(true)
            .setFlexGrow(0);

        // 使用分页数据提供者
        grid.setItems(DataProvider.fromCallbacks(
            query -> {
                int offset = query.getOffset();
                int limit = query.getLimit();
                return subscriberService.getSubscribers(
                    PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).stream();
            },
            query -> (int) subscriberService.getTotalCount()
        ));

        return grid;
    }

    /**
     * 隐藏邮箱中间1/3的字符
     * 例如: test@example.com -> te***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 4) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        int localLength = localPart.length();
        if (localLength <= 2) {
            return localPart.charAt(0) + "***" + domainPart;
        }

        // 隐藏中间1/3的字符
        int maskLength = Math.max(1, localLength / 3);
        int startMask = (localLength - maskLength) / 2;
        int endMask = startMask + maskLength;

        StringBuilder masked = new StringBuilder();
        masked.append(localPart.substring(0, startMask));
        masked.append("*".repeat(maskLength));
        masked.append(localPart.substring(endMask));
        masked.append(domainPart);

        return masked.toString();
    }
}
