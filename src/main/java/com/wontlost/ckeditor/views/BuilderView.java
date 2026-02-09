package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.component.dependency.CssImport;
import com.wontlost.ckeditor.*;

import com.vaadin.flow.component.textfield.TextField;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.wontlost.ckeditor.utils.Constant.*;

/**
 * CKEditor Builder 主视图
 * 使用 VaadinCKEditor 5.0.0 API
 * 响应式设计：支持桌面端 SplitLayout + 移动端 Tabs 布局
 */
// Hidden: Classic Builder is deprecated, use Wizard Builder instead
// @Route(value = PAGE_BUILDER, layout = MainView.class)
// @PageTitle(TITLE_BUILDER)
// @CssImport("./styles/views/app.css")
public class BuilderView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(BuilderView.class);

    // 编辑器类型
    private CKEditorType selectedEditorType = CKEditorType.CLASSIC;

    // 预设
    private CKEditorPreset selectedPreset = CKEditorPreset.STANDARD;

    // 选中的插件
    private final Set<CKEditorPlugin> selectedPlugins = new LinkedHashSet<>();

    // 自定义工具栏项
    private final List<String> customToolbarItems = new ArrayList<>();

    // 语言设置
    private String selectedLanguage = "en";

    // 主题设置 - 默认使用自动模式，自动同步 Vaadin Lumo 主题
    private CKEditorTheme selectedTheme = CKEditorTheme.AUTO;

    // 编辑器预览容器（外层，不变）
    private Div editorContainer;

    // 编辑器包装器（内层，每次刷新时替换）
    private Div editorWrapper;

    // 当前编辑器实例
    private VaadinCKEditor currentEditor;

    // 编辑器实例计数器（用于生成唯一 ID）
    private int editorInstanceCount = 0;

    // 插件复选框映射
    private final Map<CKEditorPlugin, Checkbox> pluginCheckboxes = new HashMap<>();

    // 分类详情组件映射（用于更新标签）
    private final Map<CKEditorPlugin.Category, Details> categoryDetails = new HashMap<>();

    // 配置预览容器
    private Div configPreviewContainer;

    // 插件搜索和过滤
    private String pluginSearchTerm = "";
    private String activePluginFilter = "all"; // all, selected, free, premium

    // 标记配置是否有未保存的更改
    private boolean hasUnsavedChanges = false;

    // 导出代码语言
    private String exportLanguage = "java"; // java, typescript

    // 面板组件引用
    private VerticalLayout configPanel;
    private VerticalLayout previewPanel;
    private VerticalLayout exportPanel;

    // Plugins tab label reference for dynamic updates
    private Span pluginsTabLabel;

    public BuilderView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("builder-view");
        getStyle().set("background", "#f5f6f8");

        initDefaultSelections();
        add(createMainLayout());
    }

    /**
     * 创建主布局 - 使用 SplitLayout，CSS 处理响应式
     */
    private Component createMainLayout() {
        SplitLayout mainSplit = new SplitLayout();
        mainSplit.addClassName("main-split-layout");
        mainSplit.setSizeFull();
        mainSplit.setSplitterPosition(26);

        // 左侧：配置面板
        configPanel = createConfigPanel();

        // 右侧：预览和导出
        SplitLayout rightSplit = new SplitLayout();
        rightSplit.addClassName("right-split-layout");
        rightSplit.setOrientation(SplitLayout.Orientation.VERTICAL);
        rightSplit.setSplitterPosition(70);

        previewPanel = createPreviewPanel();
        exportPanel = createExportPanel();

        rightSplit.addToPrimary(previewPanel);
        rightSplit.addToSecondary(exportPanel);

        mainSplit.addToPrimary(configPanel);
        mainSplit.addToSecondary(rightSplit);

        return mainSplit;
    }

    private void initDefaultSelections() {
        // 从预设加载默认插件
        selectedPlugins.addAll(selectedPreset.getPlugins());
        // 默认工具栏
        customToolbarItems.addAll(Arrays.asList(selectedPreset.getDefaultToolbar()));
    }

    private VerticalLayout createConfigPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("config-panel");
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-right", "1px solid var(--lumo-contrast-10pct)");

        // 选项卡 - 现代风格 (same height as Preview panel header)
        Tab basicTab = createStyledTab(VaadinIcon.COG, "Basic", false);
        Tab pluginsTab = createStyledTab(VaadinIcon.PLUG, "Plugins (" + selectedPlugins.size() + ")", true);
        Tab toolbarTab = createStyledTab(VaadinIcon.TOOLS, "Toolbar", false);

        Tabs tabs = new Tabs(basicTab, pluginsTab, toolbarTab);
        tabs.setWidthFull();
        tabs.getStyle()
            .set("--lumo-tab-padding", "12px 16px")
            .set("min-height", "57px")  // Match Preview panel header height (16px padding * 2 + content)
            .set("padding", "0 16px")
            .set("background", "var(--app-bg-primary, white)")
            .set("box-shadow", "inset 0 -1px 0 var(--app-border-color, #e2e8f0)")
            .set("display", "flex")
            .set("align-items", "center");

        // 内容区域
        VerticalLayout basicContent = createBasicSettings();
        VerticalLayout pluginContent = createPluginSettings();
        VerticalLayout toolbarContent = createToolbarSettings();

        basicContent.setVisible(true);
        pluginContent.setVisible(false);
        toolbarContent.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            basicContent.setVisible(event.getSelectedTab() == basicTab);
            pluginContent.setVisible(event.getSelectedTab() == pluginsTab);
            toolbarContent.setVisible(event.getSelectedTab() == toolbarTab);
        });

        Scroller scroller = new Scroller();
        scroller.setSizeFull();
        scroller.getStyle()
            .set("--vaadin-scroller-scroll-padding", "var(--lumo-space-m)");

        VerticalLayout content = new VerticalLayout(basicContent, pluginContent, toolbarContent);
        content.setPadding(true);
        content.setSpacing(true);
        content.getStyle().set("padding", "var(--lumo-space-m)");
        scroller.setContent(content);

        panel.add(tabs, scroller);
        panel.setFlexGrow(1, scroller);

        return panel;
    }

    private Tab createStyledTab(VaadinIcon iconType, String label, boolean isPluginsTab) {
        Icon icon = iconType.create();
        icon.setSize("16px");
        icon.getStyle()
            .set("margin-right", "var(--lumo-space-xs)");

        Span text = new Span(label);
        text.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "500");

        // Store reference to plugins tab label for dynamic updates
        if (isPluginsTab) {
            pluginsTabLabel = text;
        }

        HorizontalLayout tabContent = new HorizontalLayout(icon, text);
        tabContent.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        tabContent.setSpacing(false);
        tabContent.setPadding(false);

        Tab tab = new Tab(tabContent);
        return tab;
    }

    private VerticalLayout createBasicSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.getStyle().set("gap", "var(--lumo-space-m)");

        // 预设配置卡片
        layout.add(createConfigCard(
            VaadinIcon.OPTIONS,
            "Preset",
            "Select a predefined configuration",
            createPresetSelector()
        ));

        // 编辑器类型卡片
        layout.add(createConfigCard(
            VaadinIcon.VIEWPORT,
            "Editor Type",
            "Choose the editor display mode",
            createEditorTypeSelector()
        ));

        // 语言设置卡片
        layout.add(createConfigCard(
            VaadinIcon.GLOBE,
            "Language",
            "Set the UI language",
            createLanguageSelector()
        ));

        // 主题设置卡片
        layout.add(createConfigCard(
            VaadinIcon.PAINT_ROLL,
            "Theme",
            "Choose light or dark theme",
            createThemeSelector()
        ));

        // 粘性应用按钮栏
        layout.add(createStickyApplyBar());

        return layout;
    }

    /**
     * 创建粘性应用按钮栏
     */
    private Div createStickyApplyBar() {
        Div stickyBar = new Div();
        stickyBar.addClassName("sticky-apply-bar");
        stickyBar.getStyle()
            .set("position", "sticky")
            .set("bottom", "0")
            .set("left", "0")
            .set("right", "0")
            .set("padding", "16px")
            .set("backdrop-filter", "blur(8px)")
            .set("-webkit-backdrop-filter", "blur(8px)");
        // Background is handled by CSS with dark mode support

        Button applyBtn = new Button("Apply Changes", VaadinIcon.CHECK.create());
        applyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyBtn.addClassName("apply-button");
        applyBtn.setWidthFull();
        applyBtn.getStyle()
            .set("height", "48px")
            .set("font-weight", "600")
            .set("font-size", "15px")
            .set("background", "linear-gradient(135deg, #2563eb 0%, #3b82f6 100%)")
            .set("border", "none")
            .set("border-radius", "12px")
            .set("box-shadow", "0 4px 14px rgba(37, 99, 235, 0.4)")
            .set("cursor", "pointer")
            .set("transition", "all 0.2s ease");

        applyBtn.addClickListener(e -> {
            refreshEditor();
            hasUnsavedChanges = false;
            Notification.show("Configuration applied", 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        stickyBar.add(applyBtn);
        return stickyBar;
    }

    private Div createConfigCard(VaadinIcon iconType, String title, String description, Component content) {
        Div card = new Div();
        card.addClassName("config-card");
        // 现代卡片样式 - 带阴影和悬停效果
        card.getStyle()
            .set("background", "var(--app-bg-primary, white)")
            .set("border-radius", "16px")
            .set("padding", "20px")
            .set("margin-bottom", "16px")
            .set("border", "1px solid var(--app-border-color, #e5e7eb)")
            .set("box-shadow", "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)")
            .set("transition", "all 0.2s ease")
            .set("width", "100%")
            .set("box-sizing", "border-box");

        // 卡片头部
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("config-card-header");
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.getStyle()
            .set("margin-bottom", "12px")
            .set("gap", "12px");

        // 图标容器 - 渐变背景
        Div iconContainer = new Div();
        iconContainer.addClassName("config-card-icon");
        iconContainer.getStyle()
            .set("width", "40px")
            .set("height", "40px")
            .set("min-width", "40px")
            .set("border-radius", "12px")
            .set("background", "linear-gradient(135deg, #2563eb 0%, #3b82f6 100%)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("box-shadow", "0 4px 12px rgba(37, 99, 235, 0.3)");

        Icon icon = iconType.create();
        icon.setSize("18px");
        icon.getStyle().set("color", "white");
        iconContainer.add(icon);

        // 标题和描述
        VerticalLayout textSection = new VerticalLayout();
        textSection.setPadding(false);
        textSection.setSpacing(false);
        textSection.getStyle().set("gap", "2px");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("config-card-title");
        titleSpan.getStyle()
            .set("font-weight", "600")
            .set("font-size", "15px")
            .set("color", "var(--app-text-primary, #1f2937)");

        Span descSpan = new Span(description);
        descSpan.addClassName("config-card-description");
        descSpan.getStyle()
            .set("font-size", "13px")
            .set("color", "var(--app-text-muted, #6b7280)");

        textSection.add(titleSpan, descSpan);

        header.add(iconContainer, textSection);

        card.add(header, content);
        return card;
    }

    private Component createPresetSelector() {
        ComboBox<CKEditorPreset> presetCombo = new ComboBox<>();
        presetCombo.setItems(CKEditorPreset.values());
        presetCombo.setValue(selectedPreset);
        presetCombo.setItemLabelGenerator(CKEditorPreset::getDisplayName);
        presetCombo.setWidthFull();
        presetCombo.getStyle().set("--vaadin-input-field-border-radius", "8px");
        presetCombo.addValueChangeListener(e -> {
            selectedPreset = e.getValue();
            selectedPlugins.clear();
            selectedPlugins.addAll(selectedPreset.getPlugins());
            customToolbarItems.clear();
            customToolbarItems.addAll(Arrays.asList(selectedPreset.getDefaultToolbar()));
            updatePluginCheckboxes();
            updateConfigPreview();
        });

        return presetCombo;
    }

    private Component createEditorTypeSelector() {
        ComboBox<CKEditorType> editorTypeCombo = new ComboBox<>();
        editorTypeCombo.setItems(CKEditorType.values());
        editorTypeCombo.setValue(selectedEditorType);
        editorTypeCombo.setItemLabelGenerator(type -> {
            switch (type) {
                case CLASSIC: return "Classic (Fixed toolbar)";
                case INLINE: return "Inline (Click to edit)";
                case BALLOON: return "Balloon (Floating toolbar)";
                case DECOUPLED: return "Decoupled (Separate toolbar)";
                default: return type.name();
            }
        });
        editorTypeCombo.setWidthFull();
        editorTypeCombo.getStyle().set("--vaadin-input-field-border-radius", "8px");
        editorTypeCombo.addValueChangeListener(e -> {
            selectedEditorType = e.getValue();
            updateConfigPreview();
        });

        return editorTypeCombo;
    }

    private Component createLanguageSelector() {
        ComboBox<String> languageCombo = new ComboBox<>();
        languageCombo.setItems("en", "zh-cn", "zh", "ja", "ko", "de", "fr", "es", "pt", "ru", "ar");
        languageCombo.setValue(selectedLanguage);
        languageCombo.setItemLabelGenerator(lang -> {
            switch (lang) {
                case "en": return "English";
                case "zh-cn": return "Chinese (Simplified)";
                case "zh": return "Chinese (Traditional)";
                case "ja": return "Japanese";
                case "ko": return "Korean";
                case "de": return "German";
                case "fr": return "French";
                case "es": return "Spanish";
                case "pt": return "Portuguese";
                case "ru": return "Russian";
                case "ar": return "Arabic";
                default: return lang;
            }
        });
        languageCombo.setWidthFull();
        languageCombo.getStyle().set("--vaadin-input-field-border-radius", "8px");
        languageCombo.addValueChangeListener(e -> {
            selectedLanguage = e.getValue();
            updateConfigPreview();
        });

        return languageCombo;
    }

    private Component createThemeSelector() {
        ComboBox<CKEditorTheme> themeCombo = new ComboBox<>();
        themeCombo.setItems(CKEditorTheme.values());
        themeCombo.setValue(selectedTheme);
        themeCombo.setItemLabelGenerator(theme -> {
            switch (theme) {
                case AUTO: return "Auto (Vaadin Sync)";
                case LIGHT: return "Light Theme";
                case DARK: return "Dark Theme";
                default: return theme.name();
            }
        });
        themeCombo.setWidthFull();
        themeCombo.getStyle().set("--vaadin-input-field-border-radius", "8px");
        themeCombo.addValueChangeListener(e -> {
            selectedTheme = e.getValue();
            // Sync Vaadin Lumo theme with CKEditor theme selection
            syncVaadinTheme(selectedTheme);
            updateConfigPreview();
        });

        return themeCombo;
    }

    /**
     * Sync Vaadin Lumo theme with CKEditor theme selection.
     * This ensures the entire app shell (including editor container wrapper)
     * matches the selected theme.
     */
    private void syncVaadinTheme(CKEditorTheme theme) {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        switch (theme) {
            case DARK:
                // Set dark theme on HTML element
                ui.getPage().executeJs(
                    "document.documentElement.setAttribute('theme', 'dark');" +
                    "setTimeout(function() { window.applyDarkModeStyles && window.applyDarkModeStyles(true); }, 50);"
                );
                break;
            case LIGHT:
                // Remove dark theme (use light/default)
                ui.getPage().executeJs(
                    "document.documentElement.removeAttribute('theme');" +
                    "setTimeout(function() { window.applyDarkModeStyles && window.applyDarkModeStyles(false); }, 50);"
                );
                break;
            case AUTO:
            default:
                // Auto mode: detect system preference
                ui.getPage().executeJs(
                    "var isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;" +
                    "if (isDark) {" +
                    "  document.documentElement.setAttribute('theme', 'dark');" +
                    "} else {" +
                    "  document.documentElement.removeAttribute('theme');" +
                    "}" +
                    "setTimeout(function() { window.applyDarkModeStyles && window.applyDarkModeStyles(isDark); }, 50);"
                );
                break;
        }

        // Inject and immediately execute dark mode styling
        // Using separate executeJs calls for better reliability
        injectDarkModeScript(ui);
    }

    /**
     * Inject the dark mode JavaScript helper and apply styles.
     * Split into multiple executeJs calls for reliability.
     */
    private void injectDarkModeScript(UI ui) {
        // First, define the helper function
        ui.getPage().executeJs(
            "window.applyDarkModeToElements = function() {" +
            "  var isDark = document.documentElement.getAttribute('theme') === 'dark';" +
            "  document.querySelectorAll('.plugin-search-bar').forEach(function(el) {" +
            "    el.style.setProperty('background', isDark ? '#1f2937' : 'white', 'important');" +
            "    el.style.setProperty('border-color', isDark ? '#374151' : '#e5e7eb', 'important');" +
            "  });" +
            "  document.querySelectorAll('.plugin-filter-buttons, .export-lang-tabs').forEach(function(el) {" +
            "    el.style.setProperty('background', isDark ? '#374151' : '#f3f4f6', 'important');" +
            "  });" +
            "  document.querySelectorAll('.plugin-filter-button, .export-lang-tab').forEach(function(btn) {" +
            "    var isActive = btn.classList.contains('active');" +
            "    if (isActive) {" +
            "      btn.style.setProperty('background', isDark ? '#1f2937' : 'white', 'important');" +
            "      btn.style.setProperty('color', isDark ? '#60a5fa' : '#2563eb', 'important');" +
            "    } else {" +
            "      btn.style.setProperty('background', 'transparent', 'important');" +
            "      btn.style.setProperty('color', isDark ? '#9ca3af' : '#6b7280', 'important');" +
            "    }" +
            "  });" +
            "  document.querySelectorAll('.plugin-search-input, .plugin-search-bar vaadin-text-field').forEach(function(el) {" +
            "    if (el.shadowRoot) {" +
            "      var inputField = el.shadowRoot.querySelector('[part=\"input-field\"]');" +
            "      if (inputField) {" +
            "        inputField.style.setProperty('background', isDark ? '#374151' : '#f9fafb', 'important');" +
            "        inputField.style.setProperty('border-color', isDark ? '#4b5563' : '#e5e7eb', 'important');" +
            "        inputField.style.setProperty('color', isDark ? '#f3f4f6' : '#1f2937', 'important');" +
            "      }" +
            "    }" +
            "  });" +
            "};"
        );

        // Set up observer for theme changes (only once)
        ui.getPage().executeJs(
            "if (!window.darkModeObserver) {" +
            "  window.darkModeObserver = new MutationObserver(function(mutations) {" +
            "    if (window.applyDarkModeToElements) window.applyDarkModeToElements();" +
            "  });" +
            "  window.darkModeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['theme'] });" +
            "}"
        );

        // Apply styles immediately
        ui.getPage().executeJs(
            "setTimeout(function() { if (window.applyDarkModeToElements) window.applyDarkModeToElements(); }, 100);"
        );
    }

    private VerticalLayout createPluginSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.getStyle().set("gap", "12px");

        // 插件搜索和筛选栏 - 现代风格 (using Lumo variables for dark mode)
        Div searchBar = new Div();
        searchBar.addClassName("plugin-search-bar");
        searchBar.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "var(--lumo-space-s)")
            .set("padding", "16px")
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "16px")
            .set("margin-bottom", "12px")
            .set("box-shadow", "var(--lumo-box-shadow-xs)")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("width", "100%");  // Match vaadin-details width

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search plugins...");
        Icon searchIcon = VaadinIcon.SEARCH.create();
        searchIcon.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        searchField.setPrefixComponent(searchIcon);
        searchField.setWidthFull();
        searchField.addClassName("plugin-search-input");
        searchField.getStyle()
            .set("--vaadin-input-field-border-radius", "10px")
            .set("--vaadin-input-field-background", "var(--lumo-contrast-5pct)")
            .set("margin-bottom", "12px");
        searchField.addValueChangeListener(e -> {
            pluginSearchTerm = e.getValue().toLowerCase();
            applyPluginFilters();
        });

        // 筛选按钮组 - 现代分段样式 (using Lumo variables for dark mode)
        HorizontalLayout filterButtons = new HorizontalLayout();
        filterButtons.addClassName("plugin-filter-buttons");
        filterButtons.setSpacing(false);
        filterButtons.setWidthFull();
        filterButtons.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "10px")
            .set("padding", "4px");

        Button allFilter = createFilterButton("All", "all");
        Button selectedFilter = createFilterButton("Selected", "selected");

        filterButtons.add(allFilter, selectedFilter);
        searchBar.add(searchField, filterButtons);
        layout.add(searchBar);

        // 按分类显示插件
        for (CKEditorPlugin.Category category : CKEditorPlugin.Category.values()) {
            Set<CKEditorPlugin> categoryPlugins = CKEditorPlugin.getByCategory(category);
            if (!categoryPlugins.isEmpty()) {
                // Calculate initial selected count
                long selectedCount = categoryPlugins.stream()
                    .filter(selectedPlugins::contains)
                    .count();
                Details details = new Details(
                    getCategoryDisplayName(category) + " (" + selectedCount + "/" + categoryPlugins.size() + ")",
                    createPluginCheckboxes(categoryPlugins)
                );
                details.setOpened(category == CKEditorPlugin.Category.CORE ||
                                  category == CKEditorPlugin.Category.BASIC_STYLES);
                details.setWidthFull();
                categoryDetails.put(category, details);
                layout.add(details);
            }
        }

        // 快捷操作 - 现代风格，固定在底部
        Div quickActionsCard = new Div();
        quickActionsCard.addClassName("quick-actions-card");
        quickActionsCard.getStyle()
            .set("position", "sticky")
            .set("bottom", "0")
            .set("background", "var(--lumo-base-color)")
            .set("padding", "12px 16px")
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("margin-top", "auto")
            .set("margin-left", "-16px")
            .set("margin-right", "-16px")
            .set("margin-bottom", "-16px")
            .set("z-index", "10");

        HorizontalLayout quickActions = new HorizontalLayout();
        quickActions.setWidthFull();
        quickActions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        quickActions.getStyle().set("gap", "8px");

        Button selectAll = new Button("Select All", e -> {
            selectedPlugins.addAll(Arrays.asList(CKEditorPlugin.values()));
            updatePluginCheckboxes();
            updateConfigPreview();
        });
        selectAll.addThemeVariants(ButtonVariant.LUMO_SMALL);
        selectAll.getStyle()
            .set("flex", "1")
            .set("background", "linear-gradient(135deg, #10b981 0%, #34d399 100%)")
            .set("color", "white")
            .set("border", "none")
            .set("border-radius", "10px")
            .set("font-weight", "500");

        Button clearAll = new Button("Clear", e -> {
            selectedPlugins.clear();
            selectedPlugins.add(CKEditorPlugin.ESSENTIALS);
            selectedPlugins.add(CKEditorPlugin.PARAGRAPH);
            updatePluginCheckboxes();
            updateConfigPreview();
        });
        clearAll.addThemeVariants(ButtonVariant.LUMO_SMALL);
        clearAll.getStyle()
            .set("flex", "1")
            .set("background", "linear-gradient(135deg, #ef4444 0%, #f87171 100%)")
            .set("color", "white")
            .set("border", "none")
            .set("border-radius", "10px")
            .set("font-weight", "500");

        Button resetToPreset = new Button("Reset", e -> {
            selectedPlugins.clear();
            selectedPlugins.addAll(selectedPreset.getPlugins());
            updatePluginCheckboxes();
            updateConfigPreview();
        });
        resetToPreset.addThemeVariants(ButtonVariant.LUMO_SMALL);
        resetToPreset.addClassName("reset-button");
        resetToPreset.getStyle()
            .set("flex", "1")
            .set("background", "var(--app-bg-tertiary, #f3f4f6)")
            .set("color", "var(--app-text-secondary, #374151)")
            .set("border", "none")
            .set("border-radius", "10px")
            .set("font-weight", "500");

        quickActions.add(selectAll, clearAll, resetToPreset);
        quickActionsCard.add(quickActions);
        layout.add(quickActionsCard);

        return layout;
    }

    private String getCategoryDisplayName(CKEditorPlugin.Category category) {
        // Use the library's built-in display name for consistency
        return category.getDisplayName();
    }

    private VerticalLayout createPluginCheckboxes(Set<CKEditorPlugin> plugins) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        for (CKEditorPlugin plugin : plugins) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.setSpacing(true);
            row.getStyle()
                .set("padding", "var(--lumo-space-xs) 0")
                .set("border-radius", "6px")
                .set("transition", "background 0.15s ease");

            Checkbox checkbox = new Checkbox(plugin.getJsName());
            checkbox.setValue(selectedPlugins.contains(plugin));
            checkbox.addValueChangeListener(e -> {
                // 跳过批量更新触发的事件
                if (isUpdatingCheckboxes) return;

                if (e.getValue()) {
                    selectedPlugins.add(plugin);
                } else {
                    selectedPlugins.remove(plugin);
                }
                // Update tab and category labels
                updatePluginsTabLabel();
                updateCategoryLabels();
                updateConfigPreview();
            });

            pluginCheckboxes.put(plugin, checkbox);

            row.add(checkbox);

            layout.add(row);
        }

        return layout;
    }

    // 标记是否正在批量更新 checkbox（避免触发 listener）
    private boolean isUpdatingCheckboxes = false;

    private void updatePluginCheckboxes() {
        isUpdatingCheckboxes = true;
        try {
            pluginCheckboxes.forEach((plugin, checkbox) -> {
                checkbox.setValue(selectedPlugins.contains(plugin));
            });
            // Update plugins tab label with count
            updatePluginsTabLabel();
            // Update category labels with selected/total counts
            updateCategoryLabels();
        } finally {
            isUpdatingCheckboxes = false;
        }
    }

    /**
     * Update the Plugins tab label with current selection count
     */
    private void updatePluginsTabLabel() {
        if (pluginsTabLabel != null) {
            pluginsTabLabel.setText("Plugins (" + selectedPlugins.size() + ")");
        }
    }

    /**
     * Update all category labels with selected/total counts
     */
    private void updateCategoryLabels() {
        for (CKEditorPlugin.Category category : categoryDetails.keySet()) {
            Details details = categoryDetails.get(category);
            if (details != null) {
                Set<CKEditorPlugin> categoryPlugins = CKEditorPlugin.getByCategory(category);
                long selectedCount = categoryPlugins.stream()
                    .filter(selectedPlugins::contains)
                    .count();
                String newSummary = getCategoryDisplayName(category) + " (" + selectedCount + "/" + categoryPlugins.size() + ")";
                details.setSummaryText(newSummary);
            }
        }
    }

    /**
     * 创建筛选按钮 - 现代风格
     */
    private Button createFilterButton(String label, String filterValue) {
        Button btn = new Button(label);
        btn.addClassName("plugin-filter-button");
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST);
        btn.getStyle()
            .set("flex", "1")
            .set("border-radius", "8px")
            .set("font-size", "13px")
            .set("font-weight", "500")
            .set("min-height", "36px")
            .set("border", "none")
            .set("transition", "all 0.15s ease")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("color", "var(--lumo-secondary-text-color)");

        if ("all".equals(filterValue)) {
            btn.addClassName("active");
            btn.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-primary-color)");
            // Active state styles handled by CSS
        }
        // Styles handled by CSS class for proper dark mode support

        btn.addClickListener(e -> {
            activePluginFilter = filterValue;
            // 更新按钮样式 - via Lumo CSS variables for dark mode support
            btn.getParent().ifPresent(parent -> {
                parent.getChildren().forEach(child -> {
                    if (child instanceof Button) {
                        Button b = (Button) child;
                        b.getElement().getClassList().remove("active");
                        // Reset to inactive state
                        b.getStyle()
                            .set("background", "var(--lumo-contrast-5pct)")
                            .set("color", "var(--lumo-secondary-text-color)");
                    }
                });
            });
            btn.addClassName("active");
            // Set active state
            btn.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-primary-color)");
            applyPluginFilters();
        });
        return btn;
    }

    /**
     * 应用插件搜索和筛选
     */
    private void applyPluginFilters() {
        pluginCheckboxes.forEach((plugin, checkbox) -> {
            boolean matchesSearch = pluginSearchTerm.isEmpty() ||
                plugin.getJsName().toLowerCase().contains(pluginSearchTerm) ||
                plugin.name().toLowerCase().contains(pluginSearchTerm);

            boolean matchesFilter = "selected".equals(activePluginFilter)
                ? selectedPlugins.contains(plugin)
                : true; // "all"

            // 控制父行的可见性
            checkbox.getParent().ifPresent(row ->
                row.setVisible(matchesSearch && matchesFilter)
            );
        });
        markUnsavedChanges();
    }

    /**
     * 标记有未保存的更改
     */
    private void markUnsavedChanges() {
        hasUnsavedChanges = true;
    }

    private VerticalLayout createToolbarSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.getStyle().set("gap", "16px");

        // 说明卡片 - 现代风格
        Div infoCard = new Div();
        infoCard.getStyle()
            .set("background", "linear-gradient(135deg, #10b981 0%, #34d399 100%)")
            .set("border-radius", "16px")
            .set("padding", "20px")
            .set("box-shadow", "0 4px 14px rgba(16, 185, 129, 0.3)");

        HorizontalLayout infoContent = new HorizontalLayout();
        infoContent.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        infoContent.setWidthFull();
        infoContent.getStyle().set("gap", "16px");

        // 图标容器
        Div iconContainer = new Div();
        iconContainer.getStyle()
            .set("width", "48px")
            .set("height", "48px")
            .set("min-width", "48px")
            .set("border-radius", "12px")
            .set("background", "rgba(255, 255, 255, 0.2)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center");
        Icon infoIcon = VaadinIcon.INFO_CIRCLE.create();
        infoIcon.setSize("24px");
        infoIcon.getStyle().set("color", "white");
        iconContainer.add(infoIcon);

        VerticalLayout textContent = new VerticalLayout();
        textContent.setPadding(false);
        textContent.setSpacing(false);
        textContent.getStyle().set("gap", "4px");

        Span infoTitle = new Span("Auto-generated Toolbar");
        infoTitle.getStyle()
            .set("font-size", "15px")
            .set("font-weight", "600")
            .set("color", "white");

        Paragraph infoText = new Paragraph("The toolbar is automatically generated based on selected plugins. Each plugin adds its corresponding toolbar items.");
        infoText.getStyle()
            .set("font-size", "13px")
            .set("color", "rgba(255, 255, 255, 0.85)")
            .set("margin", "0")
            .set("line-height", "1.5");

        textContent.add(infoTitle, infoText);
        infoContent.add(iconContainer, textContent);
        infoCard.add(infoContent);

        layout.add(infoCard);

        // 工具栏预览卡片
        Div previewCard = new Div();
        previewCard.addClassName("toolbar-preview-card");
        // Styles handled by CSS class for proper dark mode support

        H4 previewTitle = new H4("Current Toolbar Items");
        previewTitle.getStyle()
            .set("margin", "0 0 12px 0")
            .set("font-size", "15px")
            .set("font-weight", "600")
            .set("color", "var(--app-text-primary, #1f2937)");

        Div toolbarPreview = new Div();
        toolbarPreview.getStyle()
            .set("font-family", "'JetBrains Mono', 'SF Mono', monospace")
            .set("font-size", "12px")
            .set("background", "linear-gradient(135deg, #1e293b 0%, #0f172a 100%)")
            .set("color", "#e2e8f0")
            .set("padding", "16px")
            .set("border-radius", "12px")
            .set("white-space", "pre-wrap")
            .set("max-height", "180px")
            .set("overflow", "auto")
            .set("line-height", "1.7")
            .set("border", "1px solid #334155");

        updateToolbarPreview(toolbarPreview);

        previewCard.add(previewTitle, toolbarPreview);
        layout.add(previewCard);

        return layout;
    }

    private void updateToolbarPreview(Div container) {
        Set<String> items = new LinkedHashSet<>();
        for (CKEditorPlugin plugin : selectedPlugins) {
            items.addAll(plugin.getToolbarItems());
        }
        container.setText(String.join(", ", items));
    }

    private VerticalLayout createPreviewPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("preview-panel");
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "0");

        // 面板头部 - 现代风格
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("panel-header");
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle()
            .set("padding", "16px 24px")
            .set("background", "var(--app-gradient-header, linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%))")
            .set("border-bottom", "1px solid var(--app-border-color, #e2e8f0)");

        // 标题区
        HorizontalLayout titleSection = new HorizontalLayout();
        titleSection.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        titleSection.getStyle().set("gap", "12px");

        Span titleBar = new Span();
        titleBar.getStyle()
            .set("width", "4px")
            .set("height", "24px")
            .set("background", "linear-gradient(180deg, #2563eb 0%, #3b82f6 100%)")
            .set("border-radius", "2px");

        H3 title = new H3("Preview");
        title.getStyle()
            .set("margin", "0")
            .set("font-size", "18px")
            .set("font-weight", "700")
            .set("color", "var(--app-text-primary, #1e293b)");

        Span editorTypeBadge = new Span(selectedEditorType.name());
        editorTypeBadge.getStyle()
            .set("font-size", "11px")
            .set("font-weight", "600")
            .set("color", "#2563eb")
            .set("background", "linear-gradient(135deg, rgba(37, 99, 235, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%)")
            .set("padding", "4px 12px")
            .set("border-radius", "20px")
            .set("text-transform", "uppercase")
            .set("letter-spacing", "0.5px");

        titleSection.add(titleBar, title, editorTypeBadge);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        refreshBtn.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "8px")
            .set("color", "#64748b");
        refreshBtn.addClickListener(e -> refreshEditor());

        header.add(titleSection, refreshBtn);

        // 编辑器容器 - 现代风格
        Div editorWrapper = new Div();
        editorWrapper.setSizeFull();
        editorWrapper.getStyle()
            .set("padding", "20px")
            .set("overflow", "auto")
            .set("box-sizing", "border-box")
            .set("background", "var(--app-bg-secondary, #f8fafc)");
        editorWrapper.addClassName("editor-wrapper");

        editorContainer = new Div();
        editorContainer.getStyle()
            .set("width", "100%")
            .set("max-width", "100%")
            .set("border", "1px solid var(--app-border-color, #e2e8f0)")
            .set("border-radius", "16px")
            .set("overflow", "visible")  // Allow sticky toolbar to work properly
            .set("box-shadow", "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)")
            .set("transition", "all 0.2s ease")
            .set("box-sizing", "border-box")
            .set("background", "var(--app-bg-primary, white)");
        editorContainer.addClassName("editor-container");

        editorWrapper.add(editorContainer);

        panel.add(header, editorWrapper);
        panel.setFlexGrow(1, editorWrapper);

        return panel;
    }

    private VerticalLayout createExportPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("export-panel");
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.getStyle()
            .set("background", "var(--app-bg-secondary, #f9fafb)")
            .set("border-top", "1px solid var(--app-border-color, #e2e8f0)");

        // 面板头部 - 现代风格
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("panel-header");
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle()
            .set("padding", "16px 24px")
            .set("background", "var(--app-gradient-header, linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%))")
            .set("border-bottom", "1px solid var(--app-border-color, #e2e8f0)");

        // 标题
        HorizontalLayout titleSection = new HorizontalLayout();
        titleSection.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        titleSection.getStyle().set("gap", "12px");

        Span titleBar = new Span();
        titleBar.getStyle()
            .set("width", "4px")
            .set("height", "24px")
            .set("background", "linear-gradient(180deg, #10b981 0%, #34d399 100%)")
            .set("border-radius", "2px");

        H3 title = new H3("Export Code");
        title.getStyle()
            .set("margin", "0")
            .set("font-size", "18px")
            .set("font-weight", "700")
            .set("color", "var(--app-text-primary, #1e293b)");

        titleSection.add(titleBar, title);

        // 语言切换选项卡 - 现代分段控件样式 (using Lumo variables for dark mode)
        HorizontalLayout langTabs = new HorizontalLayout();
        langTabs.addClassName("export-lang-tabs");
        langTabs.setSpacing(false);
        langTabs.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "10px")
            .set("padding", "4px");

        Button javaTab = createLangTab("Java", true);
        Button tsTab = createLangTab("TypeScript", false);
        Button jsonTab = createLangTab("JSON", false);

        javaTab.addClickListener(e -> {
            exportLanguage = "java";
            updateLangTabStyles(javaTab, tsTab, jsonTab);
            updateConfigPreview();
        });

        tsTab.addClickListener(e -> {
            exportLanguage = "typescript";
            updateLangTabStyles(tsTab, javaTab, jsonTab);
            updateConfigPreview();
        });

        jsonTab.addClickListener(e -> {
            exportLanguage = "json";
            updateLangTabStyles(jsonTab, javaTab, tsTab);
            updateConfigPreview();
        });

        langTabs.add(javaTab, tsTab, jsonTab);

        // 复制按钮 - 现代风格
        Button copyBtn = new Button("Copy", VaadinIcon.COPY.create());
        copyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        copyBtn.getStyle()
            .set("background", "linear-gradient(135deg, #10b981 0%, #34d399 100%)")
            .set("color", "white")
            .set("border", "none")
            .set("border-radius", "8px")
            .set("font-weight", "600")
            .set("padding", "8px 16px")
            .set("box-shadow", "0 2px 8px rgba(16, 185, 129, 0.3)");
        copyBtn.addClickListener(e -> {
            String code = switch (exportLanguage) {
                case "typescript" -> generateTypeScriptCode();
                case "json" -> generateJsonConfig();
                default -> generateJavaCode();
            };
            copyToClipboard(code);
            Notification.show("Copied to clipboard", 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        HorizontalLayout buttons = new HorizontalLayout(langTabs, copyBtn);
        buttons.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        buttons.getStyle().set("gap", "12px");
        header.add(titleSection, buttons);

        // 配置预览 - 现代代码编辑器风格
        Div previewWrapper = new Div();
        previewWrapper.setSizeFull();
        previewWrapper.getStyle()
            .set("padding", "16px 24px")
            .set("overflow", "auto")
            .set("box-sizing", "border-box")
            .set("background", "var(--app-bg-secondary, #f8fafc)");
        previewWrapper.addClassName("code-preview-wrapper");

        configPreviewContainer = new Div();
        configPreviewContainer.getStyle()
            .set("width", "100%")
            .set("max-width", "100%")
            .set("font-family", "'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace")
            .set("font-size", "13px")
            .set("background", "linear-gradient(135deg, #1e293b 0%, #0f172a 100%)")
            .set("color", "#e2e8f0")
            .set("border-radius", "16px")
            .set("padding", "20px")
            .set("overflow", "auto")
            .set("white-space", "pre-wrap")
            .set("line-height", "1.7")
            .set("border", "1px solid #334155")
            .set("box-shadow", "inset 0 2px 4px rgba(0, 0, 0, 0.2)")
            .set("box-sizing", "border-box");
        configPreviewContainer.addClassName("config-preview-code");

        updateConfigPreview(configPreviewContainer);

        previewWrapper.add(configPreviewContainer);

        panel.add(header, previewWrapper);
        panel.setFlexGrow(1, previewWrapper);

        return panel;
    }

    /**
     * 更新配置预览（无参数版本，使用成员变量）
     */
    private void updateConfigPreview() {
        if (configPreviewContainer != null) {
            updateConfigPreview(configPreviewContainer);
        }
    }

    private void updateConfigPreview(Div container) {
        container.removeAll();

        String code = switch (exportLanguage) {
            case "typescript" -> generateTypeScriptCode();
            case "json" -> generateJsonConfig();
            default -> generateJavaCode();
        };

        // 使用简单的语法高亮渲染
        renderCodePreview(container, code, exportLanguage);
    }

    /**
     * 渲染代码预览 - 简洁版本
     */
    private void renderCodePreview(Div container, String code, String language) {
        // 直接显示代码文本，让 CSS 处理样式
        container.setText(code);
    }

    private void showExportDialog(String title, String content) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("800px");
        dialog.setHeight("600px");
        dialog.getElement().getStyle()
            .set("--lumo-dialog-overlay-radius", "16px");

        TextArea textArea = new TextArea();
        textArea.setValue(content);
        textArea.setWidthFull();
        textArea.setHeight("400px");
        textArea.setReadOnly(true);
        textArea.getStyle()
            .set("font-family", "'SF Mono', 'Monaco', monospace")
            .set("font-size", "12px")
            .set("--vaadin-input-field-border-radius", "8px");

        Button copyBtn = new Button("Copy", VaadinIcon.COPY.create());
        copyBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        copyBtn.addClickListener(e -> {
            copyToClipboard(content);
            Notification.show("Copied", 1500, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        Button closeBtn = new Button("Close", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(textArea);
        dialog.getFooter().add(copyBtn, closeBtn);

        dialog.open();
    }

    private String generateJsonConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"preset\": \"").append(selectedPreset.name()).append("\",\n");
        sb.append("  \"editorType\": \"").append(selectedEditorType.name()).append("\",\n");
        sb.append("  \"language\": \"").append(selectedLanguage).append("\",\n");
        sb.append("  \"theme\": \"").append(selectedTheme.name()).append("\",\n");
        sb.append("  \"plugins\": [\n    ");
        sb.append(selectedPlugins.stream()
            .map(p -> "\"" + p.name() + "\"")
            .collect(Collectors.joining(",\n    ")));
        sb.append("\n  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private String generateJavaCode() {
        StringBuilder sb = new StringBuilder();
        sb.append("// VaadinCKEditor 5.0.0 Configuration Code\n");
        sb.append("// Generated: ").append(new java.util.Date()).append("\n\n");

        sb.append("import com.wontlost.ckeditor.*;\n\n");

        // 使用预设方式
        sb.append("// Option 1: Using Preset (Recommended)\n");
        sb.append("VaadinCKEditor editor = VaadinCKEditor.create()\n");
        sb.append("    .withPreset(CKEditorPreset.").append(selectedPreset.name()).append(")\n");
        sb.append("    .withType(CKEditorType.").append(selectedEditorType.name()).append(")\n");
        sb.append("    .withTheme(CKEditorTheme.").append(selectedTheme.name()).append(")\n");
        sb.append("    .withLanguage(\"").append(selectedLanguage).append("\")\n");
        sb.append("    .withWidth(\"100%\")\n");
        sb.append("    .withHeight(\"400px\")\n");
        sb.append("    .build();\n\n");

        // 自定义插件方式
        sb.append("// Option 2: Custom Plugins\n");
        sb.append("VaadinCKEditor customEditor = VaadinCKEditor.create()\n");
        sb.append("    .withPlugins(\n");
        List<String> pluginNames = selectedPlugins.stream()
            .map(p -> "        CKEditorPlugin." + p.name())
            .collect(Collectors.toList());
        sb.append(String.join(",\n", pluginNames));
        sb.append("\n    )\n");
        sb.append("    .withType(CKEditorType.").append(selectedEditorType.name()).append(")\n");
        sb.append("    .withTheme(CKEditorTheme.").append(selectedTheme.name()).append(")\n");
        sb.append("    .withLanguage(\"").append(selectedLanguage).append("\")\n");
        sb.append("    .withWidth(\"100%\")\n");
        sb.append("    .withHeight(\"400px\")\n");
        sb.append("    .build();\n");

        return sb.toString();
    }

    private void copyToClipboard(String text) {
        getElement().executeJs(
            "navigator.clipboard.writeText($0).catch(err => console.error('Copy failed:', err))",
            text
        );
    }

    /**
     * 创建语言选项卡按钮
     */
    private Button createLangTab(String label, boolean isActive) {
        Button tab = new Button(label);
        tab.addClassName("export-lang-tab");
        tab.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST);
        tab.getStyle()
            .set("border-radius", "8px")
            .set("padding", "6px 14px")
            .set("font-size", "13px")
            .set("font-weight", "500")
            .set("border", "none")
            .set("cursor", "pointer")
            .set("transition", "all 0.15s ease");

        if (isActive) {
            tab.addClassName("active");
            tab.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-primary-color)");
        } else {
            tab.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        }
        return tab;
    }

    /**
     * 更新语言选项卡样式
     */
    private void updateLangTabStyles(Button activeTab, Button... inactiveTabs) {
        activeTab.addClassName("active");
        activeTab.getStyle()
            .set("background", "var(--lumo-contrast-10pct)")
            .set("color", "var(--lumo-primary-color)");
        for (Button tab : inactiveTabs) {
            tab.removeClassName("active");
            tab.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        }
    }

    /**
     * 生成 TypeScript 代码
     */
    private String generateTypeScriptCode() {
        StringBuilder sb = new StringBuilder();
        sb.append("// VaadinCKEditor 5.0.0 TypeScript Configuration\n");
        sb.append("// Generated: ").append(new java.util.Date()).append("\n\n");

        sb.append("import { CKEditorPreset, CKEditorType, CKEditorTheme, CKEditorPlugin } from '@vaadin/ckeditor';\n\n");

        // 接口定义
        sb.append("interface EditorConfig {\n");
        sb.append("  preset: CKEditorPreset;\n");
        sb.append("  type: CKEditorType;\n");
        sb.append("  theme: CKEditorTheme;\n");
        sb.append("  language: string;\n");
        sb.append("  plugins: CKEditorPlugin[];\n");
        sb.append("}\n\n");

        // 配置对象
        sb.append("const editorConfig: EditorConfig = {\n");
        sb.append("  preset: CKEditorPreset.").append(selectedPreset.name()).append(",\n");
        sb.append("  type: CKEditorType.").append(selectedEditorType.name()).append(",\n");
        sb.append("  theme: CKEditorTheme.").append(selectedTheme.name()).append(",\n");
        sb.append("  language: '").append(selectedLanguage).append("',\n");
        sb.append("  plugins: [\n");

        List<String> pluginNames = selectedPlugins.stream()
            .map(p -> "    CKEditorPlugin." + p.name())
            .collect(Collectors.toList());
        sb.append(String.join(",\n", pluginNames));
        sb.append("\n  ]\n");
        sb.append("};\n\n");

        // 初始化函数
        sb.append("// Initialize editor\n");
        sb.append("export function initEditor(container: HTMLElement): void {\n");
        sb.append("  const editor = document.createElement('vaadin-ckeditor');\n");
        sb.append("  editor.setAttribute('preset', editorConfig.preset);\n");
        sb.append("  editor.setAttribute('editor-type', editorConfig.type);\n");
        sb.append("  editor.setAttribute('theme', editorConfig.theme);\n");
        sb.append("  editor.setAttribute('language', editorConfig.language);\n");
        sb.append("  container.appendChild(editor);\n");
        sb.append("}\n");

        return sb.toString();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Initialize theme sync on page load
        syncVaadinTheme(selectedTheme);
        refreshEditor();
    }

    // 标记是否正在刷新编辑器（防止重复刷新，volatile 保证跨线程可见性）
    private volatile boolean isRefreshingEditor = false;

    private void refreshEditor() {
        log.debug("refreshEditor() START");
        if (editorContainer == null) {
            log.debug("editorContainer is null, returning");
            return;
        }

        // 防止重复刷新
        if (isRefreshingEditor) {
            log.debug("already refreshing, returning");
            return;
        }
        isRefreshingEditor = true;

        // 更新配置预览
        updateConfigPreview();

        UI ui = UI.getCurrent();
        if (ui == null) {
            log.debug("UI is null, returning");
            isRefreshingEditor = false;
            return;
        }

        final Div oldWrapper = editorWrapper;
        final VaadinCKEditor oldEditor = currentEditor;
        log.debug("oldWrapper: {}, oldEditor: {}",
            oldWrapper != null ? oldWrapper.getId().orElse("no-id") : "null",
            oldEditor != null);

        // 清空当前引用，防止事件回调访问旧编辑器
        currentEditor = null;
        editorWrapper = null;

        // 隐藏旧编辑器（避免视觉闪烁）
        if (oldWrapper != null) {
            oldWrapper.getStyle().set("visibility", "hidden");
            oldWrapper.getStyle().set("position", "absolute");
            log.debug("oldWrapper hidden");
        }

        // 定义创建新编辑器的逻辑
        Runnable createNewEditor = () -> {
            log.debug("createNewEditor START");
            try {
                // 创建新的编辑器实例
                editorInstanceCount++;
                Div newWrapper = new Div();
                newWrapper.setId("editor-wrapper-" + editorInstanceCount);
                newWrapper.setSizeFull();
                log.debug("created new wrapper: editor-wrapper-{}", editorInstanceCount);

                // 添加新 wrapper 到容器
                editorWrapper = newWrapper;
                editorContainer.add(newWrapper);

                // 在新 wrapper 中创建编辑器
                createEditorInWrapper(newWrapper);
                log.debug("createNewEditor END");
            } finally {
                isRefreshingEditor = false;
            }
        };

        // 如果没有旧编辑器，直接创建新的
        if (oldEditor == null && oldWrapper == null) {
            log.debug("no old editor, creating directly");
            createNewEditor.run();
            return;
        }

        // 新策略：不显式调用 destroyEditor()
        // 直接从 DOM 移除组件，让 disconnectedCallback 安全处理清理
        // 使用 access() 确保在正确的 UI 线程中执行
        log.debug("scheduling DOM removal via ui.access()");
        ui.access(() -> {
            log.debug("ui.access() executing, removing old wrapper");
            // 从 DOM 移除旧 wrapper（这会触发 disconnectedCallback）
            if (oldWrapper != null) {
                editorContainer.remove(oldWrapper);
                log.debug("oldWrapper removed from DOM");
            }

            // 使用较短的延迟让浏览器完成清理
            // 延迟在客户端执行，避免阻塞服务器
            log.debug("scheduling client-side delay (50ms)");
            ui.getPage().executeJs(
                "console.log('[BuilderView JS] 50ms delay starting'); return new Promise(resolve => setTimeout(() => { console.log('[BuilderView JS] 50ms delay complete'); resolve(); }, 50))"
            ).then(ignored -> {
                log.debug("client-side delay complete, creating new editor");
                ui.access(createNewEditor::run);
            }, error -> {
                log.error("client-side delay failed, resetting refresh flag", error);
                isRefreshingEditor = false;
            });
        });
        log.debug("refreshEditor() END (async operations scheduled)");
    }

    /**
     * 在指定包装器中创建编辑器
     */
    private void createEditorInWrapper(Div wrapper) {
        log.debug("createEditorInWrapper START, wrapper: {}", wrapper != null ? wrapper.getId().orElse("no-id") : "null");
        if (wrapper == null) return;

        try {
            // 使用 Builder API 创建编辑器
            var builder = VaadinCKEditor.create()
                .withPreset(selectedPreset)
                .withType(selectedEditorType)
                .withTheme(selectedTheme)
                .withLanguage(selectedLanguage)
                .withWidth("100%")
                .withHeight("100%")
                .withValue("<p>Start typing here...</p><p>Use the configuration panel on the left to customize the editor's preset, type, and plugins.</p>");

            // 添加自定义插件（超出预设的部分）
            for (CKEditorPlugin plugin : selectedPlugins) {
                // LineHeight 需要使用本地自定义插件
                if (plugin == CKEditorPlugin.LINE_HEIGHT) {
                    builder.addCustomPlugin(CustomPlugin.builder("LineHeight")
                        .withImportPath("custom-line-height")
                        .withToolbarItems("lineHeight")
                        .build());
                } else if (!selectedPreset.hasPlugin(plugin)) {
                    builder.addPlugin(plugin);
                }
            }

            // 移除插件（预设中有但用户取消勾选的）
            for (CKEditorPlugin presetPlugin : selectedPreset.getPlugins()) {
                if (!selectedPlugins.contains(presetPlugin)) {
                    builder.removePlugin(presetPlugin);
                }
            }

            currentEditor = builder.build();
            currentEditor.setSizeFull();

            wrapper.add(currentEditor);
            log.debug("createEditorInWrapper END, editor added to wrapper");

        } catch (Exception e) {
            log.error("createEditorInWrapper ERROR: {}", e.getMessage(), e);
            showEditorError(e.getMessage());
        }
    }

    /**
     * 直接创建编辑器（用于首次加载）
     */
    private void createEditorDirectly() {
        if (editorContainer == null) return;

        // 创建初始包装器
        editorWrapper = new Div();
        editorWrapper.setId("editor-wrapper-" + editorInstanceCount);
        editorWrapper.setSizeFull();
        editorContainer.add(editorWrapper);

        createEditorInWrapper(editorWrapper);
    }

    private void showEditorError(String message) {
        Div error = new Div();
        error.getStyle()
            .set("color", "var(--lumo-error-color)")
            .set("padding", "var(--lumo-space-l)")
            .set("text-align", "center")
            .set("background", "var(--lumo-error-color-10pct)")
            .set("border-radius", "8px")
            .set("margin", "var(--lumo-space-m)");

        Icon errorIcon = VaadinIcon.WARNING.create();
        errorIcon.setSize("48px");
        errorIcon.getStyle()
            .set("color", "var(--lumo-error-color)")
            .set("margin-bottom", "var(--lumo-space-m)");

        H4 errorTitle = new H4("Editor Creation Failed");
        errorTitle.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

        Span errorMessage = new Span(message);
        errorMessage.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout errorContent = new VerticalLayout(errorIcon, errorTitle, errorMessage);
        errorContent.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);
        errorContent.setPadding(false);

        error.add(errorContent);
        editorContainer.add(error);
    }
}
