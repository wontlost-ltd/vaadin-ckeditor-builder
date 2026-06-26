package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.wontlost.ckeditor.CKEditorConfig;
import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.CustomPlugin;
import com.wontlost.ckeditor.VaadinCKEditor;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 4: Toolbar Configuration
 * Preview toolbar using real VaadinCKEditor component
 * Supports drag-and-drop sorting, grouping and separator management
 */
public class ToolbarStep implements WizardStep {

    private static final String TOOLBAR_SEPARATOR = "|";

    private BuilderState state;
    private VerticalLayout content;
    private Div toolbarPreview;
    private VaadinCKEditor toolbarEditor;
    private ToolbarDndBridge dndBridge;

    @Override
    public String getId() { return "toolbar"; }

    @Override
    public String getTitle() { return I18nUtil.get("step4.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step4.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.TOOLS; }

    @Override
    public Component getContent() {
        boolean isNew = content == null;
        if (isNew) {
            content = createContent();
        }
        // Update toolbar preview after content is created (onEnter is called before getContent, when toolbarPreview is not yet created)
        if (isNew && state != null) {
            updateToolbarPreview();
        }
        return content;
    }

    private VerticalLayout createContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.addClassName("toolbar-step");

        // Operation guide (placed at top)
        Div operationGuide = createOperationGuide();

        // Toolbar preview area
        Div previewSection = new Div();
        previewSection.addClassName("toolbar-preview-section");
        previewSection.setMaxWidth("800px");

        H4 previewTitle = new H4(I18nUtil.get("step4.preview"));
        previewTitle.addClassName("preview-title");

        toolbarPreview = new Div();
        toolbarPreview.addClassName("toolbar-preview");

        // DnD communication bridge component
        dndBridge = new ToolbarDndBridge();
        dndBridge.addClassName("toolbar-dnd-bridge");
        dndBridge.getStyle().set("display", "none");

        previewSection.add(previewTitle, toolbarPreview, dndBridge);

        // Action buttons
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setMaxWidth("800px");
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actions.addClassName("toolbar-actions");

        Button addSeparatorBtn = new Button(I18nUtil.get("step4.addSeparator"), VaadinIcon.MINUS.create());
        addSeparatorBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        addSeparatorBtn.addClickListener(e -> addSeparator());

        Button applyBtn = new Button(I18nUtil.get("step4.applyChanges"), VaadinIcon.CHECK.create());
        applyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyBtn.addClickListener(e -> applyToolbarChanges());

        Button resetBtn = new Button(I18nUtil.get("step4.reset"), VaadinIcon.ROTATE_LEFT.create());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        resetBtn.addClickListener(e -> resetToolbar());

        actions.add(addSeparatorBtn, applyBtn, resetBtn);

        layout.add(operationGuide, previewSection, actions);

        return layout;
    }

    /**
     * Create operation guide (drag-and-drop layout instructions)
     */
    private Div createOperationGuide() {
        Div guide = new Div();
        guide.addClassName("toolbar-operation-guide");
        guide.setMaxWidth("800px");

        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        Icon icon = VaadinIcon.INFO_CIRCLE.create();
        icon.setSize("20px");
        icon.addClassName("guide-icon");

        Span title = new Span(I18nUtil.get("step4.operationGuide"));
        title.addClassName("guide-title");

        header.add(icon, title);

        VerticalLayout instructions = new VerticalLayout();
        instructions.setPadding(false);
        instructions.setSpacing(false);
        instructions.addClassName("guide-instructions");

        instructions.add(createGuideItem(I18nUtil.get("step4.guide.dragSort"), I18nUtil.get("step4.guide.dragSort.desc")));
        instructions.add(createGuideItem(I18nUtil.get("step4.guide.addSeparator"), I18nUtil.get("step4.guide.addSeparator.desc")));
        instructions.add(createGuideItem(I18nUtil.get("step4.guide.deleteSeparator"), I18nUtil.get("step4.guide.deleteSeparator.desc")));
        instructions.add(createGuideItem(I18nUtil.get("step4.guide.applyChanges"), I18nUtil.get("step4.guide.applyChanges.desc")));

        guide.add(header, instructions);

        return guide;
    }

    private HorizontalLayout createGuideItem(String label, String description) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(FlexComponent.Alignment.BASELINE);
        item.setSpacing(true);
        item.addClassName("guide-item");

        Span labelSpan = new Span("• " + label + "：");
        labelSpan.addClassName("guide-item-label");

        Span descSpan = new Span(description);
        descSpan.addClassName("guide-item-desc");

        item.add(labelSpan, descSpan);
        return item;
    }

