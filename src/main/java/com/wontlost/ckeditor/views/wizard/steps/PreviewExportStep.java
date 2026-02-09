package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.wontlost.ckeditor.CKEditorConfig;
import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorPreset;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.CustomPlugin;
import com.wontlost.ckeditor.VaadinCKEditor;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.domain.entity.SubscriptionSource;
import com.wontlost.ckeditor.generator.CodeGeneratorFactory;
import com.wontlost.ckeditor.i18n.I18nUtil;
import com.wontlost.ckeditor.service.SubscriberService;
import com.wontlost.ckeditor.views.components.SubscriptionDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 6: 预览与导出
 * 用户预览编辑器并导出代码
 */
public class PreviewExportStep implements WizardStep {

    // 需要特殊配置或容器的 Premium 插件（在普通预览中跳过）
    private static final java.util.Set<String> PREMIUM_PLUGINS_TO_SKIP = java.util.Set.of(
        "DocumentOutline",      // 需要容器
        "TableOfContents",      // 需要容器
        "AIAssistant",          // 需要 AI adapter 配置
        "Comments",             // 需要 comments 配置
        "TrackChanges",         // 需要协作配置
        "RevisionHistory",      // 需要协作配置
        "Pagination"            // 需要分页配置
    );

    // 需要跳过的标准插件（初始化问题）
    private static final java.util.Set<String> STANDARD_PLUGINS_TO_SKIP = java.util.Set.of(
        "Minimap"               // 需要编辑器 DOM 完全就绪
    );

    // 有冲突的插件对（互斥，不能同时启用）
    private static final java.util.Map<String, String> CONFLICTING_PLUGINS = java.util.Map.of(
        "RestrictedEditingMode", "StandardEditingMode",
        "StandardEditingMode", "RestrictedEditingMode"
    );

    private BuilderState state;
    private VerticalLayout content;
    private Div editorContainer;
    private Div codePreview;
    private VaadinCKEditor currentEditor;
    private Dialog previewDialog;
    private Div dialogEditorContainer;
    private VaadinCKEditor dialogEditor;
    private final Map<BuilderState.ExportLanguage, Button> langButtons = new HashMap<>();

    // 订阅按钮（匿名用户可见，已订阅用户隐藏）
    private Button subscribeBtn;

    // 订阅服务（延迟注入）
    private SubscriberService subscriberService;

