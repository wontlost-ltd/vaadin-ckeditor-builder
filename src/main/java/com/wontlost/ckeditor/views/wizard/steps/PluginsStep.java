package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.*;

/**
 * Step 3: Plugin Selection
 * User selects required CKEditor plugins
 * Uses accordion panels to group by category, plugins displayed as clickable cards
 */
public class PluginsStep implements WizardStep {

    private BuilderState state;
    private VerticalLayout content;
    private final Map<CKEditorPlugin, Div> pluginCards = new HashMap<>();
    private final Map<CKEditorPlugin.Category, AccordionPanel> categoryPanels = new HashMap<>();
    private TextField searchField;
    private Span selectedCountLabel;
    private String currentFilter = "all";
    private Accordion accordion;

    @Override
    public String getId() { return "plugins"; }

    @Override
    public String getTitle() { return I18nUtil.get("step3.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step3.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.PLUG; }

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
        layout.addClassName("plugins-step");

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(70);
        splitLayout.addClassName("plugins-split");

        // Left side: plugin list
        VerticalLayout pluginList = createPluginList();

        // Right side: selected plugins summary
        VerticalLayout summaryPanel = createSummaryPanel();

        splitLayout.addToPrimary(pluginList);
        splitLayout.addToSecondary(summaryPanel);

        layout.add(splitLayout);

        return layout;
    }

    private VerticalLayout createPluginList() {
        VerticalLayout list = new VerticalLayout();
        list.setSizeFull();
        list.setPadding(true);
        list.setSpacing(false);
        list.addClassName("plugin-list-panel");

        // Search and filter bar
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.addClassName("plugin-toolbar");

        // Search field
        searchField = new TextField();
        searchField.setPlaceholder(I18nUtil.get("step3.searchPlaceholder"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.addClassName("plugin-search");
        searchField.addValueChangeListener(e -> applyFilters());

        // Remove blue border on focus - inject styles into shadow DOM
        searchField.getElement().executeJs(
            "const el = this;" +
            "if (el.shadowRoot) {" +
            "  const style = document.createElement('style');" +
            "  style.textContent = `" +
            "    :host([focused]) [part=\"input-field\"] { box-shadow: none !important; }" +
            "    :host([focus-ring]) [part=\"input-field\"] { box-shadow: none !important; }" +
            "    [part=\"input-field\"] { box-shadow: none !important; }" +
            "  `;" +
            "  el.shadowRoot.appendChild(style);" +
            "}"
        );

        // Filter button group
        HorizontalLayout filterButtons = new HorizontalLayout();
        filterButtons.setSpacing(false);
        filterButtons.addClassName("plugin-filter-buttons");

        Button allBtn = createFilterButton(I18nUtil.get("step3.filter.all"), "all", true);
        Button selectedBtn = createFilterButton(I18nUtil.get("step3.filter.selected"), "selected", false);

        filterButtons.add(allBtn, selectedBtn);

        toolbar.add(searchField, filterButtons);
        toolbar.setFlexGrow(1, searchField);

        list.add(toolbar);

        // Use accordion to organize plugin categories
        accordion = new Accordion();
        accordion.setWidthFull();

        for (CKEditorPlugin.Category category : CKEditorPlugin.Category.values()) {
            Set<CKEditorPlugin> plugins = CKEditorPlugin.getByCategory(category);
            if (!plugins.isEmpty()) {
                AccordionPanel panel = createCategoryPanel(category, plugins);
                categoryPanels.put(category, panel);
                accordion.add(panel);
            }
        }

        // Expand first category by default
        if (!categoryPanels.isEmpty()) {
            accordion.open(categoryPanels.get(CKEditorPlugin.Category.CORE));
        }

        // Scroll container
        Div scrollContainer = new Div(accordion);
        scrollContainer.addClassName("plugin-scroll-container");

        list.add(scrollContainer);
        list.setFlexGrow(1, scrollContainer);

        return list;
    }

    private Button createFilterButton(String label, String filter, boolean active) {
        Button btn = new Button(label);
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        btn.addClassName("filter-btn");
        if (active) {
            btn.addClassName("active");
        }

        btn.addClickListener(e -> {
            currentFilter = filter;
            // Update button state
            btn.getParent().ifPresent(parent -> {
                parent.getChildren().forEach(child -> {
                    if (child instanceof Button b) {
                        b.getElement().getClassList().remove("active");
                    }
                });
            });
            btn.addClassName("active");
            applyFilters();
        });

        return btn;
    }

    private AccordionPanel createCategoryPanel(CKEditorPlugin.Category category, Set<CKEditorPlugin> plugins) {
        AccordionPanel panel = new AccordionPanel();
        panel.setSummary(createCategoryTitle(category, plugins.size()));
        panel.addClassName("config-accordion-panel");

        // Create plugin card grid
        FlexLayout cardsContainer = new FlexLayout();
        cardsContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsContainer.addClassName("plugin-cards-container");

        for (CKEditorPlugin plugin : plugins) {
            Div card = createPluginCard(plugin);
            pluginCards.put(plugin, card);
            cardsContainer.add(card);
        }

        panel.add(cardsContainer);

        return panel;
    }

    private Component createCategoryTitle(CKEditorPlugin.Category category, int totalPlugins) {
        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        Icon icon = getCategoryIcon(category).create();
        icon.setSize("20px");
        icon.addClassName("section-icon");

        Span titleSpan = new Span(category.getDisplayName());
        titleSpan.addClassName("section-title-text");

        Span countBadge = new Span("0/" + totalPlugins);
        countBadge.addClassName("category-count-badge");
        countBadge.getElement().setAttribute("data-category", category.name());

        header.add(icon, titleSpan, countBadge);

        return header;
    }

    private VaadinIcon getCategoryIcon(CKEditorPlugin.Category category) {
        return switch (category) {
            case CORE -> VaadinIcon.COG;
            case BASIC_STYLES -> VaadinIcon.TEXT_LABEL;
            case FONT -> VaadinIcon.TEXT_LABEL;
            case PARAGRAPH -> VaadinIcon.PARAGRAPH;
            case LIST -> VaadinIcon.LIST;
            case LINK -> VaadinIcon.LINK;
            case IMAGE -> VaadinIcon.PICTURE;
            case UPLOAD -> VaadinIcon.UPLOAD;
            case TABLE -> VaadinIcon.TABLE;
            case MEDIA -> VaadinIcon.PLAY;
            case CODE -> VaadinIcon.CODE;
            case SPECIAL -> VaadinIcon.STAR;
            case EDITING -> VaadinIcon.EDIT;
            case MENTION -> VaadinIcon.AT;
            case DOCUMENT -> VaadinIcon.FILE_TEXT;
            case HTML -> VaadinIcon.CODE;
            case RESTRICTED -> VaadinIcon.LOCK;
            case CUSTOM -> VaadinIcon.PLUG;
        };
    }

    private Div createPluginCard(CKEditorPlugin plugin) {
        Div card = new Div();
        card.addClassName("plugin-card");
        card.getElement().setAttribute("data-plugin", plugin.name());

        // Plugin name
        Span name = new Span(plugin.getJsName());
        name.addClassName("plugin-card-name");

        // Plugin description
        String desc = getPluginDescription(plugin);
        Span description = new Span(desc);
        description.addClassName("plugin-card-desc");

        // Selection indicator
        Div checkIndicator = new Div();
        checkIndicator.addClassName("plugin-card-check");
        Icon checkIcon = VaadinIcon.CHECK.create();
        checkIcon.setSize("14px");
        checkIndicator.add(checkIcon);

        card.add(checkIndicator, name, description);

        // Click to toggle selection
        card.addClickListener(e -> {
            if (state != null) {
                if (state.hasPlugin(plugin)) {
                    state.removePlugin(plugin);
                    card.removeClassName("selected");
                } else {
                    state.addPlugin(plugin);
                    card.addClassName("selected");
                }
                updateSummary();
                updateCategoryLabels();
            }
        });

        return card;
    }

    private String getPluginDescription(CKEditorPlugin plugin) {
        return switch (plugin) {
            // Core
            case ESSENTIALS -> I18nUtil.get("plugin.desc.essentials");
            case PARAGRAPH -> I18nUtil.get("plugin.desc.paragraph");
            case UNDO -> I18nUtil.get("plugin.desc.undo");
            case CLIPBOARD -> I18nUtil.get("plugin.desc.clipboard");
            case TYPING -> I18nUtil.get("plugin.desc.typing");
            case SELECT_ALL -> I18nUtil.get("plugin.desc.selectAll");
            case WIDGET -> I18nUtil.get("plugin.desc.widget");
            case WIDGET_TOOLBAR_REPOSITORY -> I18nUtil.get("plugin.desc.widgetToolbarRepository");
            case WIDGET_RESIZE -> I18nUtil.get("plugin.desc.widgetResize");
            case WIDGET_TYPE_AROUND -> I18nUtil.get("plugin.desc.widgetTypeAround");
            // Basic Styles
            case BOLD -> I18nUtil.get("plugin.desc.bold");
            case ITALIC -> I18nUtil.get("plugin.desc.italic");
            case UNDERLINE -> I18nUtil.get("plugin.desc.underline");
            case STRIKETHROUGH -> I18nUtil.get("plugin.desc.strikethrough");
            case CODE -> I18nUtil.get("plugin.desc.code");
            case SUBSCRIPT -> I18nUtil.get("plugin.desc.subscript");
            case SUPERSCRIPT -> I18nUtil.get("plugin.desc.superscript");
            // Font
            case FONT_SIZE -> I18nUtil.get("plugin.desc.fontSize");
            case FONT_FAMILY -> I18nUtil.get("plugin.desc.fontFamily");
            case FONT_COLOR -> I18nUtil.get("plugin.desc.fontColor");
            case FONT_BACKGROUND_COLOR -> I18nUtil.get("plugin.desc.fontBackgroundColor");
            // Paragraph
            case HEADING -> I18nUtil.get("plugin.desc.heading");
            case ALIGNMENT -> I18nUtil.get("plugin.desc.alignment");
            case INDENT -> I18nUtil.get("plugin.desc.indent");
            case INDENT_BLOCK -> I18nUtil.get("plugin.desc.indentBlock");
            case BLOCK_QUOTE -> I18nUtil.get("plugin.desc.blockQuote");
            // List
            case LIST -> I18nUtil.get("plugin.desc.list");
            case TODO_LIST -> I18nUtil.get("plugin.desc.todoList");
            // Link
            case LINK -> I18nUtil.get("plugin.desc.link");
            case AUTO_LINK -> I18nUtil.get("plugin.desc.autoLink");
            // Image
            case IMAGE -> I18nUtil.get("plugin.desc.image");
            case IMAGE_TOOLBAR -> I18nUtil.get("plugin.desc.imageToolbar");
            case IMAGE_CAPTION -> I18nUtil.get("plugin.desc.imageCaption");
            case IMAGE_STYLE -> I18nUtil.get("plugin.desc.imageStyle");
            case IMAGE_RESIZE -> I18nUtil.get("plugin.desc.imageResize");
            case IMAGE_UPLOAD -> I18nUtil.get("plugin.desc.imageUpload");
            case IMAGE_INSERT -> I18nUtil.get("plugin.desc.imageInsert");
            case IMAGE_BLOCK -> I18nUtil.get("plugin.desc.imageBlock");
            case IMAGE_INLINE -> I18nUtil.get("plugin.desc.imageInline");
            case LINK_IMAGE -> I18nUtil.get("plugin.desc.linkImage");
            case AUTO_IMAGE -> I18nUtil.get("plugin.desc.autoImage");
            case EASY_IMAGE -> I18nUtil.get("plugin.desc.easyImage");
            // Upload
            case SIMPLE_UPLOAD_ADAPTER -> I18nUtil.get("plugin.desc.simpleUploadAdapter");
            case CLOUD_SERVICES -> I18nUtil.get("plugin.desc.cloudServices");
            case CLOUD_SERVICES_CORE -> I18nUtil.get("plugin.desc.cloudServicesCore");
            case CLOUD_SERVICES_UPLOAD_ADAPTER -> I18nUtil.get("plugin.desc.cloudServicesUploadAdapter");
            // Table
            case TABLE -> I18nUtil.get("plugin.desc.table");
            case TABLE_TOOLBAR -> I18nUtil.get("plugin.desc.tableToolbar");
            case TABLE_PROPERTIES -> I18nUtil.get("plugin.desc.tableProperties");
            case TABLE_CELL_PROPERTIES -> I18nUtil.get("plugin.desc.tableCellProperties");
            case TABLE_CAPTION -> I18nUtil.get("plugin.desc.tableCaption");
            case TABLE_COLUMN_RESIZE -> I18nUtil.get("plugin.desc.tableColumnResize");
            // Media
            case MEDIA_EMBED -> I18nUtil.get("plugin.desc.mediaEmbed");
            case HTML_EMBED -> I18nUtil.get("plugin.desc.htmlEmbed");
            // Code
            case CODE_BLOCK -> I18nUtil.get("plugin.desc.codeBlock");
            // Special
            case HORIZONTAL_LINE -> I18nUtil.get("plugin.desc.horizontalLine");
            case PAGE_BREAK -> I18nUtil.get("plugin.desc.pageBreak");
            case SPECIAL_CHARACTERS -> I18nUtil.get("plugin.desc.specialCharacters");
            case SPECIAL_CHARACTERS_ESSENTIALS -> I18nUtil.get("plugin.desc.specialCharactersEssentials");
            case EMOJI -> I18nUtil.get("plugin.desc.emoji");
            case EMOJI_PICKER -> I18nUtil.get("plugin.desc.emojiPicker");
            // Editing
            case AUTOFORMAT -> I18nUtil.get("plugin.desc.autoformat");
            case TEXT_TRANSFORMATION -> I18nUtil.get("plugin.desc.textTransformation");
            case FIND_AND_REPLACE -> I18nUtil.get("plugin.desc.findAndReplace");
            case REMOVE_FORMAT -> I18nUtil.get("plugin.desc.removeFormat");
            case SOURCE_EDITING -> I18nUtil.get("plugin.desc.sourceEditing");
            case SHOW_BLOCKS -> I18nUtil.get("plugin.desc.showBlocks");
            case HIGHLIGHT -> I18nUtil.get("plugin.desc.highlight");
            case TEXT_PART_LANGUAGE -> I18nUtil.get("plugin.desc.textPartLanguage");
            case MINIMAP -> I18nUtil.get("plugin.desc.minimap");
            // Mention
            case MENTION -> I18nUtil.get("plugin.desc.mention");
            default -> "";
        };
    }

    private VerticalLayout createSummaryPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setPadding(true);
        panel.addClassName("plugin-summary-panel");

        // Title
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Span title = new Span(I18nUtil.get("step3.filter.selected"));
        title.addClassName("summary-title");

        selectedCountLabel = new Span("0");
        selectedCountLabel.addClassName("selected-count");

        header.add(title, selectedCountLabel);

        // Quick actions
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.addClassName("summary-actions");

        Button selectAllBtn = new Button(I18nUtil.get("step3.selectAll"), e -> selectAllPlugins());
        selectAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        selectAllBtn.addClassName("summary-action-btn");

        Button clearBtn = new Button(I18nUtil.get("step3.deselectAll"), e -> clearPlugins());
        clearBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        clearBtn.addClassName("summary-action-btn");

        Button resetBtn = new Button(I18nUtil.get("step3.reset"), e -> resetToPreset());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        resetBtn.addClassName("summary-action-btn");

        actions.add(selectAllBtn, clearBtn, resetBtn);

        // Selected plugins list
        Div selectedList = new Div();
        selectedList.addClassName("selected-plugins-list");
        selectedList.setId("selected-plugins-list");

        // Dependency hint
        Div dependencyHint = new Div();
        dependencyHint.addClassName("dependency-hint");
        Icon infoIcon = VaadinIcon.INFO_CIRCLE.create();
        infoIcon.setSize("14px");
        Span hintText = new Span(I18nUtil.get("step3.description"));
        dependencyHint.add(infoIcon, hintText);

        panel.add(header, actions, selectedList, dependencyHint);
        panel.setFlexGrow(1, selectedList);

        return panel;
    }

    private void applyFilters() {
        String searchTerm = searchField.getValue().toLowerCase();

        pluginCards.forEach((plugin, card) -> {
            boolean matchesSearch = searchTerm.isEmpty() ||
                plugin.getJsName().toLowerCase().contains(searchTerm) ||
                plugin.name().toLowerCase().contains(searchTerm) ||
                getPluginDescription(plugin).toLowerCase().contains(searchTerm);

            boolean matchesFilter = switch (currentFilter) {
                case "selected" -> state != null && state.hasPlugin(plugin);
                case "core" -> plugin.getCategory() == CKEditorPlugin.Category.CORE;
                default -> true;
            };

            card.setVisible(matchesSearch && matchesFilter);
        });
    }

    private void selectAllPlugins() {
        if (state == null) return;
        state.setPlugins(Arrays.asList(CKEditorPlugin.values()));
        pluginCards.values().forEach(card -> card.addClassName("selected"));
        updateSummary();
        updateCategoryLabels();
    }

    private void clearPlugins() {
        if (state == null) return;
        // Keep essential core plugins
        Set<CKEditorPlugin> essential = Set.of(CKEditorPlugin.ESSENTIALS, CKEditorPlugin.PARAGRAPH);
        state.setPlugins(essential);
        pluginCards.forEach((plugin, card) -> {
            if (essential.contains(plugin)) {
                card.addClassName("selected");
            } else {
                card.removeClassName("selected");
            }
        });
        updateSummary();
        updateCategoryLabels();
    }

    private void resetToPreset() {
        if (state == null) return;
        state.initFromPreset(state.getPreset());
        refreshFromState();
    }

    private void updateSummary() {
        if (state == null || selectedCountLabel == null) return;

        int count = state.getSelectedPlugins().size();
        selectedCountLabel.setText(String.valueOf(count));

        // Update selected list
        content.getElement().executeJs(
            "var list = document.getElementById('selected-plugins-list');" +
            "if (list) { list.innerHTML = $0; }",
            buildSelectedPluginsHtml()
        );
    }

    private String buildSelectedPluginsHtml() {
        if (state == null) return "";

        StringBuilder html = new StringBuilder();
        for (CKEditorPlugin plugin : state.getSelectedPlugins()) {
            html.append("<div class='selected-plugin-chip'>")
                .append("<span>").append(escapeHtml(plugin.getJsName())).append("</span>")
                .append("</div>");
        }
        return html.toString();
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }

    private void updateCategoryLabels() {
        if (state == null) return;

        for (var entry : categoryPanels.entrySet()) {
            CKEditorPlugin.Category category = entry.getKey();

            Set<CKEditorPlugin> plugins = CKEditorPlugin.getByCategory(category);
            long selectedCount = plugins.stream()
                .filter(p -> state.hasPlugin(p))
                .count();

            String countText = selectedCount + "/" + plugins.size();

            // Update badge
            content.getElement().executeJs(
                "var badge = document.querySelector('[data-category=\"" + category.name() + "\"]');" +
                "if (badge) { badge.textContent = $0; }",
                countText
            );
        }
    }

    private void refreshFromState() {
        if (state == null) return;

        pluginCards.forEach((plugin, card) -> {
            if (state.hasPlugin(plugin)) {
                card.addClassName("selected");
            } else {
                card.removeClassName("selected");
            }
        });
        updateSummary();
        updateCategoryLabels();
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;
        // Ensure content is initialized
        getContent();
        refreshFromState();
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        if (state.getSelectedPlugins().isEmpty()) {
            return ValidationResult.error(I18nUtil.get("validation.selectPlugin"));
        }

        // Check if essential core plugins are included
        if (!state.hasPlugin(CKEditorPlugin.ESSENTIALS)) {
            return ValidationResult.warning(I18nUtil.get("validation.noPluginWarning"));
        }

        return ValidationResult.ok();
    }

    @Override
    public boolean isSkippable() {
        // Preset mode can skip plugin selection
        return state != null && state.getMode() == BuilderState.Mode.PRESET;
    }
}