    private void updateToolbarPreview() {
        if (state == null || toolbarPreview == null) return;

        toolbarPreview.removeAll();

        List<String> toolbarItems = getToolbarItemsForPreview();
        toolbarEditor = buildToolbarEditor(toolbarItems);
        toolbarEditor.addClassName("toolbar-only-preview");
        toolbarEditor.setWidthFull();

        toolbarPreview.add(toolbarEditor);
        initToolbarDnd(toolbarItems);
    }

    // Premium plugins that need special configuration or containers (skip in toolbar preview)
    private static final Set<String> PREMIUM_PLUGINS_TO_SKIP = Set.of(
        "DocumentOutline",      // Requires container
        "TableOfContents",      // Requires container
        "AIAssistant",          // Requires AI adapter configuration
        "Comments",             // Requires comments configuration
        "TrackChanges",         // Requires collaboration configuration
        "RevisionHistory",      // Requires collaboration configuration
        "Pagination"            // Requires pagination configuration
    );

    // Standard plugins to skip (initialization issues)
    private static final Set<String> STANDARD_PLUGINS_TO_SKIP = Set.of(
        "Minimap"               // Requires editor DOM to be fully ready
    );

    // Conflicting plugin pairs (mutually exclusive, cannot be enabled together)
    private static final Map<String, String> CONFLICTING_PLUGINS = Map.of(
        "RestrictedEditingMode", "StandardEditingMode",
        "StandardEditingMode", "RestrictedEditingMode"
    );