    /**
     * 设置订阅服务（用于依赖注入）
     */
    public void setSubscriberService(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    @Override
    public String getId() { return "preview-export"; }

    @Override
    public String getTitle() { return I18nUtil.get("step7.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step7.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.CODE; }

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
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.addClassName("preview-export-step");

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(55);
        splitLayout.addClassName("preview-export-split");

        // 左侧：编辑器预览
        VerticalLayout previewPanel = createPreviewPanel();

        // 右侧：代码导出
        VerticalLayout exportPanel = createExportPanel();

        splitLayout.addToPrimary(previewPanel);
        splitLayout.addToSecondary(exportPanel);

        layout.add(splitLayout);

        return layout;
    }

    private VerticalLayout createPreviewPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setPadding(true);
        panel.addClassName("preview-panel");

        // 标题栏
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.addClassName("preview-header");

        HorizontalLayout titleSection = new HorizontalLayout();
        titleSection.setAlignItems(FlexComponent.Alignment.CENTER);

        Icon previewIcon = VaadinIcon.EYE.create();
        previewIcon.setSize("20px");
        previewIcon.addClassName("preview-icon");

        H3 title = new H3(I18nUtil.get("step7.preview"));
        title.addClassName("preview-title");

        titleSection.add(previewIcon, title);

        Button refreshBtn = new Button(I18nUtil.get("step7.refresh"), VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshBtn.addClickListener(e -> refreshPreview());

        header.add(titleSection, refreshBtn);

        // 配置摘要
        Div summaryBar = new Div();
        summaryBar.addClassName("config-summary-bar");
        summaryBar.setId("config-summary");

        // 编辑器容器
        editorContainer = new Div();
        editorContainer.setSizeFull();
        editorContainer.addClassName("editor-preview-container");

        panel.add(header, summaryBar, editorContainer);
        panel.setFlexGrow(1, editorContainer);

        return panel;
    }

    private VerticalLayout createExportPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setPadding(true);
        panel.addClassName("export-panel");

        // 标题栏
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.addClassName("export-header");

        HorizontalLayout titleSection = new HorizontalLayout();
        titleSection.setAlignItems(FlexComponent.Alignment.CENTER);

        Icon codeIcon = VaadinIcon.CODE.create();
        codeIcon.setSize("20px");
        codeIcon.addClassName("code-icon");

        H3 title = new H3(I18nUtil.get("step7.exportCode"));
        title.addClassName("export-title");

        titleSection.add(codeIcon, title);

        // 语言切换
        HorizontalLayout langTabs = new HorizontalLayout();
        langTabs.setSpacing(false);
        langTabs.addClassName("lang-tabs");

        for (BuilderState.ExportLanguage lang : BuilderState.ExportLanguage.values()) {
            Button btn = new Button(lang.getDisplayName());
            btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
            btn.addClassName("lang-tab");
            if (lang == BuilderState.ExportLanguage.JAVA) {
                btn.addClassName("active");
            }
            btn.addClickListener(e -> selectExportLanguage(lang));
            langButtons.put(lang, btn);
            langTabs.add(btn);
        }

        header.add(titleSection, langTabs);

        // 配置名称
        HorizontalLayout configNameRow = new HorizontalLayout();
        configNameRow.setWidthFull();
        configNameRow.setAlignItems(FlexComponent.Alignment.CENTER);
        configNameRow.addClassName("config-name-row");

        Span nameLabel = new Span(I18nUtil.get("step7.configName"));
        nameLabel.addClassName("config-name-label");

        TextField configNameField = new TextField();
        configNameField.setValue("my-editor-config");
        configNameField.addClassName("config-name-field");
        configNameField.addValueChangeListener(e -> {
            if (state != null) {
                state.setConfigName(e.getValue());
            }
        });

        configNameRow.add(nameLabel, configNameField);

        // 代码预览
        codePreview = new Div();
        codePreview.addClassName("code-preview");

        // 操作按钮
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.addClassName("export-actions");

        // 订阅按钮（匿名用户可见，已订阅用户隐藏）
        subscribeBtn = new Button(I18nUtil.get("subscribe.button.confirm"), VaadinIcon.ENVELOPE_O.create());
        subscribeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        subscribeBtn.addClickListener(e -> showSubscribeButtonDialog());
        subscribeBtn.setVisible(false); // 默认隐藏，onEnter 时根据 localStorage 决定

        // 右侧按钮组
        HorizontalLayout rightButtons = new HorizontalLayout();
        rightButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        rightButtons.setSpacing(true);

        Button copyBtn = new Button(I18nUtil.get("step7.copyCode"), VaadinIcon.COPY.create());
        copyBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        copyBtn.addClickListener(e -> copyCode());

        Button downloadBtn = new Button(I18nUtil.get("step7.downloadFile"), VaadinIcon.DOWNLOAD.create());
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        downloadBtn.addClickListener(e -> downloadCode());

        rightButtons.add(copyBtn, downloadBtn);

        actions.add(subscribeBtn, rightButtons);
        actions.setFlexGrow(1, rightButtons);

        panel.add(header, configNameRow, codePreview, actions);
        panel.setFlexGrow(1, codePreview);

        return panel;
    }

    private void selectExportLanguage(BuilderState.ExportLanguage lang) {
        if (state == null) return;

        state.setExportLanguage(lang);

        // 更新按钮状态
        langButtons.values().forEach(btn -> btn.getElement().getClassList().remove("active"));
        langButtons.get(lang).getElement().getClassList().add("active");

        // 更新代码预览
        updateCodePreview();
    }

    private void refreshPreview() {
        if (state == null || editorContainer == null) return;

        editorContainer.removeAll();
        currentEditor = null;

        // Document 预设使用缩略图 + 弹窗预览
        if (state.getPreset() == CKEditorPreset.DOCUMENT) {
            renderDocumentThumbnail();
            // 如果弹窗已打开，同步更新弹窗内容
            if (previewDialog != null && previewDialog.isOpened()) {
                updateDialogPreview();
            }
        } else {
            // 其他预设使用内嵌预览
            renderInlinePreview();
        }

        // 更新配置摘要
        updateConfigSummary();

        // 更新代码预览
        updateCodePreview();
    }

    /**
     * 渲染内嵌编辑器预览（非 Document 预设）
     */
    private void renderInlinePreview() {
        try {
            currentEditor = buildPreviewEditor();
            currentEditor.setWidthFull();
            currentEditor.setMinHeight("100%");
            editorContainer.add(currentEditor);
        } catch (Exception e) {
            Div errorMsg = new Div();
            errorMsg.addClassName("preview-error");
            errorMsg.setText(I18nUtil.get("step7.preview.error", e.getMessage()));
            editorContainer.add(errorMsg);
        }
    }

    /**
     * 构建预览编辑器
     */
    private VaadinCKEditor buildPreviewEditor() {
        var builder = VaadinCKEditor.create()
            .withPreset(state.getPreset())
            .withType(state.getEditorType())
            .withTheme(state.getTheme())
            .withLanguage(state.getLanguage())
            .withWidth("100%")
            /*.withHeight("100%")*/; //Auto height for better UX in preview

        // 设置 License Key（Premium 插件需要）
        if (state.hasLicenseKey()) {
            builder.withLicenseKey(state.getLicenseKey());
        }

        // 收集已添加的插件名称用于冲突检测
        java.util.Set<String> addedPlugins = new java.util.HashSet<>();

        // 添加用户选择的插件（跳过有问题的插件）
        for (CKEditorPlugin plugin : state.getSelectedPlugins()) {
            String pluginName = plugin.getJsName();
            // 跳过 LineHeight（模块导入问题）
            if (plugin == CKEditorPlugin.LINE_HEIGHT) {
                continue;
            }
            // 跳过需要特殊处理的标准插件
            if (STANDARD_PLUGINS_TO_SKIP.contains(pluginName)) {
                continue;
            }
            // 检查插件冲突
            String conflicting = CONFLICTING_PLUGINS.get(pluginName);
            if (conflicting != null && addedPlugins.contains(conflicting)) {
                // 跳过冲突的插件
                continue;
            }
            if (!state.getPreset().hasPlugin(plugin)) {
                builder.addPlugin(plugin);
                addedPlugins.add(pluginName);
            }
        }

        // 移除未选择的预设插件
        for (CKEditorPlugin presetPlugin : state.getPreset().getPlugins()) {
            if (!state.hasPlugin(presetPlugin)) {
                builder.removePlugin(presetPlugin);
            }
        }

        // 添加 Premium 插件（跳过需要特殊配置的插件）
        for (BuilderState.CustomPluginConfig premiumConfig : state.getPremiumPlugins()) {
            String pluginName = premiumConfig.getName();
            // 跳过需要特殊配置的插件
            if (PREMIUM_PLUGINS_TO_SKIP.contains(pluginName)) {
                continue;
            }
            // 检查冲突
            String conflicting = CONFLICTING_PLUGINS.get(pluginName);
            if (conflicting != null && addedPlugins.contains(conflicting)) {
                continue;
            }
            CustomPlugin.Builder pluginBuilder = CustomPlugin.builder(pluginName)
                .premium();
            // 添加工具栏项目
            if (!premiumConfig.getToolbarItems().isEmpty()) {
                pluginBuilder.withToolbarItems(premiumConfig.getToolbarItems().toArray(new String[0]));
            }
            builder.addCustomPlugin(pluginBuilder.build());
            addedPlugins.add(pluginName);
        }

        // 使用用户配置的工具栏项目（过滤掉 lineHeight）
        List<String> toolbarItems = new ArrayList<>(state.getToolbarItems());
        toolbarItems.removeIf(item -> "lineHeight".equals(item));

        // 添加 Premium 插件的工具栏项目（排除需要特殊配置的插件）
        for (BuilderState.CustomPluginConfig premiumConfig : state.getPremiumPlugins()) {
            if (PREMIUM_PLUGINS_TO_SKIP.contains(premiumConfig.getName())) {
                continue;
            }
            for (String toolbarItem : premiumConfig.getToolbarItems()) {
                if (!toolbarItems.contains(toolbarItem)) {
                    toolbarItems.add(toolbarItem);
                }
            }
        }

        // 创建配置对象
        CKEditorConfig config = new CKEditorConfig()
            .setLanguage(state.getLanguage())
            .setPlaceholder(I18nUtil.get("step7.preview.placeholder"));

        // 设置工具栏配置
        // 对于 Balloon 和 Inline 编辑器，使用折叠模式（shouldNotGroupWhenFull = false）
        // 这样当工具栏超出可用空间时，会自动折叠到三个点菜单中，避免溢出
        // 对于其他编辑器类型，使用多行模式（shouldNotGroupWhenFull = true）显示所有按钮
        if (!toolbarItems.isEmpty()) {
            boolean shouldNotGroup = state.getEditorType() != CKEditorType.BALLOON
                                  && state.getEditorType() != CKEditorType.INLINE;
            config.setToolbar(toolbarItems.toArray(new String[0]), shouldNotGroup);
        }

        // 配置图像工具栏（防止 widget-toolbar-no-items 警告）
        if (state.hasPlugin(CKEditorPlugin.IMAGE_TOOLBAR)) {
            List<String> imageToolbar = state.getImageToolbar();
            if (imageToolbar.isEmpty()) {
                // 使用默认的图像工具栏配置
                imageToolbar = List.of(
                    "imageTextAlternative",
                    "toggleImageCaption",
                    "|",
                    "imageStyle:inline",
                    "imageStyle:wrapText",
                    "imageStyle:breakText",
                    "|",
                    "resizeImage"
                );
            }
            // 使用 CKEditorConfig.setImage(toolbar, styles) 配置
            config.setImage(imageToolbar.toArray(new String[0]), new String[0]);
        }

        // 配置表格工具栏（防止类似警告）
        if (state.hasPlugin(CKEditorPlugin.TABLE_TOOLBAR)) {
            List<String> tableToolbar = state.getTableContentToolbar();
            if (tableToolbar.isEmpty()) {
                // 使用默认的表格工具栏配置
                tableToolbar = List.of(
                    "tableColumn",
                    "tableRow",
                    "mergeTableCells",
                    "|",
                    "tableProperties",
                    "tableCellProperties"
                );
            }
            // 使用 CKEditorConfig.setTable(contentToolbar) 配置
            config.setTable(tableToolbar.toArray(new String[0]));
        }

        builder.withConfig(config);

        VaadinCKEditor editor = builder.build();

        // 应用 Document 预设特定选项
        applyDocumentOptions(editor);

        // 应用自定义 CSS
        applyCustomCss(editor);

        return editor;
    }

    /**
     * 应用 Document 预设选项（Outline 和 Minimap）
     */
    private void applyDocumentOptions(VaadinCKEditor editor) {
        if (state == null || editor == null) return;

        // Document Outline（需要 Premium license）
        if (state.isDocumentOutlineEnabled()) {
            editor.getElement().setProperty("documentOutlineEnabled", true);
        }

        // Minimap
        if (state.isMinimapEnabled()) {
            editor.getElement().setProperty("minimapEnabled", true);
            editor.getElement().setProperty("allowConfigRequiredPlugins", true);
        }
    }

    /**
     * 应用自定义 CSS 到编辑器
     * CKEditor 使用 light DOM，CSS 直接注入到 document.head
     */
    private void applyCustomCss(VaadinCKEditor editor) {
        if (state == null || editor == null) return;

        String customCss = state.getCustomCss();
        if (customCss == null || customCss.isEmpty()) return;

        // 通过 JavaScript 注入 CSS 到 document.head
        editor.getElement().executeJs(
            """
            (function(el, css) {
                const injectCss = () => {
                    // 移除之前的自定义样式（避免重复注入）
                    const oldStyles = document.querySelectorAll('style[data-ckeditor-custom-css]');
                    oldStyles.forEach(s => s.remove());

                    // 创建 style 元素
                    const style = document.createElement('style');
                    style.setAttribute('data-ckeditor-custom-css', 'true');
                    style.textContent = css;
                    document.head.appendChild(style);
                };

                // 延迟执行确保编辑器已初始化
                const checkAndInject = () => {
                    if (el.querySelector('.ck-editor') || el.querySelector('.ck-toolbar')) {
                        injectCss();
                    } else {
                        setTimeout(checkAndInject, 200);
                    }
                };
                checkAndInject();
            })($0, $1)
            """,
            editor.getElement(),
            customCss
        );
    }

    /**
     * 渲染 Document 预设的缩略图预览（铺满容器）
     */
    private void renderDocumentThumbnail() {
        Div thumbnail = createDocumentThumbnail();
        editorContainer.add(thumbnail);
    }

    /**
     * 创建工具栏图标按钮
     */
    private Div createToolbarIconButton(VaadinIcon icon) {
        Div btn = new Div();
        btn.getStyle()
            .set("width", "28px")
            .set("height", "28px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("border-radius", "4px")
            .set("cursor", "default");
        Icon ic = icon.create();
        ic.setSize("16px");
        ic.getStyle().set("color", "var(--lumo-secondary-text-color)");
        btn.add(ic);
        return btn;
    }

    /**
     * 创建工具栏分隔符
     */
    private Div createToolbarSeparator() {
        Div separator = new Div();
        separator.getStyle()
            .set("width", "1px")
            .set("height", "20px")
            .set("background", "var(--lumo-contrast-20pct)")
            .set("margin", "0 6px");
        return separator;
    }

    /**
     * 创建 Document 预览缩略图（铺满父容器）
     */
    private Div createDocumentThumbnail() {
        Div thumbnail = new Div();
        thumbnail.addClassName("document-preview-thumbnail");
        thumbnail.getStyle()
            .set("width", "100%")
            .set("height", "100%")
            .set("min-height", "0")
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "12px")
            .set("background", "var(--lumo-base-color)")
            .set("cursor", "pointer")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("overflow", "hidden")
            .set("transition", "box-shadow 0.2s");

        // 模拟菜单栏
        Div menuBar = new Div();
        menuBar.getStyle()
            .set("height", "28px")
            .set("min-height", "28px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("padding", "0 12px")
            .set("gap", "16px");

        // Simulated menu items
        String[] menuKeys = {"file", "edit", "view", "insert", "format", "tools", "help"};
        for (String key : menuKeys) {
            Span menuItem = new Span(I18nUtil.get("step7.preview.menu." + key));
            menuItem.getStyle()
                .set("font-size", "11px")
                .set("color", "var(--lumo-secondary-text-color)");
            menuBar.add(menuItem);
        }

        // 模拟工具栏（使用真实图标）
        Div toolbar = new Div();
        toolbar.getStyle()
            .set("height", "36px")
            .set("min-height", "36px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("padding", "0 8px")
            .set("gap", "2px")
            .set("flex-wrap", "nowrap")
            .set("overflow", "hidden");

        // 第一组：撤销/重做
        toolbar.add(createToolbarIconButton(VaadinIcon.ARROW_BACKWARD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ARROW_FORWARD));
        toolbar.add(createToolbarSeparator());

        // 第二组：查找/全屏
        toolbar.add(createToolbarIconButton(VaadinIcon.SEARCH));
        toolbar.add(createToolbarIconButton(VaadinIcon.EXPAND_SQUARE));
        toolbar.add(createToolbarSeparator());

        // 第三组：文本格式
        toolbar.add(createToolbarIconButton(VaadinIcon.TEXT_LABEL));
        toolbar.add(createToolbarSeparator());
        toolbar.add(createToolbarIconButton(VaadinIcon.BOLD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ITALIC));
        toolbar.add(createToolbarIconButton(VaadinIcon.UNDERLINE));
        toolbar.add(createToolbarSeparator());

        // 第四组：插入
        toolbar.add(createToolbarIconButton(VaadinIcon.LINK));
        toolbar.add(createToolbarIconButton(VaadinIcon.PICTURE));
        toolbar.add(createToolbarIconButton(VaadinIcon.TABLE));
        toolbar.add(createToolbarSeparator());

        // 第五组：列表
        toolbar.add(createToolbarIconButton(VaadinIcon.LIST_UL));
        toolbar.add(createToolbarIconButton(VaadinIcon.LIST_OL));
        toolbar.add(createToolbarIconButton(VaadinIcon.CHECK_SQUARE_O));

        // 模拟内容区域（填充剩余高度）
        Div content = new Div();
        content.getStyle()
            .set("flex", "1")
            .set("min-height", "0")
            .set("padding", "16px")
            .set("display", "flex")
            .set("gap", "12px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("overflow", "hidden");

        // 左侧模拟大纲（如果启用）
        if (state.isDocumentOutlineEnabled()) {
            Div outline = new Div();
            outline.getStyle()
                .set("width", "120px")
                .set("min-width", "120px")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "4px")
                .set("padding", "12px")
                .set("border", "1px solid var(--lumo-contrast-10pct)");

            // Outline title
            Span outlineTitle = new Span(I18nUtil.get("step7.preview.documentOutline"));
            outlineTitle.getStyle()
                .set("font-size", "11px")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "8px");
            outline.add(outlineTitle);

            // 模拟大纲项
            for (int i = 0; i < 5; i++) {
                Div item = new Div();
                item.getStyle()
                    .set("height", "8px")
                    .set("margin-bottom", "6px")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("margin-left", i % 2 == 1 ? "12px" : "0")
                    .set("width", i % 2 == 1 ? "70%" : "90%");
                outline.add(item);
            }
            content.add(outline);
        }

        // 中间模拟文档内容（A4 样式）
        Div docWrapper = new Div();
        docWrapper.getStyle()
            .set("flex", "1")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("overflow", "hidden");

        Div docContent = new Div();
        docContent.getStyle()
            .set("width", "100%")
            .set("max-width", "280px")
            .set("background", "white")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("border-radius", "2px")
            .set("padding", "20px 16px")
            .set("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.08)");

        // 模拟标题
        Div titleLine = new Div();
        titleLine.getStyle()
            .set("height", "12px")
            .set("width", "70%")
            .set("margin-bottom", "12px")
            .set("border-radius", "2px")
            .set("background", "var(--lumo-contrast-20pct)");
        docContent.add(titleLine);

        // 模拟文本行
        for (int i = 0; i < 12; i++) {
            Div line = new Div();
            line.getStyle()
                .set("height", "6px")
                .set("margin-bottom", "6px")
                .set("border-radius", "2px")
                .set("background", "var(--lumo-contrast-10pct)");
            if (i == 3 || i == 7) {
                line.getStyle().set("width", "85%");
            } else if (i == 5) {
                line.getStyle()
                    .set("height", "10px")
                    .set("width", "50%")
                    .set("margin-top", "8px")
                    .set("background", "var(--lumo-contrast-15pct)");
            } else {
                line.getStyle().set("width", "100%");
            }
            docContent.add(line);
        }
        docWrapper.add(docContent);
        content.add(docWrapper);

        // 右侧模拟 Minimap（如果启用）
        if (state.isMinimapEnabled()) {
            Div minimap = new Div();
            minimap.getStyle()
                .set("width", "80px")
                .set("min-width", "80px")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "4px")
                .set("padding", "8px")
                .set("border", "1px solid var(--lumo-contrast-10pct)");

            // 模拟 minimap 内容
            Div minimapContent = new Div();
            minimapContent.getStyle()
                .set("width", "100%")
                .set("height", "120px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "2px")
                .set("position", "relative");

            // 模拟当前视口指示器
            Div viewport = new Div();
            viewport.getStyle()
                .set("position", "absolute")
                .set("top", "10px")
                .set("left", "0")
                .set("right", "0")
                .set("height", "30px")
                .set("background", "rgba(59, 130, 246, 0.2)")
                .set("border", "1px solid rgba(59, 130, 246, 0.5)")
                .set("border-radius", "2px");
            minimapContent.add(viewport);

            minimap.add(minimapContent);
            content.add(minimap);
        }

        // 点击提示覆盖层
        Div hintOverlay = new Div();
        hintOverlay.getStyle()
            .set("position", "absolute")
            .set("top", "0")
            .set("left", "0")
            .set("right", "0")
            .set("bottom", "0")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("background", "rgba(0, 0, 0, 0)")
            .set("transition", "background 0.2s");

        Div hintBox = new Div();
        hintBox.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("padding", "12px 20px")
            .set("border-radius", "8px")
            .set("box-shadow", "0 4px 12px rgba(0, 0, 0, 0.15)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "8px")
            .set("opacity", "0")
            .set("transform", "translateY(10px)")
            .set("transition", "all 0.2s");

        Icon expandIcon = VaadinIcon.EXPAND_FULL.create();
        expandIcon.setSize("18px");
        expandIcon.getStyle().set("color", "var(--lumo-primary-color)");

        Span hint = new Span(I18nUtil.get("step7.preview.clickToPreview"));
        hint.getStyle()
            .set("font-size", "14px")
            .set("font-weight", "500")
            .set("color", "var(--lumo-body-text-color)");

        hintBox.add(expandIcon, hint);
        hintOverlay.add(hintBox);

        // 使用相对定位的容器（flex 布局填充高度）
        Div container = new Div();
        container.getStyle()
            .set("width", "100%")
            .set("height", "100%")
            .set("min-height", "0")
            .set("position", "relative")
            .set("display", "flex")
            .set("flex-direction", "column");
        container.add(menuBar, toolbar, content, hintOverlay);

        thumbnail.add(container);
        thumbnail.addClickListener(e -> openDocumentPreviewDialog());

        // 悬停效果
        thumbnail.getElement().executeJs(
            "this.addEventListener('mouseenter', () => {" +
            "  this.style.boxShadow = '0 8px 24px rgba(0, 0, 0, 0.12)';" +
            "  this.querySelector('[style*=\"rgba(0, 0, 0, 0)\"]').style.background = 'rgba(0, 0, 0, 0.3)';" +
            "  this.querySelector('[style*=\"opacity: 0\"]').style.opacity = '1';" +
            "  this.querySelector('[style*=\"translateY(10px)\"]').style.transform = 'translateY(0)';" +
            "});" +
            "this.addEventListener('mouseleave', () => {" +
            "  this.style.boxShadow = 'none';" +
            "  const overlay = this.querySelector('[style*=\"background: rgba\"]');" +
            "  if (overlay) overlay.style.background = 'rgba(0, 0, 0, 0)';" +
            "  const box = this.querySelectorAll('div')[this.querySelectorAll('div').length - 2];" +
            "  if (box) { box.style.opacity = '0'; box.style.transform = 'translateY(10px)'; }" +
            "});"
        );

        return thumbnail;
    }

    /**
     * 打开 Document 预览弹窗
     */
    private void openDocumentPreviewDialog() {
        ensurePreviewDialog();
        previewDialog.open();

        // 应用弹窗样式（毛玻璃背景和标题居中）
        // 使用 setTimeout 确保 dialog 的 shadow DOM 完全渲染
        UI.getCurrent().getPage().executeJs(
            "setTimeout(() => {" +
            "  const dialog = document.querySelector('vaadin-dialog.document-preview-dialog');" +
            "  if (!dialog || !dialog.shadowRoot) return;" +
            "  const overlay = dialog.shadowRoot.querySelector('vaadin-dialog-overlay');" +
            "  if (!overlay || !overlay.shadowRoot) return;" +
            // 毛玻璃背景
            "  const backdrop = overlay.shadowRoot.querySelector('[part=\"backdrop\"]');" +
            "  if (backdrop) {" +
            "    backdrop.style.backdropFilter = 'blur(4px)';" +
            "    backdrop.style.webkitBackdropFilter = 'blur(4px)';" +
            "    backdrop.style.background = 'rgba(0, 0, 0, 0.5)';" +
            "  }" +
            // 标题居中
            "  const header = overlay.shadowRoot.querySelector('[part=\"header\"]');" +
            "  if (header) header.style.justifyContent = 'center';" +
            "  const title = overlay.shadowRoot.querySelector('[part=\"title\"]');" +
            "  if (title) { title.style.textAlign = 'center'; title.style.flex = '1'; }" +
            "}, 100);"
        );

        // 延迟创建编辑器，确保 dialog 容器完全渲染
        UI.getCurrent().getPage().executeJs(
            "return new Promise(resolve => setTimeout(resolve, 150));"
        ).then(result -> updateDialogPreview());
    }

    /**
     * 确保预览弹窗已创建（毛玻璃背景效果）
     */
    private void ensurePreviewDialog() {
        if (previewDialog != null) return;

        previewDialog = new Dialog();
        previewDialog.setWidth("95vw");
        previewDialog.setHeight("95vh");
        previewDialog.setCloseOnEsc(true);
        previewDialog.setCloseOnOutsideClick(true);
        previewDialog.addClassName("document-preview-dialog");
        previewDialog.setHeaderTitle(I18nUtil.get("step7.preview.dialogTitle"));

        // 关闭时清理编辑器资源
        previewDialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                cleanupDialogEditor();
            }
        });

        // 关闭按钮
        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> previewDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        previewDialog.getHeader().add(closeBtn);

        // 编辑器容器（包含 document-editor-container 样式）
        dialogEditorContainer = new Div();
        dialogEditorContainer.setSizeFull();
        dialogEditorContainer.addClassName("document-preview-dialog-content");
        dialogEditorContainer.addClassName("document-editor-container");
        dialogEditorContainer.getStyle()
            .set("padding", "16px")
            .set("box-sizing", "border-box")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("align-items", "flex-start");

        previewDialog.add(dialogEditorContainer);
    }

