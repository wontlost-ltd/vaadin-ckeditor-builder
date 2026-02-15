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
import com.vaadin.flow.component.html.IFrame;
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
import static com.wontlost.ckeditor.JsonUtil.createObjectNode;
import static com.wontlost.ckeditor.JsonUtil.createArrayNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
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
    private Dialog collaborativePreviewDialog;
    private Dialog aiDocumentPreviewDialog;
    private Dialog emailPreviewDialog;
    private Dialog notionPreviewDialog;
    private Div dialogEditorContainer;        // Document 弹窗
    private Div aiDocDialogEditorContainer;   // AI Document 弹窗
    private Div aiDocDialogLoadingOverlay;    // AI Document 弹窗加载指示器
    private Div emailDialogEditorContainer;   // Email 弹窗
    // Notion 弹窗使用 iframe 模式（与 Collaborative 相同），无需 editorContainer
    private VaadinCKEditor dialogEditor;
    private final Map<BuilderState.ExportLanguage, Button> langButtons = new HashMap<>();

    // 订阅按钮（匿名用户可见，已订阅用户隐藏）
    private Button subscribeBtn;

    // 订阅服务（延迟注入）
    private SubscriberService subscriberService;

    // AI 配置（延迟注入）
    private com.wontlost.ckeditor.config.AIProperties aiProperties;

    // 协作配置（延迟注入，AI 插件需要 cloudServices.tokenUrl）
    private com.wontlost.ckeditor.config.CollaborationProperties collaborationProperties;

    /**
     * 设置订阅服务（用于依赖注入）
     */
    public void setSubscriberService(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    /**
     * 设置 AI 配置（用于依赖注入）
     */
    public void setAiProperties(com.wontlost.ckeditor.config.AIProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * 设置协作配置（AI 插件需要 cloudServices.tokenUrl）
     */
    public void setCollaborationProperties(com.wontlost.ckeditor.config.CollaborationProperties collaborationProperties) {
        this.collaborationProperties = collaborationProperties;
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
        } else if (state.getPreset() == CKEditorPreset.COLLABORATIVE) {
            // Collaborative 预设使用缩略图 + iframe 弹窗
            renderCollaborativeThumbnail();
        } else if (state.getPreset() == CKEditorPreset.AI_DOCUMENT) {
            // AI Document 预设使用缩略图 + 弹窗预览
            renderAIDocumentThumbnail();
        } else if (state.getPreset() == CKEditorPreset.EMAIL) {
            // Email 预设使用缩略图 + 弹窗预览
            renderEmailThumbnail();
        } else if (state.getPreset() == CKEditorPreset.NOTION) {
            // Notion 预设使用缩略图 + 弹窗预览
            renderNotionThumbnail();
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
     * 渲染 Collaborative 预设的缩略图预览（带协作视觉元素）
     */
    private void renderCollaborativeThumbnail() {
        Div thumbnail = createCollaborativeThumbnail();
        editorContainer.add(thumbnail);
    }

    /**
     * 渲染 AI Document 预设的缩略图预览（点击打开弹窗）
     */
    private void renderAIDocumentThumbnail() {
        Div thumbnail = createAIDocumentThumbnail();
        editorContainer.add(thumbnail);
    }

    /**
     * 渲染 Email 预设的缩略图预览（点击打开弹窗）
     */
    private void renderEmailThumbnail() {
        Div thumbnail = createEmailThumbnail();
        editorContainer.add(thumbnail);
    }

    /**
     * 构建 Email 编辑器
     * 基于 CLASSIC 编辑器类型，启用 EMAIL 预设 + Email premium 插件
     */
    private VaadinCKEditor buildEmailEditor() {
        var builder = VaadinCKEditor.create()
            .withType(CKEditorType.CLASSIC)
            .withPreset(CKEditorPreset.EMAIL)
            .withTheme(state.getTheme())
            .withLanguage(state.getLanguage())
            .withWidth("795px")
            .withValue(getEmailSampleContent());

        // License Key（Email premium 插件需要）
        if (state.hasLicenseKey()) {
            builder.withLicenseKey(state.getLicenseKey());
        }

        // Email premium 插件
        builder.addCustomPlugin(CustomPlugin.fromPremium("EmailConfigurationHelper"))
            .addCustomPlugin(CustomPlugin.fromPremium("ExportInlineStyles"))
            .addCustomPlugin(CustomPlugin.fromPremium("SourceEditingEnhanced"))
            .addCustomPlugin(CustomPlugin.fromPremium("MergeFields"))
            .addCustomPlugin(CustomPlugin.fromPremium("Template"))
            .addCustomPlugin(CustomPlugin.fromPremium("PasteFromOfficeEnhanced"));

        // 工具栏
        builder.withToolbar(new String[] {
            "undo", "redo", "|",
            "insertMergeField", "previewMergeFields", "|",
            "sourceEditingEnhanced", "|",
            "heading", "style", "|",
            "fontSize", "fontFamily", "fontColor", "fontBackgroundColor", "|",
            "bold", "italic", "underline", "|",
            "link", "insertImage", "insertTable", "insertTableLayout", "|",
            "alignment", "|",
            "bulletedList", "numberedList", "outdent", "indent"
        });

        // 配置
        CKEditorConfig config = new CKEditorConfig();
        config.setLanguage(state.getLanguage());
        config.setPlaceholder(I18nUtil.get("step7.preview.placeholder"));

        // menuBar 可见
        ObjectNode menuBarNode = createObjectNode();
        menuBarNode.put("isVisible", true);
        config.set("menuBar", menuBarNode);

        // Merge Fields 定义（分组 + 数据集 + 预览模式）
        config.set("mergeFields", buildMergeFieldsConfig());

        builder.withConfig(config);

        VaadinCKEditor editor = builder.build();

        // 自定义 CSS
        applyCustomCss(editor);

        return editor;
    }

    /**
     * 构建 Merge Fields 配置
     * 定义字段分组、默认值和预览数据集，来源：CKEditor 5 官方 Email 预设
     */
    private ObjectNode buildMergeFieldsConfig() {
        ObjectNode mergeFields = createObjectNode();

        // --- definitions ---
        ArrayNode definitions = createArrayNode();

        // 订阅者信息组
        ObjectNode subscriberGroup = createObjectNode();
        subscriberGroup.put("groupId", "subscriber");
        subscriberGroup.put("groupLabel", "Subscriber");
        ArrayNode subscriberDefs = createArrayNode();
        subscriberDefs.add(mergeFieldDef("subscriberName", "Subscriber name", "Subscriber"));
        subscriberDefs.add(mergeFieldDef("subscriberEmail", "Email address", "user@example.com"));
        subscriberGroup.set("definitions", subscriberDefs);
        definitions.add(subscriberGroup);

        // 网站信息组
        ObjectNode siteGroup = createObjectNode();
        siteGroup.put("groupId", "site");
        siteGroup.put("groupLabel", "Site info");
        ArrayNode siteDefs = createArrayNode();
        siteDefs.add(mergeFieldDef("siteName", "Site name", "wontlost.com"));
        siteDefs.add(mergeFieldDef("siteUrl", "Site URL", "https://wontlost.com"));
        siteGroup.set("definitions", siteDefs);
        definitions.add(siteGroup);

        mergeFields.set("definitions", definitions);

        // --- dataSets（预览数据）---
        ArrayNode dataSets = createArrayNode();
        ObjectNode sampleData = createObjectNode();
        sampleData.put("id", "sample1");
        sampleData.put("label", "Sample subscriber");
        ObjectNode values = createObjectNode();
        values.put("subscriberName", "John Doe");
        values.put("subscriberEmail", "john.doe@example.com");
        values.put("siteName", "wontlost.com");
        values.put("siteUrl", "https://wontlost.com");
        sampleData.set("values", values);
        dataSets.add(sampleData);
        mergeFields.set("dataSets", dataSets);

        // --- previewModes ---
        ArrayNode previewModes = createArrayNode();
        previewModes.add("$labels");
        previewModes.add("$defaultValues");
        previewModes.add("$dataSets");
        mergeFields.set("previewModes", previewModes);

        // --- prefix / suffix ---
        mergeFields.put("prefix", "{{");
        mergeFields.put("suffix", "}}");

        return mergeFields;
    }

    /**
     * 创建单个 Merge Field 定义
     */
    private ObjectNode mergeFieldDef(String id, String label, String defaultValue) {
        ObjectNode def = createObjectNode();
        def.put("id", id);
        def.put("label", label);
        def.put("defaultValue", defaultValue);
        return def;
    }

    /**
     * Email 预设示例内容（wontlost.com 订阅感谢邮件）
     */
    private String getEmailSampleContent() {
        return "<table class=\"table layout-table\" role=\"presentation\"><tbody>" +
            // 间距
            "<tr><td style=\"height:30px;\">&nbsp;</td></tr>" +
            // Thank You GIF
            "<tr><td style=\"text-align:center;\">" +
            "<a href=\"https://wontlost.com\">" +
            "<img class=\"image_resized\" style=\"aspect-ratio:498/373;width:60%;\" " +
            "src=\"https://i0.wp.com/bestgrafix.com/wp-content/uploads/2025/07/Lovely-Thank-You-gif.gif\" " +
            "width=\"498\" height=\"373\"></a></td></tr>" +
            "<tr><td style=\"height:30px;\">&nbsp;</td></tr>" +
            // 标题
            "<tr><td style=\"text-align:center;\">" +
            "<h1 style=\"border-bottom-style:none;color:#333333;font-size:32px;margin:0;padding:0;\">" +
            "<strong>Welcome to {{siteName}}!</strong></h1></td></tr>" +
            "<tr><td style=\"height:20px;\">&nbsp;</td></tr>" +
            // 问候语（含 Merge Field）
            "<tr><td style=\"text-align:center;\">" +
            "<h2 style=\"border-bottom-style:none;color:#555555;font-size:22px;font-weight:400;margin:0;padding:0;\">" +
            "Hi {{subscriberName}},</h2></td></tr>" +
            "<tr><td style=\"height:10px;\">&nbsp;</td></tr>" +
            // 正文
            "<tr><td style=\"text-align:center;padding:0 40px;\">" +
            "<p style=\"color:#555555;margin:0;line-height:1.6;\">Thank you for subscribing! " +
            "You'll now receive updates about our latest Vaadin components, CKEditor integrations, " +
            "and open-source projects.</p></td></tr>" +
            "<tr><td style=\"height:30px;\">&nbsp;</td></tr>" +
            // CTA 按钮
            "<tr><td style=\"text-align:center;\">" +
            "<a class=\"button button--green\" href=\"https://wontlost.com\">Visit {{siteName}}</a></td></tr>" +
            "<tr><td style=\"height:40px;\">&nbsp;</td></tr>" +
            // 深色页脚
            "<tr><td style=\"background-color:#1a1a2e;height:20px;\">&nbsp;</td></tr>" +
            "<tr><td style=\"background-color:#1a1a2e;padding:12px;text-align:center;\">" +
            "<p style=\"color:#cccccc;margin:0;font-size:13px;\">You're receiving this because {{subscriberEmail}} " +
            "subscribed to {{siteName}}.</p>" +
            "<p style=\"color:#cccccc;margin:4px 0 0;font-size:13px;\">" +
            "<a style=\"color:#8888ff;\" href=\"#\">Unsubscribe</a> | " +
            "<a style=\"color:#8888ff;\" href=\"{{siteUrl}}\">{{siteName}}</a></p></td></tr>" +
            "<tr><td style=\"background-color:#1a1a2e;height:20px;\">&nbsp;</td></tr>" +
            "</tbody></table>";
    }

    /**
     * 渲染 Notion 预设的缩略图预览
     * Notion 需要 BalloonEditor + blockToolbar + 协作插件，使用缩略图展示
     */
    private void renderNotionThumbnail() {
        Div thumbnail = createNotionThumbnail();
        editorContainer.add(thumbnail);
    }

    /**
     * 创建 Notion-like 预览缩略图（点击打开弹窗）
     * 参照 CKEditor 5 官方 Notion-like 预设：无顶部菜单/工具栏，
     * 有 blockToolbar（六点按钮）在块左侧，有协作侧栏
     */
    private Div createNotionThumbnail() {
        Div thumbnail = new Div();
        thumbnail.addClassName("notion-preview-thumbnail");
        thumbnail.getStyle()
            .set("position", "absolute")
            .set("top", "0")
            .set("left", "0")
            .set("right", "0")
            .set("bottom", "0")
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

        // 顶部窄条：在线用户头像（无菜单、无工具栏）
        Div presenceBar = new Div();
        presenceBar.getStyle()
            .set("height", "32px")
            .set("min-height", "32px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "flex-end")
            .set("padding", "0 12px")
            .set("gap", "4px");

        // 用户头像
        String[] avatarColors = {"#4CAF50", "#2196F3"};
        String[] avatarLabels = {"A", "B"};
        for (int i = 0; i < 2; i++) {
            Div avatar = new Div();
            avatar.getStyle()
                .set("width", "22px")
                .set("height", "22px")
                .set("border-radius", "50%")
                .set("background", avatarColors[i])
                .set("color", "white")
                .set("font-size", "11px")
                .set("font-weight", "600")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center");
            avatar.setText(avatarLabels[i]);
            presenceBar.add(avatar);
        }

        // 主内容区域（文档 + 侧栏）
        Div mainArea = new Div();
        mainArea.getStyle()
            .set("flex", "1")
            .set("min-height", "0")
            .set("display", "flex")
            .set("overflow", "hidden")
            .set("background", "var(--lumo-contrast-5pct)");

        // 文档内容区（带 blockToolbar 六点按钮）
        Div content = new Div();
        content.getStyle()
            .set("flex", "1")
            .set("min-height", "0")
            .set("padding", "20px 40px 20px 20px")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "3px")
            .set("overflow", "hidden")
            .set("background", "var(--lumo-base-color)")
            .set("margin", "12px")
            .set("border-radius", "4px")
            .set("box-shadow", "0 1px 4px rgba(0, 0, 0, 0.06)");

        // Title 区域（大标题）
        Div titleLine = new Div();
        titleLine.getStyle()
            .set("height", "20px")
            .set("width", "55%")
            .set("margin-bottom", "12px")
            .set("margin-left", "28px")
            .set("border-radius", "3px")
            .set("background", "var(--lumo-contrast-25pct)");
        content.add(titleLine);

        // 模拟块级内容（每行左侧有六点 blockToolbar 按钮）
        int[] blockTypes = {0, 1, 1, 2, 1, 3, 1, 4, 5, 1};
        // 0=H2, 1=paragraph, 2=image, 3=H3, 4=todoList, 5=blockquote
        int[] widths = {50, 95, 80, 100, 90, 35, 85, 0, 0, 70};

        for (int i = 0; i < blockTypes.length; i++) {
            Div blockRow = new Div();
            blockRow.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "6px")
                .set("padding", "1px 0");

            // 六点 blockToolbar 按钮（仅 hover 行显示）
            Div dragHandle = new Div();
            dragHandle.getStyle()
                .set("width", "20px")
                .set("min-width", "20px")
                .set("height", "20px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "14px")
                .set("color", "var(--lumo-contrast-30pct)")
                .set("opacity", i == 1 ? "1" : "0.15");
            dragHandle.setText("\u2630"); // 三线汉堡图标模拟六点
            blockRow.add(dragHandle);

            int type = blockTypes[i];
            if (type == 0) {
                // H2 标题
                Div h2 = new Div();
                h2.getStyle()
                    .set("height", "12px")
                    .set("width", widths[i] + "%")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-20pct)");
                blockRow.add(h2);
            } else if (type == 1) {
                // 普通段落
                Div para = new Div();
                para.getStyle()
                    .set("height", "5px")
                    .set("width", widths[i] + "%")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)");
                blockRow.add(para);
            } else if (type == 2) {
                // 图片占位
                Div img = new Div();
                img.getStyle()
                    .set("height", "50px")
                    .set("width", "100%")
                    .set("border-radius", "4px")
                    .set("background", "var(--lumo-contrast-8pct)")
                    .set("border", "1px solid var(--lumo-contrast-10pct)")
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("justify-content", "center");
                Icon imgIcon = VaadinIcon.PICTURE.create();
                imgIcon.setSize("16px");
                imgIcon.getStyle().set("color", "var(--lumo-contrast-20pct)");
                img.add(imgIcon);
                blockRow.add(img);
            } else if (type == 3) {
                // H3 标题
                Div h3 = new Div();
                h3.getStyle()
                    .set("height", "10px")
                    .set("width", widths[i] + "%")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-18pct)");
                blockRow.add(h3);
            } else if (type == 4) {
                // Todo list
                Div todoRow = new Div();
                todoRow.getStyle()
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("gap", "5px");
                Div checkbox = new Div();
                checkbox.getStyle()
                    .set("width", "12px")
                    .set("height", "12px")
                    .set("min-width", "12px")
                    .set("border", "2px solid var(--lumo-contrast-30pct)")
                    .set("border-radius", "3px");
                Div todoText = new Div();
                todoText.getStyle()
                    .set("height", "5px")
                    .set("width", "100px")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)");
                todoRow.add(checkbox, todoText);
                blockRow.add(todoRow);
            } else if (type == 5) {
                // Blockquote
                Div quote = new Div();
                quote.getStyle()
                    .set("height", "20px")
                    .set("width", "80%")
                    .set("border-left", "3px solid var(--lumo-contrast-20pct)")
                    .set("padding-left", "8px")
                    .set("display", "flex")
                    .set("align-items", "center");
                Div quoteText = new Div();
                quoteText.getStyle()
                    .set("height", "5px")
                    .set("width", "90%")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)");
                quote.add(quoteText);
                blockRow.add(quote);
            }

            content.add(blockRow);
        }
        mainArea.add(content);

        // 右侧协作侧栏
        Div sidebar = new Div();
        sidebar.getStyle()
            .set("width", "130px")
            .set("min-width", "130px")
            .set("padding", "16px 8px")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "8px");

        // 模拟协作评论
        String[] commentColors = {"#4CAF50", "#2196F3"};
        String[] commentAuthors = {"Alice", "Bob"};
        for (int i = 0; i < 2; i++) {
            Div commentBox = new Div();
            commentBox.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "6px")
                .set("padding", "8px")
                .set("border-left", "3px solid " + commentColors[i]);

            Span author = new Span(commentAuthors[i]);
            author.getStyle()
                .set("font-size", "9px")
                .set("font-weight", "600")
                .set("color", commentColors[i])
                .set("display", "block")
                .set("margin-bottom", "4px");
            commentBox.add(author);

            for (int j = 0; j < 2; j++) {
                Div textLine = new Div();
                textLine.getStyle()
                    .set("height", "4px")
                    .set("margin-bottom", "3px")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("width", (j == 0 ? 80 : 60) + "%");
                commentBox.add(textLine);
            }
            sidebar.add(commentBox);
        }
        mainArea.add(sidebar);

        // Premium 标签
        Div premiumBanner = new Div();
        premiumBanner.getStyle()
            .set("height", "32px")
            .set("min-height", "32px")
            .set("background", "linear-gradient(135deg, var(--lumo-contrast-5pct), var(--lumo-contrast-10pct))")
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("gap", "6px");

        Span premiumLabel = new Span("Premium — Requires CKEditor License for collaboration features");
        premiumLabel.getStyle()
            .set("font-size", "10px")
            .set("color", "var(--lumo-secondary-text-color)");
        premiumBanner.add(premiumLabel);

        // 点击提示覆盖层
        Div hintOverlay = createClickHintOverlay(I18nUtil.get("step7.preview.notionClickToPreview"));

        // 使用相对定位的容器
        Div container = new Div();
        container.getStyle()
            .set("width", "100%")
            .set("height", "100%")
            .set("min-height", "0")
            .set("position", "relative")
            .set("display", "flex")
            .set("flex-direction", "column");
        container.add(presenceBar, mainArea, premiumBanner, hintOverlay);

        thumbnail.add(container);
        thumbnail.addClickListener(e -> openNotionPreviewDialog());
        applyThumbnailHoverEffect(thumbnail);

        return thumbnail;
    }

    /**
     * 创建 AI Document 预览缩略图（DECOUPLED 编辑器 + AI 侧栏）
     */
    private Div createAIDocumentThumbnail() {
        Div thumbnail = new Div();
        thumbnail.addClassName("ai-document-preview-thumbnail");
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
        Div menuBar = createThumbnailMenuBar();

        // 模拟工具栏（AI 特有：toggleAi、aiQuickActions）
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

        toolbar.add(createToolbarIconButton(VaadinIcon.ARROW_BACKWARD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ARROW_FORWARD));
        toolbar.add(createToolbarSeparator());

        // AI 按钮（紫色高亮）
        Div aiBtn = new Div();
        aiBtn.getStyle()
            .set("width", "28px")
            .set("height", "28px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("border-radius", "4px")
            .set("background", "rgba(147, 51, 234, 0.1)");
        Icon aiIcon = VaadinIcon.MAGIC.create();
        aiIcon.setSize("16px");
        aiIcon.getStyle().set("color", "#9333ea");
        aiBtn.add(aiIcon);
        toolbar.add(aiBtn);
        toolbar.add(createToolbarSeparator());

        toolbar.add(createToolbarIconButton(VaadinIcon.SEARCH));
        toolbar.add(createToolbarIconButton(VaadinIcon.EXPAND_SQUARE));
        toolbar.add(createToolbarSeparator());
        toolbar.add(createToolbarIconButton(VaadinIcon.BOLD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ITALIC));
        toolbar.add(createToolbarIconButton(VaadinIcon.UNDERLINE));
        toolbar.add(createToolbarSeparator());
        toolbar.add(createToolbarIconButton(VaadinIcon.LINK));
        toolbar.add(createToolbarIconButton(VaadinIcon.PICTURE));
        toolbar.add(createToolbarIconButton(VaadinIcon.TABLE));

        // 三栏布局：大纲 | 文档 | AI 侧栏
        Div content = new Div();
        content.getStyle()
            .set("flex", "1")
            .set("min-height", "0")
            .set("padding", "16px")
            .set("display", "flex")
            .set("gap", "12px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("overflow", "hidden");

        // 左侧：文档大纲
        if (state.isDocumentOutlineEnabled()) {
            Div outline = new Div();
            outline.getStyle()
                .set("width", "100px")
                .set("min-width", "100px")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "4px")
                .set("padding", "10px")
                .set("border", "1px solid var(--lumo-contrast-10pct)");
            Span outlineTitle = new Span(I18nUtil.get("step7.preview.documentOutline"));
            outlineTitle.getStyle()
                .set("font-size", "10px")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "8px");
            outline.add(outlineTitle);
            for (int i = 0; i < 4; i++) {
                Div item = new Div();
                item.getStyle()
                    .set("height", "7px")
                    .set("margin-bottom", "5px")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("margin-left", i % 2 == 1 ? "10px" : "0")
                    .set("width", i % 2 == 1 ? "65%" : "85%");
                outline.add(item);
            }
            content.add(outline);
        }

        // 中间：文档内容（A4 样式）
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
        Div titleLine = new Div();
        titleLine.getStyle()
            .set("height", "12px").set("width", "70%")
            .set("margin-bottom", "12px").set("border-radius", "2px")
            .set("background", "var(--lumo-contrast-20pct)");
        docContent.add(titleLine);
        for (int i = 0; i < 10; i++) {
            Div line = new Div();
            line.getStyle()
                .set("height", "6px").set("margin-bottom", "6px")
                .set("border-radius", "2px").set("background", "var(--lumo-contrast-10pct)");
            if (i == 3) line.getStyle().set("width", "85%");
            else if (i == 5) line.getStyle().set("height", "10px").set("width", "50%")
                .set("margin-top", "8px").set("background", "var(--lumo-contrast-15pct)");
            else line.getStyle().set("width", "100%");
            docContent.add(line);
        }
        docWrapper.add(docContent);
        content.add(docWrapper);

        // 右侧：AI Chat 侧栏
        Div aiSidebar = new Div();
        aiSidebar.getStyle()
            .set("width", "140px")
            .set("min-width", "140px")
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "4px")
            .set("padding", "10px")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "8px");

        // AI Chat 标题
        Div aiHeader = new Div();
        aiHeader.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "6px")
            .set("padding-bottom", "8px")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        Icon aiHeaderIcon = VaadinIcon.MAGIC.create();
        aiHeaderIcon.setSize("14px");
        aiHeaderIcon.getStyle().set("color", "#9333ea");
        Span aiTitle = new Span("AI Chat");
        aiTitle.getStyle()
            .set("font-size", "11px")
            .set("font-weight", "600")
            .set("color", "var(--lumo-secondary-text-color)");
        aiHeader.add(aiHeaderIcon, aiTitle);
        aiSidebar.add(aiHeader);

        // 模拟 AI 对话气泡
        for (int i = 0; i < 3; i++) {
            Div bubble = new Div();
            bubble.getStyle()
                .set("padding", "6px 8px")
                .set("border-radius", "6px")
                .set("background", i % 2 == 0
                    ? "var(--lumo-contrast-5pct)"
                    : "rgba(147, 51, 234, 0.08)");
            if (i % 2 == 1) {
                bubble.getStyle().set("align-self", "flex-end").set("max-width", "90%");
            }
            for (int j = 0; j < 2; j++) {
                Div textLine = new Div();
                textLine.getStyle()
                    .set("height", "4px").set("margin-bottom", "3px")
                    .set("border-radius", "2px")
                    .set("background", i % 2 == 0
                        ? "var(--lumo-contrast-10pct)"
                        : "rgba(147, 51, 234, 0.15)")
                    .set("width", (j == 0 ? 85 : 60) + "%");
                bubble.add(textLine);
            }
            aiSidebar.add(bubble);
        }

        // 模拟输入框
        Div inputBox = new Div();
        inputBox.getStyle()
            .set("margin-top", "auto")
            .set("height", "24px")
            .set("border", "1px solid var(--lumo-contrast-15pct)")
            .set("border-radius", "4px")
            .set("background", "var(--lumo-contrast-5pct)");
        aiSidebar.add(inputBox);

        content.add(aiSidebar);

        // 点击提示覆盖层
        Div hintOverlay = createClickHintOverlay(I18nUtil.get("step7.preview.aiDocumentClickToPreview"));

        Div container = new Div();
        container.getStyle()
            .set("width", "100%").set("height", "100%")
            .set("min-height", "0").set("position", "relative")
            .set("display", "flex").set("flex-direction", "column");
        container.add(menuBar, toolbar, content, hintOverlay);

        thumbnail.add(container);
        thumbnail.addClickListener(e -> openAIDocumentPreviewDialog());
        applyThumbnailHoverEffect(thumbnail);

        return thumbnail;
    }

    /**
     * 创建 Email 预览缩略图（CLASSIC 编辑器 + Email 特有功能）
     */
    private Div createEmailThumbnail() {
        Div thumbnail = new Div();
        thumbnail.addClassName("email-preview-thumbnail");
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
        Div menuBar = createThumbnailMenuBar();

        // 模拟工具栏（Email 特有：mergeField、sourceEditingEnhanced）
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

        toolbar.add(createToolbarIconButton(VaadinIcon.ARROW_BACKWARD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ARROW_FORWARD));
        toolbar.add(createToolbarSeparator());

        // Merge Fields 按钮（蓝色高亮）
        Div mergeBtn = new Div();
        mergeBtn.getStyle()
            .set("height", "22px")
            .set("padding", "0 8px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("border-radius", "4px")
            .set("background", "rgba(59, 130, 246, 0.1)")
            .set("gap", "4px");
        Span mergeLabel = new Span("Merge");
        mergeLabel.getStyle()
            .set("font-size", "10px")
            .set("color", "#3b82f6")
            .set("font-weight", "600");
        mergeBtn.add(mergeLabel);
        toolbar.add(mergeBtn);
        toolbar.add(createToolbarSeparator());

        // Source editing 按钮
        toolbar.add(createToolbarIconButton(VaadinIcon.CODE));
        toolbar.add(createToolbarSeparator());

        toolbar.add(createToolbarIconButton(VaadinIcon.TEXT_LABEL));
        toolbar.add(createToolbarSeparator());
        toolbar.add(createToolbarIconButton(VaadinIcon.BOLD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ITALIC));
        toolbar.add(createToolbarIconButton(VaadinIcon.UNDERLINE));
        toolbar.add(createToolbarSeparator());
        toolbar.add(createToolbarIconButton(VaadinIcon.LINK));
        toolbar.add(createToolbarIconButton(VaadinIcon.PICTURE));
        toolbar.add(createToolbarIconButton(VaadinIcon.TABLE));

        // 模拟邮件内容区域
        Div content = new Div();
        content.getStyle()
            .set("flex", "1")
            .set("min-height", "0")
            .set("padding", "24px 40px")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "8px")
            .set("overflow", "hidden");

        // 邮件标题
        Div emailTitle = new Div();
        emailTitle.getStyle()
            .set("height", "14px")
            .set("width", "50%")
            .set("margin-bottom", "8px")
            .set("border-radius", "3px")
            .set("background", "var(--lumo-contrast-25pct)");
        content.add(emailTitle);

        // 模拟邮件段落
        for (int i = 0; i < 3; i++) {
            Div line = new Div();
            line.getStyle()
                .set("height", "6px")
                .set("border-radius", "2px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("width", (90 - i * 5) + "%");
            content.add(line);
        }

        // 模拟 Merge Field 占位符（{{name}} 样式）
        Div mergeFieldRow = new Div();
        mergeFieldRow.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "6px")
            .set("margin", "4px 0");
        Div textBefore = new Div();
        textBefore.getStyle()
            .set("height", "6px").set("width", "60px")
            .set("border-radius", "2px").set("background", "var(--lumo-contrast-10pct)");
        Div mergeField = new Div();
        mergeField.getStyle()
            .set("height", "18px")
            .set("padding", "0 8px")
            .set("border-radius", "4px")
            .set("background", "rgba(59, 130, 246, 0.1)")
            .set("border", "1px solid rgba(59, 130, 246, 0.3)")
            .set("display", "flex")
            .set("align-items", "center");
        Span mergeText = new Span("{{name}}");
        mergeText.getStyle()
            .set("font-size", "9px")
            .set("color", "#3b82f6")
            .set("font-family", "monospace");
        mergeField.add(mergeText);
        Div textAfter = new Div();
        textAfter.getStyle()
            .set("height", "6px").set("width", "100px")
            .set("border-radius", "2px").set("background", "var(--lumo-contrast-10pct)");
        mergeFieldRow.add(textBefore, mergeField, textAfter);
        content.add(mergeFieldRow);

        // 模拟表格布局
        Div tableArea = new Div();
        tableArea.getStyle()
            .set("margin-top", "8px")
            .set("border", "1px solid var(--lumo-contrast-15pct)")
            .set("border-radius", "4px")
            .set("padding", "8px")
            .set("display", "grid")
            .set("grid-template-columns", "1fr 1fr")
            .set("gap", "4px");
        for (int i = 0; i < 4; i++) {
            Div cell = new Div();
            cell.getStyle()
                .set("height", "12px")
                .set("border-radius", "2px")
                .set("background", i < 2
                    ? "var(--lumo-contrast-8pct)"
                    : "var(--lumo-contrast-5pct)");
            tableArea.add(cell);
        }
        content.add(tableArea);

        // 更多段落行
        for (int i = 0; i < 2; i++) {
            Div line = new Div();
            line.getStyle()
                .set("height", "6px").set("border-radius", "2px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("width", (85 + i * 5) + "%");
            content.add(line);
        }

        // Premium 标签
        Div premiumBanner = new Div();
        premiumBanner.getStyle()
            .set("height", "32px")
            .set("min-height", "32px")
            .set("background", "linear-gradient(135deg, var(--lumo-contrast-5pct), var(--lumo-contrast-10pct))")
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("gap", "6px");
        Span premiumLabel = new Span("Premium — Merge Fields, Source Editing Enhanced, Inline Styles");
        premiumLabel.getStyle()
            .set("font-size", "10px")
            .set("color", "var(--lumo-secondary-text-color)");
        premiumBanner.add(premiumLabel);

        // 点击提示覆盖层
        Div hintOverlay = createClickHintOverlay(I18nUtil.get("step7.preview.emailClickToPreview"));

        Div container = new Div();
        container.getStyle()
            .set("width", "100%").set("height", "100%")
            .set("min-height", "0").set("position", "relative")
            .set("display", "flex").set("flex-direction", "column");
        container.add(menuBar, toolbar, content, premiumBanner, hintOverlay);

        thumbnail.add(container);
        thumbnail.addClickListener(e -> openEmailPreviewDialog());
        applyThumbnailHoverEffect(thumbnail);

        return thumbnail;
    }

    /**
     * 创建缩略图菜单栏（共享组件）
     */
    private Div createThumbnailMenuBar() {
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
        String[] menuKeys = {"file", "edit", "view", "insert", "format", "tools", "help"};
        for (String key : menuKeys) {
            Span menuItem = new Span(I18nUtil.get("step7.preview.menu." + key));
            menuItem.getStyle()
                .set("font-size", "11px")
                .set("color", "var(--lumo-secondary-text-color)");
            menuBar.add(menuItem);
        }
        return menuBar;
    }

    /**
     * 创建点击提示覆盖层（共享组件）
     */
    private Div createClickHintOverlay(String hintText) {
        Div hintOverlay = new Div();
        hintOverlay.getStyle()
            .set("position", "absolute")
            .set("top", "0").set("left", "0").set("right", "0").set("bottom", "0")
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

        Span hint = new Span(hintText);
        hint.getStyle()
            .set("font-size", "14px")
            .set("font-weight", "500")
            .set("color", "var(--lumo-body-text-color)");

        hintBox.add(expandIcon, hint);
        hintOverlay.add(hintBox);
        return hintOverlay;
    }

    /**
     * 应用缩略图悬停效果（共享逻辑）
     */
    private void applyThumbnailHoverEffect(Div thumbnail) {
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
    }

    /**
     * 打开 AI Document 预览弹窗
     */
    private void openAIDocumentPreviewDialog() {
        ensureAIDocumentPreviewDialog();
        aiDocumentPreviewDialog.open();
        applyDialogGlassEffect("ai-document-preview-dialog");

        // 延迟创建编辑器
        UI.getCurrent().getPage().executeJs(
            "return new Promise(resolve => setTimeout(resolve, 150));"
        ).then(result -> updateAIDocumentDialogPreview());
    }

    /**
     * 确保 AI Document 预览弹窗已创建
     */
    private void ensureAIDocumentPreviewDialog() {
        if (aiDocumentPreviewDialog != null) return;

        aiDocumentPreviewDialog = new Dialog();
        aiDocumentPreviewDialog.setWidth("95vw");
        aiDocumentPreviewDialog.setHeight("95vh");
        aiDocumentPreviewDialog.setCloseOnEsc(true);
        aiDocumentPreviewDialog.setCloseOnOutsideClick(true);
        aiDocumentPreviewDialog.addClassName("ai-document-preview-dialog");
        aiDocumentPreviewDialog.setHeaderTitle(I18nUtil.get("step7.preview.aiDocumentDialogTitle"));

        aiDocumentPreviewDialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) cleanupDialogEditor(aiDocDialogEditorContainer);
        });

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> aiDocumentPreviewDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        aiDocumentPreviewDialog.getHeader().add(closeBtn);

        // 加载指示器 — 编辑器初始化完成前显示
        aiDocDialogLoadingOverlay = createDialogLoadingOverlay(
            I18nUtil.get("step7.preview.aiDocumentLoading"));

        aiDocDialogEditorContainer = new Div();
        aiDocDialogEditorContainer.setSizeFull();
        aiDocDialogEditorContainer.addClassName("ai-document-dialog-content");

        aiDocDialogEditorContainer.add(aiDocDialogLoadingOverlay);
        aiDocumentPreviewDialog.add(aiDocDialogEditorContainer);

        // 注入 @keyframes spin 动画规则（仅弹窗作用域内需要）
        aiDocumentPreviewDialog.getElement().executeJs(
            "if (!document.getElementById('ck-dialog-spinner-keyframes')) {" +
            "  const style = document.createElement('style');" +
            "  style.id = 'ck-dialog-spinner-keyframes';" +
            "  style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';" +
            "  document.head.appendChild(style);" +
            "}"
        );
    }

    /**
     * 更新 AI Document 弹窗内的编辑器
     */
    private void updateAIDocumentDialogPreview() {
        if (aiDocDialogEditorContainer == null) return;
        cleanupDialogEditor(aiDocDialogEditorContainer);

        // 显示加载指示器
        if (aiDocDialogLoadingOverlay != null) {
            aiDocDialogLoadingOverlay.getStyle().set("display", "flex");
            aiDocDialogEditorContainer.add(aiDocDialogLoadingOverlay);
        }

        try {
            dialogEditor = buildAIDocumentEditor();
            dialogEditor.setSizeFull();
            dialogEditor.addClassName("document-editor");
            dialogEditor.getStyle().set("visibility", "hidden");

            // 编辑器就绪后隐藏加载指示器、显示编辑器
            dialogEditor.addEditorReadyListener(ev -> {
                if (aiDocDialogLoadingOverlay != null) {
                    aiDocDialogLoadingOverlay.getStyle().set("display", "none");
                }
                dialogEditor.getStyle().remove("visibility");
            });

            aiDocDialogEditorContainer.add(dialogEditor);
        } catch (Exception e) {
            if (aiDocDialogLoadingOverlay != null) {
                aiDocDialogLoadingOverlay.getStyle().set("display", "none");
            }
            Div errorMsg = new Div();
            errorMsg.addClassName("preview-error");
            errorMsg.setText(I18nUtil.get("step7.preview.error", e.getMessage()));
            aiDocDialogEditorContainer.add(errorMsg);
        }
    }

    /**
     * 打开 Email 预览弹窗
     */
    private void openEmailPreviewDialog() {
        ensureEmailPreviewDialog();
        emailPreviewDialog.open();
        applyDialogGlassEffect("email-preview-dialog");

        UI.getCurrent().getPage().executeJs(
            "return new Promise(resolve => setTimeout(resolve, 150));"
        ).then(result -> updateEmailDialogPreview());
    }

    /**
     * 确保 Email 预览弹窗已创建
     */
    private void ensureEmailPreviewDialog() {
        if (emailPreviewDialog != null) return;

        emailPreviewDialog = new Dialog();
        emailPreviewDialog.setWidth("95vw");
        emailPreviewDialog.setHeight("95vh");
        emailPreviewDialog.setCloseOnEsc(true);
        emailPreviewDialog.setCloseOnOutsideClick(true);
        emailPreviewDialog.addClassName("email-preview-dialog");
        emailPreviewDialog.setHeaderTitle(I18nUtil.get("step7.preview.emailDialogTitle"));

        emailPreviewDialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) cleanupDialogEditor(emailDialogEditorContainer);
        });

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> emailPreviewDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        emailPreviewDialog.getHeader().add(closeBtn);

        emailDialogEditorContainer = new Div();
        emailDialogEditorContainer.setSizeFull();
        emailDialogEditorContainer.addClassName("document-preview-dialog-content");
        emailDialogEditorContainer.getStyle()
            .set("padding", "16px")
            .set("box-sizing", "border-box")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("align-items", "flex-start");

        emailPreviewDialog.add(emailDialogEditorContainer);
    }

    /**
     * 更新 Email 弹窗内的编辑器
     */
    private void updateEmailDialogPreview() {
        if (emailDialogEditorContainer == null) return;
        cleanupDialogEditor(emailDialogEditorContainer);

        try {
            dialogEditor = buildEmailEditor();
            // 保持 795px 宽度，高度填满弹窗
            dialogEditor.setHeight("100%");
            emailDialogEditorContainer.add(dialogEditor);
        } catch (Exception e) {
            Div errorMsg = new Div();
            errorMsg.addClassName("preview-error");
            errorMsg.setText(I18nUtil.get("step7.preview.error", e.getMessage()));
            emailDialogEditorContainer.add(errorMsg);
        }
    }

    /**
     * 打开 Notion-like 预览弹窗（iframe 加载 /notion-document-editor，需要登录）
     * Notion 预设使用 BALLOON 编辑器 + blockToolbar + balloonToolbar，无固定顶部工具栏和菜单栏
     */
    private void openNotionPreviewDialog() {
        ensureNotionPreviewDialog();
        notionPreviewDialog.open();
        applyDialogGlassEffect("notion-preview-dialog");
    }

    /**
     * 确保 Notion 预览弹窗已创建（iframe 加载独立的 /notion-document-editor 页面）
     */
    private void ensureNotionPreviewDialog() {
        if (notionPreviewDialog != null) return;

        notionPreviewDialog = new Dialog();
        notionPreviewDialog.setWidth("95vw");
        notionPreviewDialog.setHeight("95vh");
        notionPreviewDialog.setCloseOnEsc(true);
        notionPreviewDialog.setCloseOnOutsideClick(true);
        notionPreviewDialog.addClassName("notion-preview-dialog");
        notionPreviewDialog.setHeaderTitle(I18nUtil.get("step7.preview.notionDialogTitle"));

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> notionPreviewDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        notionPreviewDialog.getHeader().add(closeBtn);

        // iframe 加载 Notion 风格编辑器页面（BALLOON + blockToolbar，需要登录）
        IFrame iframe = new IFrame("/notion-document-editor");
        iframe.setSizeFull();
        iframe.getElement().getStyle()
            .set("border", "none")
            .set("border-radius", "8px");

        Div iframeContainer = new Div(iframe);
        iframeContainer.setSizeFull();
        iframeContainer.getStyle()
            .set("padding", "0")
            .set("box-sizing", "border-box");

        notionPreviewDialog.add(iframeContainer);
    }

    /**
     * 应用弹窗毛玻璃效果（共享逻辑）
     */
    /**
     * 创建弹窗内加载指示器（居中旋转动画 + 提示文字）
     */
    private Div createDialogLoadingOverlay(String message) {
        Div spinner = new Div();
        spinner.getStyle()
            .set("width", "40px")
            .set("height", "40px")
            .set("border", "3px solid var(--lumo-contrast-10pct)")
            .set("border-top-color", "var(--lumo-primary-color)")
            .set("border-radius", "50%")
            .set("animation", "spin 0.8s linear infinite");

        Span text = new Span(message);
        text.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "14px")
            .set("margin-top", "16px");

        Div overlay = new Div(spinner, text);
        overlay.getStyle()
            .set("position", "absolute")
            .set("inset", "0")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("background", "var(--lumo-base-color)")
            .set("z-index", "10");

        return overlay;
    }

    private void applyDialogGlassEffect(String dialogClassName) {
        UI.getCurrent().getPage().executeJs(
            "setTimeout(() => {" +
            "  const dialog = document.querySelector('vaadin-dialog." + dialogClassName + "');" +
            "  if (!dialog || !dialog.shadowRoot) return;" +
            "  const overlay = dialog.shadowRoot.querySelector('vaadin-dialog-overlay');" +
            "  if (!overlay || !overlay.shadowRoot) return;" +
            "  const backdrop = overlay.shadowRoot.querySelector('[part=\"backdrop\"]');" +
            "  if (backdrop) {" +
            "    backdrop.style.backdropFilter = 'blur(4px)';" +
            "    backdrop.style.webkitBackdropFilter = 'blur(4px)';" +
            "    backdrop.style.background = 'rgba(0, 0, 0, 0.5)';" +
            "  }" +
            "  const header = overlay.shadowRoot.querySelector('[part=\"header\"]');" +
            "  if (header) header.style.justifyContent = 'center';" +
            "  const title = overlay.shadowRoot.querySelector('[part=\"title\"]');" +
            "  if (title) { title.style.textAlign = 'center'; title.style.flex = '1'; }" +
            "}, 100);"
        );
    }

    /**
     * 构建 AI Document 编辑器
     * 基于 DECOUPLED 编辑器类型，启用 AI_DOCUMENT 预设 + 模块化 AI 插件（v47.5.0）
     */
    private VaadinCKEditor buildAIDocumentEditor() {
        var builder = VaadinCKEditor.create()
            .withType(CKEditorType.DECOUPLED)
            .withPreset(CKEditorPreset.AI_DOCUMENT)
            .withTheme(state.getTheme())
            .withLanguage(state.getLanguage())
            .withWidth("100%")
            .withValue("<h2>AI Document Editor Preview</h2>" +
                "<p>This is a preview of the AI-powered Document Editor.</p>" +
                "<h3>AI Assistant Features</h3>" +
                "<p>Use the AI chat sidebar or select text and use Quick Actions " +
                "to rewrite, summarize, expand, or translate your content.</p>" +
                "<h3>Getting Started</h3>" +
                "<p>Try selecting some text and clicking the AI button, " +
                "or use the AI Chat panel in the toolbar to generate content.</p>" +
                "<h3>Section 1: Introduction</h3>" +
                "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>" +
                "<h3>Section 2: Details</h3>" +
                "<p>Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris " +
                "nisi ut aliquip ex ea commodo consequat.</p>");

        // License Key（AI 插件需要 Premium license）
        if (state.hasLicenseKey()) {
            builder.withLicenseKey(state.getLicenseKey());
        }

        // Document 增强插件
        if (state.hasLicenseKey() && state.isDocumentOutlineEnabled()) {
            builder.addCustomPlugin(CustomPlugin.fromPremium("DocumentOutline"));
        }
        if (state.isMinimapEnabled()) {
            builder.addPlugin(CKEditorPlugin.MINIMAP);
        }

        // AI 插件（v47.5.0 模块化）
        builder.addCustomPlugin(CustomPlugin.fromPremium("AIChat"))
            .addCustomPlugin(CustomPlugin.fromPremium("AIEditorIntegration"))
            .addCustomPlugin(CustomPlugin.fromPremium("AIQuickActions"))
            .addCustomPlugin(CustomPlugin.fromPremium("AIReviewMode"))
            .addCustomPlugin(CustomPlugin.fromPremium("AITranslate"));

        // 额外 premium 插件（对齐 v47.5.0 sample）
        builder.addCustomPlugin(CustomPlugin.fromPremium("FormatPainter"))
            .addCustomPlugin(CustomPlugin.fromPremium("PasteFromOfficeEnhanced"))
            .addCustomPlugin(CustomPlugin.fromPremium("SlashCommand"))
            .addCustomPlugin(CustomPlugin.fromPremium("LineHeight"));

        // 工具栏（v47.5.0 新 AI toolbar items）
        builder.withToolbar(new String[] {
            "undo", "redo", "|",
            "toggleAi", "aiQuickActions", "|",
            "formatPainter", "findAndReplace", "fullscreen", "|",
            "heading", "|",
            "fontSize", "fontFamily", "fontColor", "fontBackgroundColor", "|",
            "bold", "italic", "underline", "strikethrough",
            "subscript", "superscript", "code", "removeFormat", "|",
            "emoji", "specialCharacters", "horizontalLine",
            "link", "bookmark", "insertImage", "mediaEmbed",
            "insertTable", "blockQuote", "codeBlock", "|",
            "alignment", "lineHeight", "|",
            "bulletedList", "numberedList", "todoList",
            "outdent", "indent"
        });

        // 配置
        CKEditorConfig config = new CKEditorConfig();
        config.setLanguage(state.getLanguage());
        config.setPlaceholder(I18nUtil.get("step7.preview.placeholder"));

        // AI 配置（v47.5.0 sidebar container 模式）
        ObjectNode containerNode = createObjectNode();
        containerNode.put("type", "sidebar");
        containerNode.put("showResizeButton", false);

        ObjectNode documentCtx = createObjectNode();
        documentCtx.put("enabled", true);
        ObjectNode urlsCtx = createObjectNode();
        urlsCtx.put("enabled", true);
        ObjectNode filesCtx = createObjectNode();
        filesCtx.put("enabled", true);

        ObjectNode contextNode = createObjectNode();
        contextNode.set("document", documentCtx);
        contextNode.set("urls", urlsCtx);
        contextNode.set("files", filesCtx);

        ObjectNode chatNode = createObjectNode();
        chatNode.set("context", contextNode);

        // OpenAI-compatible API 配置
        // CKEditor Cloud Services 作为中间层调用此 URL（服务端代理）
        ObjectNode openAINode = createObjectNode();
        openAINode.put("apiUrl", aiProperties != null ? aiProperties.getApiUrl()
            : "https://api.openai.com/v1/chat/completions");
        openAINode.put("model", aiProperties != null ? aiProperties.getModel() : "grok-4.1");
        ObjectNode requestHeadersNode = createObjectNode();
        requestHeadersNode.put("Content-Type", "application/json");
        openAINode.set("requestHeaders", requestHeadersNode);

        ObjectNode aiNode = createObjectNode();
        aiNode.set("container", containerNode);
        aiNode.set("chat", chatNode);
        aiNode.set("openAI", openAINode);

        config.set("ai", aiNode);

        // Cloud Services — AI 插件初始化要求 tokenUrl
        if (collaborationProperties != null && collaborationProperties.isConfigured()) {
            ObjectNode cloudServicesNode = createObjectNode();
            cloudServicesNode.put("tokenUrl", "/api/ckeditor/ai-token");
            config.set("cloudServices", cloudServicesNode);

            ObjectNode collaborationNode = createObjectNode();
            collaborationNode.put("channelId", "ai-document-preview");
            config.set("collaboration", collaborationNode);
        }

        // menuBar 可见
        ObjectNode menuBarNode = createObjectNode();
        menuBarNode.put("isVisible", true);
        config.set("menuBar", menuBarNode);

        builder.withConfig(config);

        VaadinCKEditor editor = builder.build();

        // ai-editor class — 触发 ai-document-editor.css 中的样式覆盖
        // （540px 侧栏、static 定位、margin:0 等，对齐原生 CKEditor Builder）
        editor.addClassName("ai-editor");

        // AI 侧栏容器（AI Chat sidebar 模式需要 DOM 元素）
        editor.setAiSidebarEnabled(true);

        // Document 选项
        if (state.isDocumentOutlineEnabled() && state.hasLicenseKey()) {
            editor.getElement().setProperty("documentOutlineEnabled", true);
        }
        if (state.isMinimapEnabled()) {
            editor.getElement().setProperty("minimapEnabled", true);
            editor.getElement().setProperty("allowConfigRequiredPlugins", true);
        }

        // 自定义 CSS
        applyCustomCss(editor);

        return editor;
    }

    /**
     * 创建 Collaborative 预览缩略图（复用 Document 缩略图结构，增加协作视觉元素）
     */
    private Div createCollaborativeThumbnail() {
        Div thumbnail = new Div();
        thumbnail.addClassName("collaborative-preview-thumbnail");
        thumbnail.getStyle()
            .set("position", "absolute")
            .set("top", "0")
            .set("left", "0")
            .set("right", "0")
            .set("bottom", "0")
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

        String[] menuKeys = {"file", "edit", "view", "insert", "format", "tools", "help"};
        for (String key : menuKeys) {
            Span menuItem = new Span(I18nUtil.get("step7.preview.menu." + key));
            menuItem.getStyle()
                .set("font-size", "11px")
                .set("color", "var(--lumo-secondary-text-color)");
            menuBar.add(menuItem);
        }

        // 模拟工具栏（增加协作图标：trackChanges、comment）
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

        // 第二组：协作功能（trackChanges、comment）
        toolbar.add(createToolbarIconButton(VaadinIcon.RECORDS));
        toolbar.add(createToolbarIconButton(VaadinIcon.COMMENT_ELLIPSIS));
        toolbar.add(createToolbarSeparator());

        // 第三组：查找/全屏
        toolbar.add(createToolbarIconButton(VaadinIcon.SEARCH));
        toolbar.add(createToolbarIconButton(VaadinIcon.EXPAND_SQUARE));
        toolbar.add(createToolbarSeparator());

        // 第四组：文本格式
        toolbar.add(createToolbarIconButton(VaadinIcon.BOLD));
        toolbar.add(createToolbarIconButton(VaadinIcon.ITALIC));
        toolbar.add(createToolbarIconButton(VaadinIcon.UNDERLINE));
        toolbar.add(createToolbarSeparator());

        // 第五组：插入
        toolbar.add(createToolbarIconButton(VaadinIcon.LINK));
        toolbar.add(createToolbarIconButton(VaadinIcon.PICTURE));
        toolbar.add(createToolbarIconButton(VaadinIcon.TABLE));

        // 用户头像指示器（协作特征）
        Div presenceBar = new Div();
        presenceBar.getStyle()
            .set("margin-left", "auto")
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "4px");

        String[] avatarColors = {"#4CAF50", "#2196F3", "#FF9800"};
        String[] avatarLabels = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            Div avatar = new Div();
            avatar.getStyle()
                .set("width", "22px")
                .set("height", "22px")
                .set("border-radius", "50%")
                .set("background", avatarColors[i])
                .set("color", "white")
                .set("font-size", "11px")
                .set("font-weight", "600")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center");
            avatar.setText(avatarLabels[i]);
            presenceBar.add(avatar);
        }
        toolbar.add(presenceBar);

        // 模拟内容区域（三栏布局：大纲 | 文档 | 批注侧栏）
        Div content = new Div();
        content.getStyle()
            .set("flex", "1")
            .set("min-height", "0")
            .set("padding", "16px")
            .set("display", "flex")
            .set("gap", "12px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("overflow", "hidden");

        // 左侧：模拟文档大纲
        Div outline = new Div();
        outline.getStyle()
            .set("width", "100px")
            .set("min-width", "100px")
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "4px")
            .set("padding", "10px")
            .set("border", "1px solid var(--lumo-contrast-10pct)");

        Span outlineTitle = new Span(I18nUtil.get("step7.preview.documentOutline"));
        outlineTitle.getStyle()
            .set("font-size", "10px")
            .set("font-weight", "600")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("display", "block")
            .set("margin-bottom", "8px");
        outline.add(outlineTitle);

        for (int i = 0; i < 4; i++) {
            Div item = new Div();
            item.getStyle()
                .set("height", "7px")
                .set("margin-bottom", "5px")
                .set("border-radius", "2px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("margin-left", i % 2 == 1 ? "10px" : "0")
                .set("width", i % 2 == 1 ? "65%" : "85%");
            outline.add(item);
        }
        content.add(outline);

        // 中间：模拟文档内容（A4 样式）
        Div docWrapper = new Div();
        docWrapper.getStyle()
            .set("flex", "1")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("overflow", "hidden");

        Div docContent = new Div();
        docContent.getStyle()
            .set("width", "100%")
            .set("max-width", "240px")
            .set("background", "white")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("border-radius", "2px")
            .set("padding", "16px 12px")
            .set("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.08)");

        // 模拟标题
        Div titleLine = new Div();
        titleLine.getStyle()
            .set("height", "11px")
            .set("width", "65%")
            .set("margin-bottom", "10px")
            .set("border-radius", "2px")
            .set("background", "var(--lumo-contrast-20pct)");
        docContent.add(titleLine);

        // 模拟文本行（部分带高亮表示 track changes）
        for (int i = 0; i < 10; i++) {
            Div line = new Div();
            line.getStyle()
                .set("height", "5px")
                .set("margin-bottom", "5px")
                .set("border-radius", "2px");

            if (i == 2 || i == 6) {
                // 模拟 track changes 高亮行
                line.getStyle()
                    .set("background", "rgba(76, 175, 80, 0.2)")
                    .set("border-left", "2px solid #4CAF50")
                    .set("width", "90%");
            } else if (i == 4) {
                // 模拟删除线
                line.getStyle()
                    .set("background", "rgba(244, 67, 54, 0.15)")
                    .set("border-left", "2px solid #F44336")
                    .set("width", "70%");
            } else if (i == 8) {
                // 模拟小标题
                line.getStyle()
                    .set("height", "8px")
                    .set("width", "45%")
                    .set("margin-top", "6px")
                    .set("background", "var(--lumo-contrast-15pct)");
            } else {
                line.getStyle()
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("width", i == 3 ? "80%" : "100%");
            }
            docContent.add(line);
        }
        docWrapper.add(docContent);
        content.add(docWrapper);

        // 右侧：模拟批注侧栏（annotation sidebar）
        Div sidebar = new Div();
        sidebar.getStyle()
            .set("width", "120px")
            .set("min-width", "120px")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "8px");

        // 模拟评论框
        String[] commentAuthors = {"Alice", "Bob", "Charlie"};
        String[] commentColors = {"#4CAF50", "#2196F3", "#FF9800"};
        int[] commentLineWidths = {85, 70, 90};
        for (int i = 0; i < 3; i++) {
            Div commentBox = new Div();
            commentBox.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "6px")
                .set("padding", "8px")
                .set("border-left", "3px solid " + commentColors[i]);

            // 作者名
            Span author = new Span(commentAuthors[i]);
            author.getStyle()
                .set("font-size", "9px")
                .set("font-weight", "600")
                .set("color", commentColors[i])
                .set("display", "block")
                .set("margin-bottom", "4px");
            commentBox.add(author);

            // 模拟评论文字
            for (int j = 0; j < 2; j++) {
                Div textLine = new Div();
                textLine.getStyle()
                    .set("height", "4px")
                    .set("margin-bottom", "3px")
                    .set("border-radius", "2px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("width", (j == 0 ? commentLineWidths[i] : commentLineWidths[i] - 20) + "%");
                commentBox.add(textLine);
            }
            sidebar.add(commentBox);
        }
        content.add(sidebar);

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

        Span hint = new Span(I18nUtil.get("step7.preview.collaborativeClickToPreview"));
        hint.getStyle()
            .set("font-size", "14px")
            .set("font-weight", "500")
            .set("color", "var(--lumo-body-text-color)");

        hintBox.add(expandIcon, hint);
        hintOverlay.add(hintBox);

        // 使用相对定位的容器
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
        thumbnail.addClickListener(e -> openCollaborativePreviewDialog());

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
     * 打开 Collaborative 预览弹窗（iframe 加载 /collaborative-document-editor）
     */
    private void openCollaborativePreviewDialog() {
        ensureCollaborativePreviewDialog();
        collaborativePreviewDialog.open();

        // 应用弹窗样式（毛玻璃背景和标题居中）
        UI.getCurrent().getPage().executeJs(
            "setTimeout(() => {" +
            "  const dialog = document.querySelector('vaadin-dialog.collaborative-preview-dialog');" +
            "  if (!dialog || !dialog.shadowRoot) return;" +
            "  const overlay = dialog.shadowRoot.querySelector('vaadin-dialog-overlay');" +
            "  if (!overlay || !overlay.shadowRoot) return;" +
            "  const backdrop = overlay.shadowRoot.querySelector('[part=\"backdrop\"]');" +
            "  if (backdrop) {" +
            "    backdrop.style.backdropFilter = 'blur(4px)';" +
            "    backdrop.style.webkitBackdropFilter = 'blur(4px)';" +
            "    backdrop.style.background = 'rgba(0, 0, 0, 0.5)';" +
            "  }" +
            "  const header = overlay.shadowRoot.querySelector('[part=\"header\"]');" +
            "  if (header) header.style.justifyContent = 'center';" +
            "  const title = overlay.shadowRoot.querySelector('[part=\"title\"]');" +
            "  if (title) { title.style.textAlign = 'center'; title.style.flex = '1'; }" +
            "}, 100);"
        );
    }

    /**
     * 确保 Collaborative 预览弹窗已创建（iframe 模式，无需清理编辑器实例）
     */
    private void ensureCollaborativePreviewDialog() {
        if (collaborativePreviewDialog != null) return;

        collaborativePreviewDialog = new Dialog();
        collaborativePreviewDialog.setWidth("95vw");
        collaborativePreviewDialog.setHeight("95vh");
        collaborativePreviewDialog.setCloseOnEsc(true);
        collaborativePreviewDialog.setCloseOnOutsideClick(true);
        collaborativePreviewDialog.addClassName("collaborative-preview-dialog");
        collaborativePreviewDialog.setHeaderTitle(
            I18nUtil.get("step7.preview.collaborativeDialogTitle"));

        // 关闭按钮
        Button closeBtn = new Button(VaadinIcon.CLOSE.create(),
            e -> collaborativePreviewDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        collaborativePreviewDialog.getHeader().add(closeBtn);

        // iframe 加载协作编辑器页面
        IFrame iframe = new IFrame("/collaborative-document-editor");
        iframe.setSizeFull();
        iframe.getElement().getStyle()
            .set("border", "none")
            .set("border-radius", "8px");

        Div iframeContainer = new Div(iframe);
        iframeContainer.setSizeFull();
        iframeContainer.getStyle()
            .set("padding", "0")
            .set("box-sizing", "border-box");

        collaborativePreviewDialog.add(iframeContainer);
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
        cleanupDialogEditor(dialogEditorContainer);
    }

    /**
     * 清理指定容器中的弹窗编辑器
     */
    private void cleanupDialogEditor(Div container) {
        if (dialogEditor != null) {
            dialogEditor.getElement().executeJs(
                "if (this.editor && typeof this.editor.destroy === 'function') {" +
                "  this.editor.destroy().catch(err => console.warn('Editor destroy error:', err));" +
                "}"
            );
            dialogEditor = null;
        }
        if (container != null) {
            container.removeAll();
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

        // 清理协作预览弹窗
        if (collaborativePreviewDialog != null && collaborativePreviewDialog.isOpened()) {
            collaborativePreviewDialog.close();
        }

        // 清理 AI Document 预览弹窗
        if (aiDocumentPreviewDialog != null && aiDocumentPreviewDialog.isOpened()) {
            aiDocumentPreviewDialog.close();
        }

        // 清理 Email 预览弹窗
        if (emailPreviewDialog != null && emailPreviewDialog.isOpened()) {
            emailPreviewDialog.close();
        }

        // 清理 Notion 预览弹窗
        if (notionPreviewDialog != null && notionPreviewDialog.isOpened()) {
            notionPreviewDialog.close();
        }
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        // 最后一步，无需验证
        return ValidationResult.ok();
    }
}
