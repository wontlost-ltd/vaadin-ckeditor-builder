package com.wontlost.ckeditor.views.wizard;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;
import com.wontlost.ckeditor.views.MainView;
import com.wontlost.ckeditor.views.wizard.steps.AdvancedConfigStep;
import com.wontlost.ckeditor.views.wizard.steps.EditorTypeStep;
import com.wontlost.ckeditor.views.wizard.steps.GettingStartedStep;
import com.wontlost.ckeditor.views.wizard.steps.PluginsStep;
import com.wontlost.ckeditor.views.wizard.steps.PreviewExportStep;
import com.wontlost.ckeditor.views.wizard.steps.StyleLanguageStep;
import com.wontlost.ckeditor.views.wizard.steps.ToolbarStep;
import com.wontlost.ckeditor.service.SubscriberService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Wizard 模式的 CKEditor Builder 视图
 * 分步骤引导用户完成编辑器配置
 */
@Route(value = "", layout = MainView.class)
@PageTitle("CKEditor Wizard Builder")
@AnonymousAllowed
@CssImport("./styles/views/wizard.css")
@JsModule("./custom-plugins.ts")
public class WizardBuilderView extends VerticalLayout {

    private final BuilderState state;
    private final List<WizardStep> steps;
    private int currentStepIndex = 0;

    // UI 组件
    private HorizontalLayout stepIndicator;
    private HorizontalLayout stepsSection;
    private ProgressBar progressBar;
    private Div stepContainer;
    private Button prevButton;
    private Button nextButton;
    private Button skipButton;

    // 服务
    private final SubscriberService subscriberService;

    @Autowired
    public WizardBuilderView(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
        this.state = new BuilderState();
        this.steps = createSteps();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("wizard-builder-view");

        add(createStepIndicator());
        add(createMainContent());
        add(createFooter());
    }