    /**
     * 清理弹窗中的编辑器（正确销毁以避免内存泄漏）
     */
    private void cleanupDialogEditor() {
        if (dialogEditor != null) {
            // 先调用 destroy 方法销毁 CKEditor 实例
            dialogEditor.getElement().executeJs(
                "if (this.editor && typeof this.editor.destroy === 'function') {" +
                "  this.editor.destroy().catch(err => console.warn('Editor destroy error:', err));" +
                "}"
            );
            dialogEditor = null;
        }
        if (dialogEditorContainer != null) {
            dialogEditorContainer.removeAll();
        }
    }

    /**
     * 更新弹窗内的编辑器预览（完整 Document Editor 配置）
     */
    private void updateDialogPreview() {
        if (dialogEditorContainer == null) return;

        // 先清理旧编辑器
        cleanupDialogEditor();

        try {
            dialogEditor = buildDocumentEditor();
            dialogEditor.setSizeFull();
            dialogEditor.addClassName("document-editor");
            dialogEditorContainer.add(dialogEditor);
        } catch (Exception e) {
            Div errorMsg = new Div();
            errorMsg.addClassName("preview-error");
            errorMsg.setText(I18nUtil.get("step7.preview.error", e.getMessage()));
            dialogEditorContainer.add(errorMsg);
        }
    }

