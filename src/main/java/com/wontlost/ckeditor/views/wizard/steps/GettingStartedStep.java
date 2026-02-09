package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.wontlost.ckeditor.CKEditorPreset;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Step 1: 入门配置
 * 用户选择预设模式或自定义模式
 * 预设模式使用弹出式卡片选择器
 */
public class GettingStartedStep implements WizardStep {

    private BuilderState state;
    private VerticalLayout content;
    private Div presetCard;
    private Div customCard;
    private Div presetOverlay;
    private final Map<String, Div> presetCards = new HashMap<>();
    private final Map<String, CKEditorPreset> cardPresetMap = new HashMap<>();
    // 暂不可用的预设卡片（Coming Soon）
    private static final Set<String> DISABLED_CARD_IDS = Set.of(
        "collaborative", "ai-document", "email", "notion"
    );
    private String selectedCardId;
    private CKEditorPreset selectedPreset;

    // 预设选择完成后的回调，用于智能跳转
    private Consumer<CKEditorPreset> onPresetSelectedCallback;

    // 自定义模式选择后的回调，用于跳转到编辑器类型步骤
    private Runnable onCustomModeSelectedCallback;

    @Override
    public String getId() { return I18nUtil.get("step1.id"); }

    @Override
    public String getTitle() { return I18nUtil.get("step1.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step1.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.ROCKET; }

    @Override
    public Component getContent() {
        if (content == null) {
            content = createContent();
        }
        return content;
    }

    private VerticalLayout createContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        layout.addClassName("getting-started-step");

        // 欢迎信息
        Div welcomeSection = new Div();
        welcomeSection.addClassName("welcome-section");

        H3 welcomeTitle = new H3(I18nUtil.get("step1.welcome"));
        welcomeTitle.addClassName("welcome-title");

        Paragraph welcomeText = new Paragraph(I18nUtil.get("step1.welcomeDesc"));
        welcomeText.addClassName("welcome-text");

        welcomeSection.add(welcomeTitle, welcomeText);

        // 模式选择卡片
        HorizontalLayout modeCards = new HorizontalLayout();
        modeCards.setWidthFull();
        modeCards.setMaxWidth("900px");
        modeCards.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        modeCards.addClassName("mode-cards");

        presetCard = createModeCard(
            VaadinIcon.FLASH,
            I18nUtil.get("step1.preset.title"),
            I18nUtil.get("step1.preset.description"),
            new String[]{
                I18nUtil.get("step1.preset.standard"),
                I18nUtil.get("step1.preset.minimal"),
                I18nUtil.get("step1.preset.full")
            },
            BuilderState.Mode.PRESET
        );

        customCard = createModeCard(
            VaadinIcon.COG,
            I18nUtil.get("step1.custom.title"),
            I18nUtil.get("step1.custom.description"),
            new String[]{
                I18nUtil.get("step1.custom.editorType"),
                I18nUtil.get("step1.custom.plugins"),
                I18nUtil.get("step1.custom.toolbar")
            },
            BuilderState.Mode.CUSTOM
        );

        modeCards.add(presetCard, customCard);

        // 预设选择蒙版（初始隐藏）
        presetOverlay = createPresetOverlay();

        layout.add(welcomeSection, modeCards, presetOverlay);

        return layout;
    }

    private Div createPresetOverlay() {
        Div overlay = new Div();
        overlay.addClassName("preset-overlay");
        overlay.setVisible(false);

        // 蒙版内容容器
        Div overlayContent = new Div();
        overlayContent.addClassName("preset-overlay-content");

        // 标题栏
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.addClassName("preset-overlay-header");

        H4 title = new H4(I18nUtil.get("step1.preset.selectTitle"));
        title.addClassName("preset-overlay-title");

        Button closeBtn = new Button(VaadinIcon.CLOSE.create());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        closeBtn.addClassName("preset-overlay-close");
        closeBtn.addClickListener(e -> hidePresetOverlay());

        header.add(title, closeBtn);

        // 预设卡片网格 - 使用Div让CSS控制grid布局
        Div presetGrid = new Div();
        presetGrid.addClassName("preset-grid");

        // 添加各预设卡片
        presetGrid.add(createPresetCard(CKEditorPreset.BASIC,
            "Basic",
            I18nUtil.get("preset.basic.desc"),
            new String[]{"bold", "italic", "link", "list"},
            "~300KB"));

        presetGrid.add(createPresetCard(CKEditorPreset.STANDARD,
            "Standard",
            I18nUtil.get("preset.standard.desc"),
            new String[]{"bold", "italic", "heading", "image", "table", "list"},
            "~600KB"));

        presetGrid.add(createPresetCard(CKEditorPreset.FULL,
            "Full",
            I18nUtil.get("preset.full.desc"),
            new String[]{"bold", "italic", "heading", "image", "table", "media", "code"},
            "~1MB"));

        presetGrid.add(createPresetCard(CKEditorPreset.DOCUMENT,
            "Document",
            I18nUtil.get("preset.document.desc"),
            new String[]{"heading", "toc", "pageBreak", "footnote"},
            "~800KB"));

        presetGrid.add(createPresetCard(CKEditorPreset.COLLABORATIVE,
            "Collaborative",
            I18nUtil.get("preset.collaborative.desc"),
            new String[]{"comments", "trackChanges", "realtime"},
            "Premium"));

        presetGrid.add(createPresetCard(CKEditorPreset.EMPTY,
            "Empty",
            I18nUtil.get("preset.empty.desc"),
            new String[]{"custom"},
            "~100KB"));

        // 新增：AI-powered Document Editor (基于 FULL)
        presetGrid.add(createPresetCardWithBadge(CKEditorPreset.FULL,
            "AI Document",
            I18nUtil.get("preset.ai.desc"),
            new String[]{"✨", "heading", "image", "table", "ai"},
            "Premium",
            "ai-document",
            "AI"));

        // 新增：Email Editor (基于 STANDARD)
        presetGrid.add(createPresetCardWithBadge(CKEditorPreset.STANDARD,
            "Email",
            I18nUtil.get("preset.email.desc"),
            new String[]{"bold", "italic", "link", "list", "image"},
            "~500KB",
            "email",
            null));

        // 新增：Notion-like Editor (基于 FULL)
        presetGrid.add(createPresetCardWithBadge(CKEditorPreset.FULL,
            "Notion-like",
            I18nUtil.get("preset.notion.desc"),
            new String[]{"/", "heading", "list", "toggle", "code"},
            "~900KB",
            "notion",
            "New"));

        // 底部操作栏
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.addClassName("preset-overlay-footer");

        Button cancelBtn = new Button(I18nUtil.get("button.cancel"));
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelBtn.addClickListener(e -> hidePresetOverlay());

        Button continueBtn = new Button(I18nUtil.get("button.continue"), VaadinIcon.ARROW_RIGHT.create());
        continueBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        continueBtn.setIconAfterText(true);
        continueBtn.addClickListener(e -> confirmPresetSelection());

        footer.add(cancelBtn, continueBtn);

        overlayContent.add(header, presetGrid, footer);
        overlay.add(overlayContent);

        // 点击蒙版背景关闭（使用 JavaScript 过滤，只有直接点击 overlay 才关闭）
        overlay.getElement().addEventListener("click", e -> hidePresetOverlay())
            .setFilter("event.target === event.currentTarget");

        return overlay;
    }

    private Div createPresetCard(CKEditorPreset preset, String name, String description,
                                  String[] toolbarItems, String sizeLabel) {
        // 使用 preset.name() 作为 cardId
        String cardId = preset.name().toLowerCase();

        Div card = new Div();
        card.addClassName("preset-card");
        card.getElement().setAttribute("data-preset", preset.name());
        card.getElement().setAttribute("data-card-id", cardId);

        // 推荐标签
        if (preset == CKEditorPreset.STANDARD) {
            Span recommendBadge = new Span(I18nUtil.get("badge.recommended"));
            recommendBadge.addClassName("preset-recommend-badge");
            card.add(recommendBadge);
        }

        // Premium 标签
        if (preset == CKEditorPreset.COLLABORATIVE) {
            Span premiumBadge = new Span("Premium");
            premiumBadge.addClassName("preset-premium-badge");
            card.add(premiumBadge);
        }

        // 编辑器预览缩略图
        Div preview = new Div();
        preview.addClassName("preset-preview");

        // 模拟工具栏
        Div mockToolbar = new Div();
        mockToolbar.addClassName("preset-mock-toolbar");
        for (String item : toolbarItems) {
            Div btn = new Div();
            btn.addClassName("preset-mock-btn");
            btn.setText(getToolbarIcon(item));
            mockToolbar.add(btn);
        }

        // 模拟编辑区
        Div mockContent = new Div();
        mockContent.addClassName("preset-mock-content");

        Div line1 = new Div();
        line1.addClassName("preset-mock-line");
        line1.getElement().getStyle().set("width", "80%");

        Div line2 = new Div();
        line2.addClassName("preset-mock-line");
        line2.getElement().getStyle().set("width", "60%");

        Div line3 = new Div();
        line3.addClassName("preset-mock-line");
        line3.getElement().getStyle().set("width", "70%");

        mockContent.add(line1, line2, line3);

        preview.add(mockToolbar, mockContent);

        // 名称
        Span nameSpan = new Span(name);
        nameSpan.addClassName("preset-card-name");

        // 描述
        Span descSpan = new Span(description);
        descSpan.addClassName("preset-card-desc");

        // 大小标签
        Span sizeSpan = new Span(sizeLabel);
        sizeSpan.addClassName("preset-card-size");

        card.add(preview, nameSpan, descSpan, sizeSpan);

        // Coming Soon 卡片：灰化并禁止选择
        if (DISABLED_CARD_IDS.contains(cardId)) {
            applyDisabledStyle(card);
        } else {
            card.addClickListener(e -> selectPresetCardById(cardId));
        }

        // 存储映射关系
        presetCards.put(cardId, card);
        cardPresetMap.put(cardId, preset);

        return card;
    }

    /**
     * 创建带自定义徽章的预设卡片
     */
    private Div createPresetCardWithBadge(CKEditorPreset preset, String name, String description,
                                           String[] toolbarItems, String sizeLabel,
                                           String cardId, String badgeText) {
        Div card = new Div();
        card.addClassName("preset-card");
        card.getElement().setAttribute("data-preset", preset.name());
        card.getElement().setAttribute("data-card-id", cardId);

        // 自定义徽章
        if (badgeText != null && !badgeText.isEmpty()) {
            Span badge = new Span(badgeText);
            if ("AI".equals(badgeText) || "Premium".equals(sizeLabel)) {
                badge.addClassName("preset-premium-badge");
            } else if ("New".equals(badgeText)) {
                badge.addClassName("preset-new-badge");
            } else {
                badge.addClassName("preset-recommend-badge");
            }
            card.add(badge);
        }

        // 编辑器预览缩略图
        Div preview = new Div();
        preview.addClassName("preset-preview");

        // 模拟工具栏
        Div mockToolbar = new Div();
        mockToolbar.addClassName("preset-mock-toolbar");
        for (String item : toolbarItems) {
            Div btn = new Div();
            btn.addClassName("preset-mock-btn");
            btn.setText(getToolbarIcon(item));
            mockToolbar.add(btn);
        }

        // 模拟编辑区
        Div mockContent = new Div();
        mockContent.addClassName("preset-mock-content");

        Div line1 = new Div();
        line1.addClassName("preset-mock-line");
        line1.getElement().getStyle().set("width", "80%");

        Div line2 = new Div();
        line2.addClassName("preset-mock-line");
        line2.getElement().getStyle().set("width", "60%");

        Div line3 = new Div();
        line3.addClassName("preset-mock-line");
        line3.getElement().getStyle().set("width", "70%");

        mockContent.add(line1, line2, line3);

        preview.add(mockToolbar, mockContent);

        // 名称
        Span nameSpan = new Span(name);
        nameSpan.addClassName("preset-card-name");

        // 描述
        Span descSpan = new Span(description);
        descSpan.addClassName("preset-card-desc");

        // 大小标签
        Span sizeSpan = new Span(sizeLabel);
        sizeSpan.addClassName("preset-card-size");

        card.add(preview, nameSpan, descSpan, sizeSpan);

        // Coming Soon 卡片：灰化并禁止选择
        if (DISABLED_CARD_IDS.contains(cardId)) {
            applyDisabledStyle(card);
        } else {
            card.addClickListener(e -> selectPresetCardById(cardId));
        }

        // 存储映射关系
        presetCards.put(cardId, card);
        cardPresetMap.put(cardId, preset);

        return card;
    }

    /**
     * 将卡片设为灰化不可选状态，并添加 Coming Soon 遮罩
     */
    private void applyDisabledStyle(Div card) {
        card.addClassName("preset-card-disabled");
        card.getStyle()
            .set("cursor", "not-allowed")
            .set("overflow", "hidden");

        // 半透明毛玻璃遮罩
        Div overlay = new Div();
        overlay.getStyle()
            .set("position", "absolute")
            .set("inset", "0")
            .set("background", "rgba(255, 255, 255, 0.55)")
            .set("backdrop-filter", "blur(1px) grayscale(100%)")
            .set("-webkit-backdrop-filter", "blur(2px) grayscale(100%)")
            .set("z-index", "1")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("transition", "background 0.3s");

        Span comingSoon = new Span("Coming Soon");
        comingSoon.addClassName("coming-soon-badge");
        comingSoon.getStyle()
            .set("background", "linear-gradient(135deg, #6366f1, #8b5cf6)")
            .set("color", "white")
            .set("padding", "5px 16px")
            .set("border-radius", "20px")
            .set("font-size", "11px")
            .set("font-weight", "700")
            .set("letter-spacing", "0.5px")
            .set("text-transform", "uppercase")
            .set("box-shadow", "0 2px 8px rgba(99, 102, 241, 0.4)")
            .set("white-space", "nowrap")
            .set("animation", "coming-soon-pulse 2s ease-in-out infinite");

        overlay.add(comingSoon);
        card.add(overlay);

        // 注入 keyframes 动画（通过卡片的 JS 注入，仅一次）
        card.getElement().executeJs(
            "if (!document.getElementById('coming-soon-keyframes')) {" +
            "  const style = document.createElement('style');" +
            "  style.id = 'coming-soon-keyframes';" +
            "  style.textContent = `" +
            "    @keyframes coming-soon-pulse {" +
            "      0%, 100% { transform: scale(1); box-shadow: 0 2px 8px rgba(99,102,241,0.4); }" +
            "      50% { transform: scale(1.05); box-shadow: 0 4px 16px rgba(99,102,241,0.6); }" +
            "    }" +
            "    .preset-card-disabled:hover > div:last-child {" +
            "      background: rgba(255,255,255,0.35) !important;" +
            "    }" +
            "    [theme~=\"dark\"] .preset-card-disabled > div:last-child {" +
            "      background: rgba(0,0,0,0.55) !important;" +
            "    }" +
            "    [theme~=\"dark\"] .preset-card-disabled:hover > div:last-child {" +
            "      background: rgba(0,0,0,0.4) !important;" +
            "    }" +
            "  `;" +
            "  document.head.appendChild(style);" +
            "}"
        );
    }

    private String getToolbarIcon(String item) {
        return switch (item) {
            case "bold" -> "B";
            case "italic" -> "I";
            case "underline" -> "U";
            case "link" -> "🔗";
            case "list" -> "•";
            case "heading" -> "H";
            case "image" -> "🖼";
            case "table" -> "⊞";
            case "media" -> "▶";
            case "code" -> "<>";
            case "toc" -> "≡";
            case "pageBreak" -> "—";
            case "footnote" -> "¹";
            case "comments" -> "💬";
            case "trackChanges" -> "✎";
            case "realtime" -> "⟳";
            case "custom" -> "⚙";
            case "ai" -> "🤖";
            case "✨" -> "✨";
            case "/" -> "/";
            case "toggle" -> "▸";
            default -> "·";
        };
    }

    private void selectPresetCardById(String cardId) {
        selectedCardId = cardId;
        selectedPreset = cardPresetMap.get(cardId);

        // 更新卡片样式
        presetCards.values().forEach(card -> card.removeClassName("selected"));
        Div selectedCard = presetCards.get(cardId);
        if (selectedCard != null) {
            selectedCard.addClassName("selected");
        }
    }

    private void showPresetOverlay() {
        presetOverlay.setVisible(true);
        presetOverlay.addClassName("visible");

        // 默认选中 STANDARD
        if (selectedCardId == null) {
            selectedCardId = "standard";
            selectedPreset = CKEditorPreset.STANDARD;
        }
        selectPresetCardById(selectedCardId);
    }

    private void hidePresetOverlay() {
        presetOverlay.removeClassName("visible");
        presetOverlay.setVisible(false);
    }

    private void confirmPresetSelection() {
        if (state != null && selectedPreset != null) {
            state.setPreset(selectedPreset);
            state.setPresetCardId(selectedCardId); // 存储选择的卡片ID，用于识别 AI Document 等特殊预设
            state.setMode(BuilderState.Mode.PRESET);

            // 触发智能跳转回调
            if (onPresetSelectedCallback != null) {
                onPresetSelectedCallback.accept(selectedPreset);
            }
        }
        hidePresetOverlay();
    }

    private Div createModeCard(VaadinIcon iconType, String title, String description,
                                String[] features, BuilderState.Mode mode) {
        Div card = new Div();
        card.addClassName("mode-card");
        card.getElement().setAttribute("data-mode", mode.name());

        // 键盘导航支持
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().setAttribute("role", "button");
        card.getElement().setAttribute("aria-label", title + ": " + description);

        // 图标
        Div iconWrapper = new Div();
        iconWrapper.addClassName("mode-card-icon");
        Icon icon = iconType.create();
        icon.setSize("32px");
        iconWrapper.add(icon);

        // 标题
        H3 cardTitle = new H3(title);
        cardTitle.addClassName("mode-card-title");

        // 描述
        Paragraph cardDesc = new Paragraph(description);
        cardDesc.addClassName("mode-card-description");

        // 特性列表
        Div featureList = new Div();
        featureList.addClassName("mode-card-features");
        for (String feature : features) {
            Div featureItem = new Div();
            featureItem.addClassName("feature-item");

            Icon checkIcon = VaadinIcon.CHECK.create();
            checkIcon.setSize("14px");
            checkIcon.addClassName("feature-check");

            Span featureText = new Span(feature);

            featureItem.add(checkIcon, featureText);
            featureList.add(featureItem);
        }

        card.add(iconWrapper, cardTitle, cardDesc, featureList);

        // 点击选择
        card.addClickListener(e -> selectMode(mode));

        // 键盘事件支持 (Enter/Space)
        card.getElement().addEventListener("keydown", e -> selectMode(mode))
            .setFilter("event.key === 'Enter' || event.key === ' '");

        return card;
    }

    private void selectMode(BuilderState.Mode mode) {
        if (state == null) return;

        state.setMode(mode);

        // 更新卡片样式
        presetCard.getElement().getClassList().remove("selected");
        customCard.getElement().getClassList().remove("selected");

        if (mode == BuilderState.Mode.PRESET) {
            presetCard.getElement().getClassList().add("selected");
            // 显示预设选择蒙版
            showPresetOverlay();
        } else {
            customCard.getElement().getClassList().add("selected");
            // 触发自定义模式回调，跳转到编辑器类型步骤
            if (onCustomModeSelectedCallback != null) {
                onCustomModeSelectedCallback.run();
            }
        }
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;

        // 初始化 UI 状态
        if (content != null) {
            // 恢复之前的选择状态
            presetCard.getElement().getClassList().remove("selected");
            customCard.getElement().getClassList().remove("selected");

            if (state.getMode() == BuilderState.Mode.PRESET) {
                presetCard.getElement().getClassList().add("selected");
                selectedPreset = state.getPreset();
            } else {
                customCard.getElement().getClassList().add("selected");
            }
        }
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        if (state.getMode() == BuilderState.Mode.PRESET && state.getPreset() == null) {
            return ValidationResult.error(I18nUtil.get("validation.selectPreset"));
        }
        return ValidationResult.ok();
    }

    /**
     * 设置预设选择完成后的回调
     * 用于智能跳转功能：选择预设后自动跳到 Step 6
     */
    public void setOnPresetSelectedCallback(Consumer<CKEditorPreset> callback) {
        this.onPresetSelectedCallback = callback;
    }

    /**
     * 设置自定义模式选择后的回调
     * 用于智能跳转功能：选择自定义后跳到编辑器类型步骤
     */
    public void setOnCustomModeSelectedCallback(Runnable callback) {
        this.onCustomModeSelectedCallback = callback;
    }
}