    private List<WizardStep> createSteps() {
        List<WizardStep> stepList = new ArrayList<>();

        // Step 1: 入门配置 - 设置预设选择回调实现智能跳转
        GettingStartedStep gettingStartedStep = new GettingStartedStep();
        gettingStartedStep.setOnPresetSelectedCallback(preset -> {
            // 预设模式：自动初始化配置并跳转到 Step 6（StyleLanguage）
            state.initFromPreset(preset);
            // 延迟跳转，确保 UI 状态已更新
            getUI().ifPresent(ui -> ui.access(() -> {
                // 跳转到 Step 6（索引 5）- StyleLanguageStep
                navigateToStep(5);
            }));
        });
        // 自定义模式：跳转到 Step 2（EditorType）
        gettingStartedStep.setOnCustomModeSelectedCallback(() -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                // 跳转到 Step 2（索引 1）- EditorTypeStep
                navigateToStep(1);
            }));
        });
        stepList.add(gettingStartedStep);

        stepList.add(new EditorTypeStep());       // Step 2: 编辑器类型
        stepList.add(new PluginsStep());          // Step 3: 插件选择
        stepList.add(new AdvancedConfigStep());   // Step 4: 高级配置
        stepList.add(new ToolbarStep());          // Step 5: 工具栏配置
        stepList.add(new StyleLanguageStep());    // Step 6: 样式与语言

        // Step 7: 预览与导出（注入订阅服务）
        PreviewExportStep previewExportStep = new PreviewExportStep();
        previewExportStep.setSubscriberService(subscriberService);
        stepList.add(previewExportStep);

        return stepList;
    }

    /**
     * 创建步骤指示器（包含进度指示）
     */
    private Component createStepIndicator() {
        // 创建步骤指示器容器
        stepIndicator = new HorizontalLayout();
        stepIndicator.setWidthFull();
        stepIndicator.setAlignItems(FlexComponent.Alignment.CENTER);
        stepIndicator.addClassName("wizard-step-indicator");

        // 步骤圆圈区（居中）
        stepsSection = new HorizontalLayout();
        stepsSection.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        stepsSection.addClassName("wizard-steps-section");

        for (int i = 0; i < steps.size(); i++) {
            WizardStep step = steps.get(i);
            Div stepItem = createStepIndicatorItem(i, step);
            stepsSection.add(stepItem);

            // 添加连接线（除了最后一个）
            if (i < steps.size() - 1) {
                Div connector = new Div();
                connector.addClassName("step-connector");
                stepsSection.add(connector);
            }
        }

        // 右侧进度指示区
        Div progressSection = new Div();
        progressSection.addClassName("wizard-progress-section");

        Span progressText = new Span();
        progressText.addClassName("wizard-progress-text");
        progressText.setId("progress-text");

        progressBar = new ProgressBar();
        progressBar.setMin(0);
        progressBar.setMax(steps.size());
        progressBar.addClassName("wizard-progress-bar");

        progressSection.add(progressText, progressBar);

        stepIndicator.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        stepIndicator.add(stepsSection, progressSection);

        return stepIndicator;
    }

    private Div createStepIndicatorItem(int index, WizardStep step) {
        Div item = new Div();
        item.addClassName("step-indicator-item");
        item.getElement().setAttribute("data-step", String.valueOf(index));

        // 键盘导航支持
        item.getElement().setAttribute("tabindex", "0");
        item.getElement().setAttribute("role", "button");
        item.getElement().setAttribute("aria-label",
            String.format("%s %d: %s", I18nUtil.get("wizard.step", "", "").replace(" / ", "").trim(), index + 1, step.getTitle()));

        // 步骤圆圈
        Div circle = new Div();
        circle.addClassName("step-circle");

        Icon icon = step.getIcon().create();
        icon.setSize("16px");
        circle.add(icon);

        // 步骤标签
        Span label = new Span(step.getTitle());
        label.addClassName("step-label");

        item.add(circle, label);

        // 点击切换步骤
        item.addClickListener(e -> {
            if (index < currentStepIndex) {
                // 可以回到之前的步骤
                navigateToStep(index);
            }
        });

        // 键盘事件支持 (Enter/Space)
        item.getElement().addEventListener("keydown", e -> {
            if (index < currentStepIndex) {
                navigateToStep(index);
            }
        }).setFilter("event.key === 'Enter' || event.key === ' '");

        return item;
    }

    /**
     * 创建主内容区
     */
    private Component createMainContent() {
        stepContainer = new Div();
        stepContainer.setSizeFull();
        stepContainer.addClassName("wizard-step-container");

        VerticalLayout wrapper = new VerticalLayout(stepContainer);
        wrapper.setSizeFull();
        wrapper.setPadding(true);
        wrapper.addClassName("wizard-content-wrapper");

        return wrapper;
    }

    /**
     * 创建底部导航栏
     */
    private Component createFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setPadding(true);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        footer.addClassName("wizard-footer");

        // 左侧按钮
        HorizontalLayout leftButtons = new HorizontalLayout();

        prevButton = new Button(I18nUtil.get("wizard.prev"), VaadinIcon.ARROW_LEFT.create());
        prevButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        prevButton.addClassName("wizard-nav-button");
        prevButton.addClickListener(e -> previousStep());

        leftButtons.add(prevButton);

        // 右侧按钮
        HorizontalLayout rightButtons = new HorizontalLayout();

        skipButton = new Button(I18nUtil.get("wizard.skip"));
        skipButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        skipButton.addClassName("wizard-skip-button");
        skipButton.addClickListener(e -> skipStep());

        nextButton = new Button(I18nUtil.get("wizard.next"), VaadinIcon.ARROW_RIGHT.create());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nextButton.setIconAfterText(true);
        nextButton.addClassName("wizard-nav-button");
        nextButton.addClickListener(e -> nextStep());

        rightButtons.add(skipButton, nextButton);

        footer.add(leftButtons, rightButtons);

        return footer;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        showStep(0);
    }

    /**
     * 显示指定步骤
     */
    private void showStep(int index) {
        if (index < 0 || index >= steps.size()) return;

        // 退出当前步骤
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            steps.get(currentStepIndex).onExit(state);
        }

        currentStepIndex = index;
        WizardStep step = steps.get(index);

        // 先更新内容（确保组件已附加到 DOM）
        stepContainer.removeAll();
        stepContainer.add(step.getContent());

        // 再进入步骤（VaadinCKEditor 等组件需要已附加才能初始化）
        step.onEnter(state);

        // 更新进度
        updateProgress();

        // 更新步骤指示器
        updateStepIndicator();

        // 更新按钮状态
        updateNavigationButtons();
    }

    private void navigateToStep(int index) {
        showStep(index);
    }

    private void nextStep() {
        // 清除之前的错误提示
        clearValidationError();

        WizardStep currentStep = steps.get(currentStepIndex);
        ValidationResult result = currentStep.validate(state);

        if (!result.isValid()) {
            // 显示内联错误提示
            showValidationError(result.getFirstError());
            // 同时显示 toast 通知
            Notification.show(result.getFirstError(), 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        if (result.hasWarnings()) {
            for (String warning : result.getWarnings()) {
                Notification.show(warning, 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        }

        if (currentStepIndex < steps.size() - 1) {
            showStep(currentStepIndex + 1);
        } else {
            // 最后一步点击"完成"
            showThankYouOverlay();
        }
    }

    /**
     * 显示感谢信息浮层（居中渐入，延时自动关闭）
     */
    private void showThankYouOverlay() {
        Div overlay = new Div();
        overlay.addClassName("thank-you-overlay");
        overlay.getStyle()
            .set("position", "fixed")
            .set("inset", "0")
            .set("z-index", "9999")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("background", "rgba(0, 0, 0, 0)")
            .set("transition", "background 0.4s ease")
            .set("pointer-events", "auto");

        Div card = new Div();
        card.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "16px")
            .set("padding", "40px 48px")
            .set("text-align", "center")
            .set("box-shadow", "0 20px 60px rgba(0, 0, 0, 0.2)")
            .set("max-width", "420px")
            .set("transform", "scale(0.8) translateY(20px)")
            .set("opacity", "0")
            .set("transition", "all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1)");

        // 图标
        Div iconWrap = new Div();
        iconWrap.getStyle()
            .set("width", "64px")
            .set("height", "64px")
            .set("margin", "0 auto 16px")
            .set("border-radius", "50%")
            .set("background", "linear-gradient(135deg, #6366f1, #8b5cf6)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center");
        Icon checkIcon = VaadinIcon.CHECK.create();
        checkIcon.setSize("32px");
        checkIcon.getStyle().set("color", "white");
        iconWrap.add(checkIcon);

        // 标题
        Div title = new Div();
        title.setText(I18nUtil.get("wizard.thankYou.title"));
        title.getStyle()
            .set("font-size", "22px")
            .set("font-weight", "700")
            .set("color", "var(--lumo-header-text-color)")
            .set("margin-bottom", "8px");

        // 副标题
        Div subtitle = new Div();
        subtitle.setText(I18nUtil.get("wizard.thankYou.message"));
        subtitle.getStyle()
            .set("font-size", "14px")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("line-height", "1.5");

        // 进度条（倒计时指示）
        Div progressTrack = new Div();
        progressTrack.getStyle()
            .set("margin-top", "24px")
            .set("height", "3px")
            .set("border-radius", "2px")
            .set("background", "var(--lumo-contrast-10pct)")
            .set("overflow", "hidden");

        Div progressBar = new Div();
        progressBar.getStyle()
            .set("width", "100%")
            .set("height", "100%")
            .set("border-radius", "2px")
            .set("background", "linear-gradient(90deg, #6366f1, #8b5cf6)")
            .set("transition", "width 3.5s linear");
        progressTrack.add(progressBar);

        card.add(iconWrap, title, subtitle, progressTrack);
        overlay.add(card);

        getElement().appendChild(overlay.getElement());

        // 渐入动画 + 倒计时进度条 + 延时移除
        overlay.getElement().executeJs(
            "const overlay = this;" +
            "const card = this.firstElementChild;" +
            "const bar = this.querySelector('div > div:last-child > div');" +
            // 触发渐入
            "requestAnimationFrame(() => {" +
            "  overlay.style.background = 'rgba(0,0,0,0.4)';" +
            "  overlay.style.backdropFilter = 'blur(4px)';" +
            "  card.style.transform = 'scale(1) translateY(0)';" +
            "  card.style.opacity = '1';" +
            // 启动倒计时进度条
            "  setTimeout(() => { bar.style.width = '0%'; }, 50);" +
            "});" +
            // 点击蒙版提前关闭
            "overlay.addEventListener('click', function handler(e) {" +
            "  if (e.target === overlay) {" +
            "    card.style.transform = 'scale(0.9)';" +
            "    card.style.opacity = '0';" +
            "    overlay.style.background = 'rgba(0,0,0,0)';" +
            "    setTimeout(() => overlay.remove(), 300);" +
            "    overlay.removeEventListener('click', handler);" +
            "  }" +
            "});" +
            // 4 秒后自动关闭
            "setTimeout(() => {" +
            "  if (overlay.parentNode) {" +
            "    card.style.transform = 'scale(0.9)';" +
            "    card.style.opacity = '0';" +
            "    overlay.style.background = 'rgba(0,0,0,0)';" +
            "    setTimeout(() => overlay.remove(), 300);" +
            "  }" +
            "}, 4000);"
        );
    }

    /**
     * 显示内联验证错误提示
     */
    private void showValidationError(String errorMessage) {
        // 创建错误提示组件
        Div errorBanner = new Div();
        errorBanner.addClassName("validation-error-banner");
        errorBanner.setId("validation-error");

        Icon errorIcon = VaadinIcon.EXCLAMATION_CIRCLE.create();
        errorIcon.addClassName("validation-error-icon");

        Span errorText = new Span(errorMessage);
        errorText.addClassName("validation-error-text");

        Button closeButton = new Button(VaadinIcon.CLOSE_SMALL.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        closeButton.addClassName("validation-error-close");
        closeButton.addClickListener(e -> clearValidationError());

        errorBanner.add(errorIcon, errorText, closeButton);

        // 在步骤内容顶部插入错误提示
        stepContainer.getElement().insertChild(0, errorBanner.getElement());

        // 滚动到错误提示区域
        errorBanner.getElement().executeJs("this.scrollIntoView({behavior: 'smooth', block: 'start'})");
    }

    /**
     * 清除验证错误提示
     */
    private void clearValidationError() {
        stepContainer.getChildren()
            .filter(c -> c.getId().orElse("").equals("validation-error"))
            .findFirst()
            .ifPresent(stepContainer::remove);
    }

    private void previousStep() {
        if (currentStepIndex > 0) {
            showStep(currentStepIndex - 1);
        }
    }

    private void skipStep() {
        WizardStep currentStep = steps.get(currentStepIndex);
        if (currentStep.isSkippable() && currentStepIndex < steps.size() - 1) {
            showStep(currentStepIndex + 1);
        }
    }

    private void updateProgress() {
        progressBar.setValue(currentStepIndex + 1);

        // 更新进度文本
        getElement().executeJs(
            "document.getElementById('progress-text').innerText = $0",
            I18nUtil.get("wizard.step", currentStepIndex + 1, steps.size())
        );
    }

    private void updateStepIndicator() {
        for (int i = 0; i < stepsSection.getComponentCount(); i++) {
            Component component = stepsSection.getComponentAt(i);
            if (component instanceof Div item) {
                String dataStep = item.getElement().getAttribute("data-step");
                if (dataStep != null) {
                    int stepIndex = Integer.parseInt(dataStep);
                    var classList = item.getElement().getClassList();
                    classList.remove("completed");
                    classList.remove("current");
                    classList.remove("upcoming");

                    if (stepIndex < currentStepIndex) {
                        classList.add("completed");
                        // 已完成步骤可通过键盘导航
                        item.getElement().setAttribute("tabindex", "0");
                    } else if (stepIndex == currentStepIndex) {
                        classList.add("current");
                        // 当前步骤不可点击，禁用 Tab
                        item.getElement().setAttribute("tabindex", "-1");
                    } else {
                        classList.add("upcoming");
                        // 未来步骤禁用 Tab 导航
                        item.getElement().setAttribute("tabindex", "-1");
                    }
                }
            }
        }
    }

    private void updateNavigationButtons() {
        // 上一步按钮
        prevButton.setEnabled(currentStepIndex > 0);

        // 下一步按钮
        boolean isLastStep = currentStepIndex == steps.size() - 1;
        nextButton.setText(isLastStep ? I18nUtil.get("wizard.finish") : I18nUtil.get("wizard.next"));
        nextButton.setIcon(isLastStep ? VaadinIcon.CHECK.create() : VaadinIcon.ARROW_RIGHT.create());

        // 跳过按钮
        WizardStep currentStep = steps.get(currentStepIndex);
        skipButton.setVisible(currentStep.isSkippable() && !isLastStep);
    }

    /**
     * 获取当前构建状态
     */
    public BuilderState getState() {
        return state;
    }
}