    /**
     * 构建完整的 Document Editor（参考 DocumentEditorView 实现）
     */
    private VaadinCKEditor buildDocumentEditor() {
        var builder = VaadinCKEditor.create()
            // Document 预设强制使用 DECOUPLED 类型
            .withType(CKEditorType.DECOUPLED)
            .withPreset(CKEditorPreset.DOCUMENT)
            .withTheme(state.getTheme())
            .withLanguage(state.getLanguage())
            .withWidth("100%")
            .withHeight("100%")
            .withValue("<h2>Document Editor Preview</h2>" +
                "<p>This is a preview of your configured Document Editor.</p>" +
                "<h3>Features enabled:</h3>" +
                "<ul>" +
                "<li>Document Outline: " + (state.isDocumentOutlineEnabled() ? "Enabled" : "Disabled") + "</li>" +
                "<li>Minimap: " + (state.isMinimapEnabled() ? "Enabled" : "Disabled") + "</li>" +
                "</ul>" +
                "<p>Try adding headings to see the Document Outline update, " +
                "and scroll to see the Minimap in action.</p>" +
                "<h3>Section 1</h3>" +
                "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>" +
                "<h3>Section 2</h3>" +
                "<p>Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris " +
                "nisi ut aliquip ex ea commodo consequat.</p>" +
                "<h3>Section 3</h3>" +
                "<p>Duis aute irure dolor in reprehenderit in voluptate velit esse " +
                "cillum dolore eu fugiat nulla pariatur.</p>");

        // 设置 License Key（Premium 插件需要）
        if (state.hasLicenseKey()) {
            builder.withLicenseKey(state.getLicenseKey());
        }

        // 添加基础插件
        builder.addPlugin(CKEditorPlugin.AUTOFORMAT)
            .addPlugin(CKEditorPlugin.TEXT_TRANSFORMATION)
            .addPlugin(CKEditorPlugin.FULLSCREEN);

        // 如果启用 Minimap，添加 Minimap 插件
        if (state.isMinimapEnabled()) {
            builder.addPlugin(CKEditorPlugin.MINIMAP);
        }

        // 添加 Premium 插件（如果有 license key）
        if (state.hasLicenseKey() && state.isDocumentOutlineEnabled()) {
            builder.addCustomPlugin(CustomPlugin.fromPremium("DocumentOutline"));
        }

        // 设置工具栏
        builder.withToolbar(new String[] {
            "undo", "redo", "|",
            "findAndReplace", "fullscreen", "|",
            "heading", "|",
            "fontSize", "fontFamily", "fontColor", "fontBackgroundColor", "|",
            "bold", "italic", "underline", "strikethrough",
            "subscript", "superscript", "removeFormat", "|",
            "link", "insertImage", "insertTable", "|",
            "alignment", "|",
            "bulletedList", "numberedList", "todoList",
            "outdent", "indent"
        });

        VaadinCKEditor editor = builder.build();

        // 应用 Document 选项属性
        if (state.isDocumentOutlineEnabled() && state.hasLicenseKey()) {
            editor.getElement().setProperty("documentOutlineEnabled", true);
        }

        if (state.isMinimapEnabled()) {
            editor.getElement().setProperty("minimapEnabled", true);
            editor.getElement().setProperty("allowConfigRequiredPlugins", true);
            editor.getElement().setProperty("minimapSimplePreview", false);
        }

        // 应用自定义 CSS
        applyCustomCss(editor);

        return editor;
    }