    /**
     * 构建工具栏预览编辑器
     * 注意：强制使用 CLASSIC 类型以确保工具栏始终可见，便于拖拽排序
     * Balloon 和 Inline 编辑器的工具栏在选择文本时才会弹出，无法进行拖拽操作
     */
    private VaadinCKEditor buildToolbarEditor(List<String> toolbarItems) {
        var builder = VaadinCKEditor.create()
            .withPreset(state.getPreset())
            // 强制使用 CLASSIC 类型以显示固定工具栏，便于拖拽排序
            // 实际编辑器类型仍保留在 state 中，仅预览时使用 CLASSIC
            .withType(CKEditorType.CLASSIC)
            .withTheme(state.getTheme())
            .withLanguage(state.getLanguage())
            .withWidth("100%");

        // Set License Key
        if (state.hasLicenseKey()) {
            builder.withLicenseKey(state.getLicenseKey());
        }

        // Collect added plugin names for conflict detection
        Set<String> addedPlugins = new java.util.HashSet<>();

        // Add user-selected plugins (skip problematic ones)
        for (CKEditorPlugin plugin : state.getSelectedPlugins()) {
            String pluginName = plugin.getJsName();
            // Skip standard plugins that need special handling
            if (STANDARD_PLUGINS_TO_SKIP.contains(pluginName)) {
                continue;
            }
            // Check plugin conflicts
            String conflicting = CONFLICTING_PLUGINS.get(pluginName);
            if (conflicting != null && addedPlugins.contains(conflicting)) {
                // Skip conflicting plugin
                continue;
            }
            if (!state.getPreset().hasPlugin(plugin)) {
                builder.addPlugin(plugin);
                addedPlugins.add(pluginName);
            }
        }

        // Remove plugins from preset that user didn't select
        for (CKEditorPlugin presetPlugin : state.getPreset().getPlugins()) {
            if (!state.hasPlugin(presetPlugin)) {
                builder.removePlugin(presetPlugin);
            }
        }

        // Add Premium plugins (skip those needing special configuration)
        for (BuilderState.CustomPluginConfig premiumConfig : state.getPremiumPlugins()) {
            String pluginName = premiumConfig.getName();
            // Skip plugins that need special configuration
            if (PREMIUM_PLUGINS_TO_SKIP.contains(pluginName)) {
                continue;
            }
            // Check conflicts
            String conflicting = CONFLICTING_PLUGINS.get(pluginName);
            if (conflicting != null && addedPlugins.contains(conflicting)) {
                continue;
            }
            CustomPlugin.Builder pluginBuilder = CustomPlugin.builder(pluginName)
                .premium();
            if (!premiumConfig.getToolbarItems().isEmpty()) {
                pluginBuilder.withToolbarItems(premiumConfig.getToolbarItems().toArray(new String[0]));
            }
            builder.addCustomPlugin(pluginBuilder.build());
            addedPlugins.add(pluginName);
        }

        // Add custom plugins (skip potentially problematic ones)
        for (BuilderState.CustomPluginConfig customConfig : state.getCustomPlugins()) {
            if (customConfig.isPremium()) {
                continue;
            }
            String pluginName = customConfig.getName();
            // Skip LineHeight (module import issues)
            if ("LineHeight".equals(pluginName)) {
                continue;
            }
            CustomPlugin.Builder pluginBuilder = CustomPlugin.builder(pluginName)
                .withImportPath(customConfig.getImportPath());
            if (!customConfig.getToolbarItems().isEmpty()) {
                pluginBuilder.withToolbarItems(customConfig.getToolbarItems().toArray(new String[0]));
            }
            builder.addCustomPlugin(pluginBuilder.build());
        }

        // Configure toolbar
        List<String> filteredToolbarItems = toolbarItems;

        // Must use builder.withToolbar() to explicitly set toolbar
        // Otherwise frontend will use preset's default toolbar (which doesn't include Premium plugin items)
        if (!filteredToolbarItems.isEmpty()) {
            builder.withToolbar(filteredToolbarItems.toArray(new String[0]));
        }

        CKEditorConfig config = new CKEditorConfig()
            .setLanguage(state.getLanguage());
        if (!filteredToolbarItems.isEmpty()) {
            // shouldNotGroupWhenFull = true ensures all buttons visible for easier dragging
            config.setToolbar(filteredToolbarItems.toArray(new String[0]), true);
        }

        // Configure image toolbar (prevent widget-toolbar-no-items warning)
        if (state.hasPlugin(CKEditorPlugin.IMAGE_TOOLBAR)) {
            List<String> imageToolbar = state.getImageToolbar();
            if (imageToolbar.isEmpty()) {
                // Use default image toolbar configuration
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
            // Use CKEditorConfig.setImage(toolbar, styles) to configure
            config.setImage(imageToolbar.toArray(new String[0]), new String[0]);
        }

        // Configure table toolbar (prevent similar warnings)
        if (state.hasPlugin(CKEditorPlugin.TABLE_TOOLBAR)) {
            List<String> tableToolbar = state.getTableContentToolbar();
            if (tableToolbar.isEmpty()) {
                // Use default table toolbar configuration
                tableToolbar = List.of(
                    "tableColumn",
                    "tableRow",
                    "mergeTableCells",
                    "|",
                    "tableProperties",
                    "tableCellProperties"
                );
            }
            // Use CKEditorConfig.setTable(contentToolbar) to configure
            config.setTable(tableToolbar.toArray(new String[0]));
        }

        builder.withConfig(config);

        return builder.build();
    }

    /**
     * Get toolbar items list (for preview)
     */
    private List<String> getToolbarItemsForPreview() {
        List<String> toolbarItems = new ArrayList<>(state.getToolbarItems());

        // If no custom toolbar items, generate from plugins
        if (toolbarItems.isEmpty()) {
            Set<String> items = new LinkedHashSet<>();
            for (CKEditorPlugin plugin : state.getSelectedPlugins()) {
                items.addAll(plugin.getToolbarItems());
            }
            toolbarItems.addAll(items);
        }

        // Add Premium plugin toolbar items
        for (BuilderState.CustomPluginConfig premiumConfig : state.getPremiumPlugins()) {
            for (String toolbarItem : premiumConfig.getToolbarItems()) {
                if (!toolbarItems.contains(toolbarItem)) {
                    toolbarItems.add(toolbarItem);
                }
            }
        }

        // Add custom plugin toolbar items
        for (BuilderState.CustomPluginConfig customConfig : state.getCustomPlugins()) {
            for (String toolbarItem : customConfig.getToolbarItems()) {
                if (!toolbarItems.contains(toolbarItem)) {
                    toolbarItems.add(toolbarItem);
                }
            }
        }

        return toolbarItems;
    }

    /**
     * Initialize toolbar drag-and-drop functionality
     * Inline JavaScript to avoid module import issues
     */
    private void initToolbarDnd(List<String> toolbarItems) {
        if (toolbarEditor == null || dndBridge == null) {
            return;
        }

        // Convert toolbarItems to JSON array string
        StringBuilder itemsJson = new StringBuilder("[");
        for (int i = 0; i < toolbarItems.size(); i++) {
            if (i > 0) itemsJson.append(",");
            itemsJson.append("\"").append(toolbarItems.get(i).replace("\"", "\\\"")).append("\"");
        }
        itemsJson.append("]");

        // Get translated separator tooltip
        String separatorTooltip = escapeJsString(I18nUtil.get("step4.separatorTooltip"));

        // Inline JavaScript for drag-and-drop functionality (no auto-sync after drag, requires "Apply Changes" button)
        String initScript = """
            (function(editorEl, bridgeEl, items, sepTooltip) {
                const tryAttach = () => {
                    const toolbarItems = editorEl.querySelector('.ck-toolbar__items');
                    if (!toolbarItems) return false;
                    setupToolbar(toolbarItems, bridgeEl, items);
                    return true;
                };

                function setupToolbar(toolbarItems, bridgeEl, items) {
                    if (toolbarItems.dataset.toolbarDndReady === 'true') {
                        // Already initialized, just remap
                        mapToolbarItems(toolbarItems, items);
                        return;
                    }
                    toolbarItems.dataset.toolbarDndReady = 'true';

                    // Delayed mapping, wait for CKEditor to finish initialization (adding tooltip and other attributes)
                    const delayedMap = () => {
                        mapToolbarItems(toolbarItems, items);
                        // Check if all non-separator elements have been mapped
                        const total = Array.from(toolbarItems.children).filter(
                            c => !c.classList.contains('ck-toolbar__grouped-dropdown')
                        ).length;
                        const mapped = toolbarItems.querySelectorAll('.toolbar-dnd-item').length;
                        if (mapped < total * 0.8) {
                            // If less than 80% mapped, retry after 500ms
                            setTimeout(() => mapToolbarItems(toolbarItems, items), 500);
                        }
                    };
                    setTimeout(delayedMap, 300);

                    let dragged = null, lastOver = null;

                    toolbarItems.addEventListener('dragstart', (e) => {
                        const target = e.target?.closest('.toolbar-dnd-item');
                        if (!target) return;
                        dragged = target;
                        target.classList.add('toolbar-dnd-dragging');
                        e.dataTransfer?.setData('text/plain', '');
                        if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
                    });

                    toolbarItems.addEventListener('dragover', (e) => {
                        if (!dragged) return;
                        e.preventDefault();
                        const target = e.target?.closest('.toolbar-dnd-item');
                        if (!target || target === dragged) return;
                        if (lastOver && lastOver !== target) lastOver.classList.remove('toolbar-dnd-over');
                        lastOver = target;
                        target.classList.add('toolbar-dnd-over');
                        const rect = target.getBoundingClientRect();
                        const before = e.clientX < rect.left + rect.width / 2;
                        const referenceNode = before ? target : target.nextSibling;
                        if (referenceNode !== dragged) toolbarItems.insertBefore(dragged, referenceNode);
                    });

                    toolbarItems.addEventListener('drop', (e) => {
                        if (!dragged) return;
                        e.preventDefault();
                        // Drag completed but no auto-sync, requires user to click "Apply Changes" button
                    });

                    toolbarItems.addEventListener('dragend', () => {
                        if (dragged) dragged.classList.remove('toolbar-dnd-dragging');
                        if (lastOver) { lastOver.classList.remove('toolbar-dnd-over'); lastOver = null; }
                        dragged = null;
                    });

                    // Click event handling: separators can be deleted, other buttons selected (for positioning separator insertion)
                    toolbarItems.addEventListener('click', (e) => {
                        const sepTarget = e.target?.closest('.toolbar-dnd-separator');
                        if (sepTarget) {
                            // Click separator - delete it
                            sepTarget.remove();
                            return;
                        }
                        // Click tool button - select it (for adding separator after this position)
                        const btnTarget = e.target?.closest('.toolbar-dnd-item');
                        if (btnTarget) {
                            e.preventDefault();
                            e.stopPropagation();
                            // Clear previous selected state
                            toolbarItems.querySelectorAll('.toolbar-dnd-selected').forEach(el => {
                                el.classList.remove('toolbar-dnd-selected');
                            });
                            // Select current item
                            btnTarget.classList.add('toolbar-dnd-selected');
                        }
                    }, true);  // Use capture phase to intercept events
                }

                function mapToolbarItems(toolbarItems, items) {
                    // Build tooltip to itemName mapping (based on server-provided items)
                    const tooltipMap = {};
                    items.forEach(item => {
                        if (item === '|') return;
                        // Convert to lowercase for matching
                        const key = item.toLowerCase();
                        tooltipMap[key] = item;
                    });

                    const children = Array.from(toolbarItems.children);
                    let itemIndex = 0;

                    children.forEach((child) => {
                        // Skip already processed elements
                        if (child.classList.contains('toolbar-dnd-item')) return;
                        // Skip grouped dropdown (collapsed button)
                        if (child.classList.contains('ck-toolbar__grouped-dropdown')) return;

                        const isSep = child.classList.contains('ck-toolbar__separator');
                        let itemName = null;

                        if (isSep) {
                            itemName = '|';
                        } else {
                            // Get tooltip text (from button itself or button inside dropdown)
                            let tooltip = child.getAttribute('data-cke-tooltip-text');
                            if (!tooltip) {
                                const innerBtn = child.querySelector('button[data-cke-tooltip-text]');
                                if (innerBtn) {
                                    tooltip = innerBtn.getAttribute('data-cke-tooltip-text');
                                }
                            }

                            if (tooltip) {
                                // Extract command name from tooltip (remove shortcut part)
                                // e.g. "Bold (⌘B)" -> "bold", "Insert table" -> "inserttable"
                                const cleanTooltip = tooltip.split('(')[0].trim().toLowerCase().replace(/[^a-z0-9]/g, '');

                                // Try to find matching item in items
                                for (const item of items) {
                                    if (item === '|') continue;
                                    const itemKey = item.toLowerCase().replace(/[^a-z0-9]/g, '');
                                    // Check if matches (contains relationship)
                                    if (cleanTooltip.includes(itemKey) || itemKey.includes(cleanTooltip)) {
                                        itemName = item;
                                        break;
                                    }
                                }

                                // If no match found, use cleaned tooltip as name
                                if (!itemName) {
                                    itemName = cleanTooltip;
                                }
                            }
                        }

                        if (!itemName) return;

                        child.dataset.toolbarItem = itemName;
                        child.classList.add('toolbar-dnd-item');
                        child.setAttribute('draggable', 'true');
                        if (itemName === '|') {
                            child.classList.add('toolbar-dnd-separator');
                            child.setAttribute('title', sepTooltip);
                        }
                    });
                }

                function collectToolbarGroups(toolbarItems) {
                    const groups = [];
                    let current = [];
                    Array.from(toolbarItems.children).forEach((child) => {
                        const item = child.dataset.toolbarItem;
                        if (!item) return;
                        if (item === '|') {
                            if (current.length) { groups.push(current); current = []; }
                        } else {
                            current.push(item);
                        }
                    });
                    if (current.length) groups.push(current);
                    return groups;
                }

                if (tryAttach()) return;

                const observer = new MutationObserver(() => {
                    if (tryAttach()) { observer.disconnect(); }
                });
                observer.observe(editorEl, { childList: true, subtree: true });
            })($0, $1, """ + itemsJson + ", \"" + separatorTooltip + "\")";

        toolbarEditor.getElement().executeJs(initScript, toolbarEditor.getElement(), dndBridge.getElement());
    }

    /**
     * 转义 JS 字符串字面量中的特殊字符
     */
    private String escapeJsString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("'", "\\'")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    /**
     * Add separator (insert after selected tool, without rebuilding editor)
     */
    private void addSeparator() {
        if (toolbarEditor == null) {
            return;
        }

        // Get translated separator tooltip
        String separatorTooltip = escapeJsString(I18nUtil.get("step4.separatorTooltip"));

        // Insert separator via JavaScript after selected tool (without rebuilding editor, keep current layout)
        toolbarEditor.getElement().executeJs(
            """
            (function(editorEl, sepTooltip) {
                const toolbarItems = editorEl.querySelector('.ck-toolbar__items');
                if (!toolbarItems) return;

                // Find selected tool item (with toolbar-dnd-selected class)
                let targetItem = toolbarItems.querySelector('.toolbar-dnd-selected');

                // If no selected item, use the last non-separator tool item
                if (!targetItem) {
                    const allItems = Array.from(toolbarItems.querySelectorAll('.toolbar-dnd-item:not(.toolbar-dnd-separator)'));
                    if (allItems.length > 0) {
                        targetItem = allItems[allItems.length - 1];
                    }
                }

                if (!targetItem) return;

                // Check if there's already a separator after the target item
                const nextSibling = targetItem.nextElementSibling;
                if (nextSibling && nextSibling.classList.contains('toolbar-dnd-separator')) {
                    return; // Avoid consecutive separators
                }

                // Create separator element (mimicking CKEditor native separator structure)
                const separator = document.createElement('span');
                separator.className = 'ck ck-toolbar__separator toolbar-dnd-item toolbar-dnd-separator';
                separator.dataset.toolbarItem = '|';
                separator.setAttribute('draggable', 'true');
                separator.setAttribute('title', sepTooltip);

                // Insert separator after target item
                targetItem.after(separator);

                // Clear selected state
                if (targetItem.classList.contains('toolbar-dnd-selected')) {
                    targetItem.classList.remove('toolbar-dnd-selected');
                }
            })($0, '""" + separatorTooltip + """
            ')
            """,
            toolbarEditor.getElement()
        );
    }

    /**
     * Reset toolbar
     */
    private void resetToolbar() {
        if (state == null) return;
        state.setAutoGenerateToolbar(true);
        state.setToolbarItems(new ArrayList<>()); // Clear custom items
        updateToolbarPreview();
    }

    /**
     * Apply toolbar changes (collect current order from frontend and sync to state)
     */
    private void applyToolbarChanges() {
        if (toolbarEditor == null || dndBridge == null) {
            return;
        }
        // Trigger frontend to collect current toolbar order and send to server
        toolbarEditor.getElement().executeJs(
            """
            (function(editorEl, bridgeEl) {
                const toolbarItems = editorEl.querySelector('.ck-toolbar__items');
                if (!toolbarItems) return;
                const groups = [];
                let current = [];
                Array.from(toolbarItems.children).forEach((child) => {
                    const item = child.dataset.toolbarItem;
                    if (!item) return;
                    if (item === '|') {
                        if (current.length) { groups.push(current); current = []; }
                    } else {
                        current.push(item);
                    }
                });
                if (current.length) groups.push(current);
                bridgeEl.$server?.updateToolbarItems(JSON.stringify(groups));
            })($0, $1)
            """,
            toolbarEditor.getElement(),
            dndBridge.getElement()
        );
    }

    /**
     * 应用拖拽后的工具栏顺序
     */
    private void applyToolbarOrder(String toolbarJson) {
        if (state == null || toolbarJson == null) {
            return;
        }
        List<String> items = parseToolbarItems(toolbarJson);
        if (items == null) {
            return;
        }
        state.setAutoGenerateToolbar(false);
        state.setToolbarItems(items);
    }

    /**
     * 解析工具栏 JSON（支持嵌套数组表示分组）
     * 格式: [["bold", "italic"], ["link"]] 或 ["bold", "italic", "|", "link"]
     * 使用简单的手动解析，避免外部依赖
     */
    private List<String> parseToolbarItems(String toolbarJson) {
        if (toolbarJson == null || toolbarJson.isBlank()) {
            return null;
        }
        try {
            List<String> items = new ArrayList<>();
            parseJsonArray(toolbarJson.trim(), items);
            return items;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 简单的 JSON 数组解析器
     * 支持嵌套数组，每个嵌套数组之间会自动添加分隔符
     */
    private void parseJsonArray(String json, List<String> items) {
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return;
        }
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return;
        }

        boolean hasGroup = false;
        int depth = 0;
        int start = 0;

        for (int i = 0; i <= content.length(); i++) {
            char c = i < content.length() ? content.charAt(i) : ',';

            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String element = content.substring(start, i).trim();
                if (!element.isEmpty()) {
                    if (element.startsWith("[")) {
                        // Nested array - represents a group
                        List<String> groupItems = new ArrayList<>();
                        parseJsonArray(element, groupItems);
                        if (!groupItems.isEmpty()) {
                            if (hasGroup && !items.isEmpty()
                                && !TOOLBAR_SEPARATOR.equals(items.get(items.size() - 1))) {
                                items.add(TOOLBAR_SEPARATOR);
                            }
                            items.addAll(groupItems);
                            hasGroup = true;
                        }
                    } else if (element.startsWith("\"") && element.endsWith("\"")) {
                        // String
                        String item = element.substring(1, element.length() - 1);
                        if (!item.isBlank()) {
                            items.add(item);
                        }
                    }
                }
                start = i + 1;
            }
        }
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;
        updateToolbarPreview();
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        if (state.getToolbarItems().isEmpty() && state.getSelectedPlugins().isEmpty()) {
            return ValidationResult.error(I18nUtil.get("validation.selectPlugin"));
        }
        return ValidationResult.ok();
    }

    @Override
    public boolean isSkippable() {
        return true; // Toolbar can be skipped, using auto-generate
    }

    /**
     * Inner class: Bridge component for receiving frontend DnD events
     */
    private class ToolbarDndBridge extends Div {
        @ClientCallable
        private void updateToolbarItems(String toolbarJson) {
            applyToolbarOrder(toolbarJson);
            // Show notification to user that toolbar has been updated
            Notification notification = Notification.show(I18nUtil.get("step4.notification.updated"), 2000, Notification.Position.BOTTOM_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }
}