    private void updateConfigSummary() {
        if (state == null) return;

        // Calculate premium plugins count
        int premiumCount = state.getPremiumPlugins().size();
        String premiumInfo = premiumCount > 0
            ? String.format(" <span class='summary-premium'>%s</span>",
                I18nUtil.get("step7.summary.premium", premiumCount))
            : "";

        String summary = String.format(
            "<span class='summary-item'><strong>%s</strong> %s</span>" +
            "<span class='summary-item'><strong>%s</strong> %s</span>" +
            "<span class='summary-item'><strong>%s</strong> %s%s</span>" +
            "<span class='summary-item'><strong>%s</strong> %s</span>" +
            "<span class='summary-item'><strong>%s</strong> %s</span>",
            I18nUtil.get("step7.summary.type"),
            state.getEditorType().name(),
            I18nUtil.get("step7.summary.preset"),
            state.getPreset().getDisplayName(),
            I18nUtil.get("step7.summary.plugins"),
            I18nUtil.get("step7.summary.pluginCount", state.getSelectedPlugins().size()),
            premiumInfo,
            I18nUtil.get("step7.summary.theme"),
            state.getTheme().name(),
            I18nUtil.get("step7.summary.language"),
            state.getLanguage()
        );

        content.getElement().executeJs(
            "var el = document.getElementById('config-summary');" +
            "if (el) { el.innerHTML = $0; }",
            summary
        );
    }

    private void updateCodePreview() {
        if (state == null || codePreview == null) return;

        String code = generateCode();
        codePreview.removeAll();

        Div codeBlock = new Div();
        codeBlock.addClassName("code-block");
        codeBlock.setText(code);

        codePreview.add(codeBlock);
    }

    private String generateCode() {
        if (state == null) return "";
        return CodeGeneratorFactory.generateCode(state);
    }

    private void copyCode() {
        checkSubscriptionAndExecute(SubscriptionSource.COPY_CODE, () -> {
            String code = generateCode();
            UI.getCurrent().getPage().executeJs(
                "navigator.clipboard.writeText($0).then(() => { console.log('Copied'); })",
                code
            );
            Notification.show(I18nUtil.get("step7.notification.copied"), 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
    }

    private void downloadCode() {
        checkSubscriptionAndExecute(SubscriptionSource.DOWNLOAD_FILE, () -> {
            String code = generateCode();
            String extension = CodeGeneratorFactory.getFileExtension(state.getExportLanguage());
            String filename = state.getConfigName() + "." + extension;

            // 使用 JavaScript 下载
            UI.getCurrent().getPage().executeJs(
                "var blob = new Blob([$0], {type: 'text/plain'});" +
                "var url = URL.createObjectURL(blob);" +
                "var a = document.createElement('a');" +
                "a.href = url;" +
                "a.download = $1;" +
                "a.click();" +
                "URL.revokeObjectURL(url);",
                code, filename
            );

            Notification.show(I18nUtil.get("step7.notification.downloadStarted"), 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
    }

    /**
     * 检查订阅状态，未订阅则显示订阅对话框
     * @param source 订阅来源
     * @param action 执行的操作
     */
    private void checkSubscriptionAndExecute(SubscriptionSource source, Runnable action) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            action.run();
            return;
        }

        // 检查 localStorage 中是否已订阅或已跳过
        ui.getPage().executeJs(
            "return JSON.stringify({" +
            "  subscribed: localStorage.getItem('ckeditor-builder-subscribed')," +
            "  skipped: localStorage.getItem('ckeditor-builder-subscription-skipped')," +
            "  email: localStorage.getItem('ckeditor-builder-email')," +
            "  anonymousId: localStorage.getItem('ckeditor-builder-anonymous-id')" +
            "})"
        ).then(String.class, result -> {
            if (result == null || result.isEmpty()) {
                showSubscriptionDialog(source, action);
                return;
            }
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = mapper.readTree(result);
                String subscribed = node.has("subscribed") && !node.get("subscribed").isNull()
                    ? node.get("subscribed").asText() : null;
                String skipped = node.has("skipped") && !node.get("skipped").isNull()
                    ? node.get("skipped").asText() : null;
                String email = node.has("email") && !node.get("email").isNull()
                    ? node.get("email").asText() : null;
                String anonymousId = node.has("anonymousId") && !node.get("anonymousId").isNull()
                    ? node.get("anonymousId").asText() : null;

                if ("true".equals(subscribed) && email != null && !email.isEmpty()) {
                    // 已订阅，记录活动
                    action.run();
                    if (subscriberService != null) {
                        subscriberService.recordActivity(email, source);
                    }
                } else if ("true".equals(skipped)) {
                    // 已跳过，更新匿名记录计数
                    action.run();
                    if (subscriberService != null && anonymousId != null && !anonymousId.isEmpty()) {
                        subscriberService.createOrUpdateAnonymous(anonymousId, source, state);
                    }
                } else {
                    showSubscriptionDialog(source, action);
                }
            } catch (Exception e) {
                showSubscriptionDialog(source, action);
            }
        });
    }

    /**
     * 显示订阅邀请对话框
     */
    private void showSubscriptionDialog(SubscriptionSource source, Runnable action) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            action.run();
            return;
        }

        SubscriptionDialog dialog = new SubscriptionDialog(
            // 订阅回调
            email -> {
                // 保存到数据库
                if (subscriberService != null) {
                    subscriberService.subscribe(email, source, state);
                }
                // 保存到 localStorage，并读取匿名ID用于合并
                ui.getPage().executeJs(
                    "var anonId = localStorage.getItem('ckeditor-builder-anonymous-id');" +
                    "localStorage.setItem('ckeditor-builder-subscribed', 'true');" +
                    "localStorage.setItem('ckeditor-builder-email', $0);" +
                    "localStorage.removeItem('ckeditor-builder-anonymous-id');" +
                    "localStorage.removeItem('ckeditor-builder-subscription-skipped');" +
                    "return anonId;",
                    email
                ).then(String.class, anonymousId -> {
                    // 合并匿名数据到真实订阅记录
                    if (subscriberService != null && anonymousId != null && !anonymousId.isEmpty()) {
                        subscriberService.mergeAnonymousToSubscriber(anonymousId, email);
                    }
                });
                // 显示感谢通知
                Notification.show(I18nUtil.get("subscribe.success"), 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                // 隐藏订阅按钮
                if (subscribeBtn != null) {
                    subscribeBtn.setVisible(false);
                }
                // 执行原操作
                action.run();
            },
            // 跳过回调
            () -> {
                // 生成匿名ID并存入 localStorage，同时创建服务端记录
                String anonymousId = "anon-" + java.util.UUID.randomUUID() + "@anonymous.local";
                ui.getPage().executeJs(
                    "localStorage.setItem('ckeditor-builder-subscription-skipped', 'true');" +
                    "localStorage.setItem('ckeditor-builder-anonymous-id', $0);",
                    anonymousId
                );
                if (subscriberService != null) {
                    subscriberService.createOrUpdateAnonymous(anonymousId, source, state);
                }
                // 直接执行操作
                action.run();
            }
        );
        dialog.open();
    }

    /**
     * 订阅按钮点击：弹出订阅对话框（无跳过回调）
     */
    private void showSubscribeButtonDialog() {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        SubscriptionDialog dialog = new SubscriptionDialog(
            // 订阅回调
            email -> {
                if (subscriberService != null) {
                    subscriberService.subscribe(email, SubscriptionSource.MANUAL, state);
                }
                // 更新 localStorage，合并匿名数据
                ui.getPage().executeJs(
                    "var anonId = localStorage.getItem('ckeditor-builder-anonymous-id');" +
                    "localStorage.setItem('ckeditor-builder-subscribed', 'true');" +
                    "localStorage.setItem('ckeditor-builder-email', $0);" +
                    "localStorage.removeItem('ckeditor-builder-anonymous-id');" +
                    "localStorage.removeItem('ckeditor-builder-subscription-skipped');" +
                    "return anonId;",
                    email
                ).then(String.class, anonymousId -> {
                    if (subscriberService != null && anonymousId != null && !anonymousId.isEmpty()) {
                        subscriberService.mergeAnonymousToSubscriber(anonymousId, email);
                    }
                });
                Notification.show(I18nUtil.get("subscribe.success"), 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                // 隐藏订阅按钮
                subscribeBtn.setVisible(false);
            },
            // 跳过回调（关闭对话框即可）
            () -> { }
        );
        dialog.open();
    }

    /**
     * 根据 localStorage 订阅状态更新订阅按钮可见性
     */
    private void updateSubscribeButtonVisibility() {
        UI ui = UI.getCurrent();
        if (ui == null || subscribeBtn == null) return;

        ui.getPage().executeJs(
            "return localStorage.getItem('ckeditor-builder-subscribed')"
        ).then(String.class, subscribed -> {
            subscribeBtn.setVisible(!"true".equals(subscribed));
        });
    }

    private void exportJsonConfig() {
        // 临时切换到 JSON 导出
        BuilderState.ExportLanguage original = state.getExportLanguage();
        state.setExportLanguage(BuilderState.ExportLanguage.JSON);

        String json = generateCode();
        String filename = state.getConfigName() + "-config.json";

        UI.getCurrent().getPage().executeJs(
            "var blob = new Blob([$0], {type: 'application/json'});" +
            "var url = URL.createObjectURL(blob);" +
            "var a = document.createElement('a');" +
            "a.href = url;" +
            "a.download = $1;" +
            "a.click();" +
            "URL.revokeObjectURL(url);",
            json, filename
        );

        // 恢复原来的导出语言
        state.setExportLanguage(original);

        Notification.show(I18nUtil.get("step7.notification.configExported"), 2000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;

        // 初始化 UI 状态
        if (langButtons.containsKey(state.getExportLanguage())) {
            selectExportLanguage(state.getExportLanguage());
        }

        // 刷新预览
        refreshPreview();

        // 更新订阅按钮可见性
        updateSubscribeButtonVisibility();
    }

    @Override
    public void onExit(BuilderState state) {
        // 清理内嵌编辑器
        if (currentEditor != null) {
            currentEditor.getElement().executeJs(
                "if (this.editor && typeof this.editor.destroy === 'function') {" +
                "  this.editor.destroy().catch(err => console.warn('Editor destroy error:', err));" +
                "}"
            );
            editorContainer.removeAll();
            currentEditor = null;
        }

        // 清理弹窗资源
        if (previewDialog != null && previewDialog.isOpened()) {
            previewDialog.close();
        }
        cleanupDialogEditor();
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        // 最后一步，无需验证
        return ValidationResult.ok();
    }
}
