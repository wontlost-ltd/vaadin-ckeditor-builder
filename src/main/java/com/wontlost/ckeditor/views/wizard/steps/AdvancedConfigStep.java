package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.BuilderState.CustomPluginConfig;
import com.wontlost.ckeditor.domain.BuilderState.DependencyMode;
import com.wontlost.ckeditor.domain.BuilderState.FallbackMode;
import com.wontlost.ckeditor.domain.BuilderState.SanitizationPolicy;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Step 4: Advanced Configuration
 * Configure Premium features, upload, behavior, size and other advanced options
 */
public class AdvancedConfigStep implements WizardStep {

    private BuilderState state;
    private VerticalLayout content;

    // Premium configuration components
    private FlexLayout premiumPluginsList;
    private String[][] premiumPluginDefinitions;

    // Editor config components
    private TextField placeholderField;
    private TextField fontSizesField;
    private Checkbox allowAnyFontSizeCheckbox;
    private TextField fontFamiliesField;
    private TextField linkDefaultProtocolField;
    private Checkbox addTargetToExternalLinksCheckbox;

    // Upload configuration components
    private TextField simpleUploadUrlField;
    private Checkbox simpleUploadWithCredentialsCheckbox;
    private NumberField uploadMaxFileSizeField;
    private TextField uploadAllowedMimeTypesField;

    // Behavior configuration components
    private Checkbox autosaveEnabledCheckbox;
    private IntegerField autosaveIntervalField;
    private Checkbox readOnlyCheckbox;
    private Checkbox viewOnlyCheckbox;
    private Checkbox ghsEnabledCheckbox;
    private Checkbox minimapEnabledCheckbox;
    private ComboBox<FallbackMode> fallbackModeComboBox;
    private ComboBox<SanitizationPolicy> sanitizationPolicyComboBox;
    private ComboBox<DependencyMode> dependencyModeComboBox;

    // Size configuration components
    private TextField editorWidthField;
    private TextField editorHeightField;
    private TextArea initialValueField;

    @Override
    public String getId() { return "advanced-config"; }

    @Override
    public String getTitle() { return I18nUtil.get("step6.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step6.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.COG; }

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
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.addClassName("advanced-config-step");

        // Use accordion to organize configuration groups
        Accordion accordion = new Accordion();
        accordion.setWidthFull();

        // 1. Premium configuration panel
        AccordionPanel premiumPanel = new AccordionPanel();
        premiumPanel.setSummary(createSectionTitle(VaadinIcon.STAR, I18nUtil.get("step6.premium"), true));
        premiumPanel.add(createPremiumSection());
        premiumPanel.addClassName("config-accordion-panel");
        accordion.add(premiumPanel);

        // 2. Editor configuration panel
        AccordionPanel editorConfigPanel = new AccordionPanel();
        editorConfigPanel.setSummary(createSectionTitle(VaadinIcon.EDIT, I18nUtil.get("step6.editorConfig"), false));
        editorConfigPanel.add(createEditorConfigSection());
        editorConfigPanel.addClassName("config-accordion-panel");
        accordion.add(editorConfigPanel);

        // 3. Upload configuration panel
        AccordionPanel uploadPanel = new AccordionPanel();
        uploadPanel.setSummary(createSectionTitle(VaadinIcon.UPLOAD, I18nUtil.get("step6.upload"), false));
        uploadPanel.add(createUploadSection());
        uploadPanel.addClassName("config-accordion-panel");
        accordion.add(uploadPanel);

        // 4. Behavior configuration panel
        AccordionPanel behaviorPanel = new AccordionPanel();
        behaviorPanel.setSummary(createSectionTitle(VaadinIcon.SLIDERS, I18nUtil.get("step6.behavior"), false));
        behaviorPanel.add(createBehaviorSection());
        behaviorPanel.addClassName("config-accordion-panel");
        accordion.add(behaviorPanel);

        // 5. Size configuration panel
        AccordionPanel sizePanel = new AccordionPanel();
        sizePanel.setSummary(createSectionTitle(VaadinIcon.VIEWPORT, I18nUtil.get("step6.size"), false));
        sizePanel.add(createSizeSection());
        sizePanel.addClassName("config-accordion-panel");
        accordion.add(sizePanel);

        // Expand Premium panel by default
        accordion.open(premiumPanel);

        layout.add(accordion);

        return layout;
    }

    private Component createSectionTitle(VaadinIcon iconType, String title, boolean isPremium) {
        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        Icon icon = iconType.create();
        icon.setSize("20px");
        icon.addClassName("section-icon");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("section-title-text");

        header.add(icon, titleSpan);

        if (isPremium) {
            Span premiumBadge = new Span("Premium");
            premiumBadge.addClassName("premium-badge");
            header.add(premiumBadge);
        }

        return header;
    }

    private VerticalLayout createPremiumSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.addClassName("config-section");

        // Premium plugin selection
        Div premiumPluginsGroup = new Div();
        premiumPluginsGroup.addClassName("form-group");

        // Title and action buttons row
        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);

        H4 pluginsTitle = new H4(I18nUtil.get("step6.premiumPlugins"));
        pluginsTitle.addClassName("form-group-title");

        // Select all / Deselect all buttons
        HorizontalLayout actionButtons = new HorizontalLayout();
        actionButtons.setSpacing(true);

        Button selectAllBtn = new Button(I18nUtil.get("step3.selectAll"), e -> selectAllPremiumPlugins());
        selectAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        selectAllBtn.addClassName("summary-action-btn");

        Button deselectAllBtn = new Button(I18nUtil.get("step3.deselectAll"), e -> deselectAllPremiumPlugins());
        deselectAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        deselectAllBtn.addClassName("summary-action-btn");

        actionButtons.add(selectAllBtn, deselectAllBtn);
        titleRow.add(pluginsTitle, actionButtons);

        // Use FlexLayout for card grid layout, consistent with PluginsStep
        premiumPluginsList = new FlexLayout();
        premiumPluginsList.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        premiumPluginsList.addClassName("plugin-cards-container");

        // Predefined Premium plugins - using i18n keys for descriptions
        premiumPluginDefinitions = new String[][] {
            {"ExportPdf", "premium.desc.exportPdf", "exportPdf"},
            {"ExportWord", "premium.desc.exportWord", "exportWord"},
            {"ImportWord", "premium.desc.importWord", "importWord"},
            {"FormatPainter", "premium.desc.formatPainter", "formatPainter"},
            {"SlashCommand", "premium.desc.slashCommand", ""},
            {"TableOfContents", "premium.desc.tableOfContents", "tableOfContents"},
            {"DocumentOutline", "premium.desc.documentOutline", ""},
            {"Template", "premium.desc.template", "insertTemplate"},
            {"CaseChange", "premium.desc.caseChange", "caseChange"},
            {"MergeFields", "premium.desc.mergeFields", "mergeFields"},
            {"Pagination", "premium.desc.pagination", "pageBreak"},
            {"AIAssistant", "premium.desc.aiAssistant", "aiAssistant"},
            {"Comments", "premium.desc.comments", "comment"},
            {"TrackChanges", "premium.desc.trackChanges", "trackChanges"},
            {"RevisionHistory", "premium.desc.revisionHistory", "revisionHistory"}
        };

        for (String[] pluginDef : premiumPluginDefinitions) {
            // pluginDef[1] is now an i18n key, so we use I18nUtil.get() to get the translated description
            Div pluginCard = createPremiumPluginCard(pluginDef[0], I18nUtil.get(pluginDef[1]), pluginDef[2]);
            premiumPluginsList.add(pluginCard);
        }

        // Premium feature hint
        Div premiumHint = new Div();
        premiumHint.addClassName("security-hint");
        Icon infoIcon = VaadinIcon.INFO_CIRCLE.create();
        infoIcon.setSize("14px");
        Span hintText = new Span(I18nUtil.get("step6.premium.hint"));
        premiumHint.add(infoIcon, hintText);

        premiumPluginsGroup.add(premiumHint, titleRow, premiumPluginsList);

        section.add(premiumPluginsGroup);

        return section;
    }

    /**
     * Select all Premium plugins
     */
    private void selectAllPremiumPlugins() {
        if (state == null || premiumPluginDefinitions == null) return;

        for (String[] pluginDef : premiumPluginDefinitions) {
            String name = pluginDef[0];
            String toolbarItem = pluginDef[2];

            // Check if already selected
            boolean alreadySelected = state.getPremiumPlugins().stream()
                .anyMatch(p -> p.getName().equals(name));

            if (!alreadySelected) {
                CustomPluginConfig config = CustomPluginConfig.fromPremium(name);
                if (toolbarItem != null && !toolbarItem.isEmpty()) {
                    config.setToolbarItems(List.of(toolbarItem));
                }
                state.addPremiumPlugin(config);
            }
        }

        updatePremiumPluginsSelection();
    }

    /**
     * Deselect all Premium plugins
     */
    private void deselectAllPremiumPlugins() {
        if (state == null) return;

        // Clear all Premium plugins
        List<CustomPluginConfig> toRemove = new ArrayList<>(state.getPremiumPlugins());
        for (CustomPluginConfig config : toRemove) {
            state.removePremiumPlugin(config);
        }

        updatePremiumPluginsSelection();
    }

    /**
     * Create Premium plugin card, style consistent with PluginsStep plugin cards
     */
    private Div createPremiumPluginCard(String name, String description, String toolbarItem) {
        Div card = new Div();
        card.addClassName("plugin-card");
        card.getElement().setAttribute("data-plugin", name);

        // Plugin name
        Span nameSpan = new Span(name);
        nameSpan.addClassName("plugin-card-name");

        // Plugin description
        Span descSpan = new Span(description);
        descSpan.addClassName("plugin-card-desc");

        // Selection indicator
        Div checkIndicator = new Div();
        checkIndicator.addClassName("plugin-card-check");
        Icon checkIcon = VaadinIcon.CHECK.create();
        checkIcon.setSize("14px");
        checkIndicator.add(checkIcon);

        card.add(checkIndicator, nameSpan, descSpan);

        // Click to toggle selection
        card.addClickListener(e -> {
            if (state != null) {
                boolean isCurrentlySelected = card.getClassNames().contains("selected");
                if (isCurrentlySelected) {
                    // Deselect
                    state.getPremiumPlugins().stream()
                        .filter(p -> p.getName().equals(name))
                        .findFirst()
                        .ifPresent(state::removePremiumPlugin);
                    card.removeClassName("selected");
                } else {
                    // Select
                    CustomPluginConfig config = CustomPluginConfig.fromPremium(name);
                    if (toolbarItem != null && !toolbarItem.isEmpty()) {
                        config.setToolbarItems(List.of(toolbarItem));
                    }
                    state.addPremiumPlugin(config);
                    card.addClassName("selected");
                }
            }
        });

        return card;
    }

    private VerticalLayout createEditorConfigSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.addClassName("config-section");

        // Placeholder
        placeholderField = new TextField(I18nUtil.get("step6.placeholder"));
        placeholderField.setWidthFull();
        placeholderField.setPlaceholder(I18nUtil.get("step6.placeholder.hint"));
        placeholderField.addValueChangeListener(e -> {
            if (state != null) {
                state.setPlaceholder(e.getValue());
            }
        });

        // Font Sizes
        fontSizesField = new TextField(I18nUtil.get("step6.fontSizes"));
        fontSizesField.setWidthFull();
        fontSizesField.setPlaceholder("tiny,small,default,big,huge");
        fontSizesField.setHelperText(I18nUtil.get("step6.fontSizes.helper"));
        fontSizesField.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null && !e.getValue().isEmpty()) {
                state.setFontSizes(Arrays.asList(e.getValue().split(",")));
            }
        });

        allowAnyFontSizeCheckbox = new Checkbox(I18nUtil.get("step6.allowAnyFontSize"));
        allowAnyFontSizeCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setAllowAnyFontSize(e.getValue());
            }
        });

        // Font Families
        fontFamiliesField = new TextField(I18nUtil.get("step6.fontFamilies"));
        fontFamiliesField.setWidthFull();
        fontFamiliesField.setPlaceholder("Arial,Georgia,Times New Roman");
        fontFamiliesField.setHelperText(I18nUtil.get("step6.fontFamilies.helper"));
        fontFamiliesField.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null && !e.getValue().isEmpty()) {
                state.setFontFamilies(Arrays.asList(e.getValue().split(",")));
            }
        });

        // Link configuration
        HorizontalLayout linkRow = new HorizontalLayout();
        linkRow.setWidthFull();
        linkRow.setAlignItems(FlexComponent.Alignment.BASELINE);

        linkDefaultProtocolField = new TextField(I18nUtil.get("step6.linkProtocol"));
        linkDefaultProtocolField.setValue("https://");
        linkDefaultProtocolField.setWidth("150px");
        linkDefaultProtocolField.addValueChangeListener(e -> {
            if (state != null) {
                state.setLinkDefaultProtocol(e.getValue());
            }
        });

        addTargetToExternalLinksCheckbox = new Checkbox(I18nUtil.get("step6.openInNewTab"));
        addTargetToExternalLinksCheckbox.setValue(true);
        addTargetToExternalLinksCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setAddTargetToExternalLinks(e.getValue());
            }
        });

        linkRow.add(linkDefaultProtocolField, addTargetToExternalLinksCheckbox);

        section.add(placeholderField, fontSizesField, allowAnyFontSizeCheckbox,
                   fontFamiliesField, linkRow);

        return section;
    }

    private VerticalLayout createUploadSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.addClassName("config-section");

        // Upload URL
        simpleUploadUrlField = new TextField(I18nUtil.get("step6.uploadUrl"));
        simpleUploadUrlField.setWidthFull();
        simpleUploadUrlField.setPlaceholder("/api/upload");
        simpleUploadUrlField.addValueChangeListener(e -> {
            if (state != null) {
                state.setSimpleUploadUrl(e.getValue());
            }
        });

        // With Credentials
        simpleUploadWithCredentialsCheckbox = new Checkbox(I18nUtil.get("step6.withCredentials"));
        simpleUploadWithCredentialsCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setSimpleUploadWithCredentials(e.getValue());
            }
        });

        // Max File Size
        uploadMaxFileSizeField = new NumberField(I18nUtil.get("step6.maxFileSize"));
        uploadMaxFileSizeField.setValue(10.0);
        uploadMaxFileSizeField.setMin(1);
        uploadMaxFileSizeField.setMax(100);
        uploadMaxFileSizeField.setStep(1);
        uploadMaxFileSizeField.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null) {
                state.setUploadMaxFileSize((long)(e.getValue() * 1_000_000));
            }
        });

        // Allowed MIME Types
        uploadAllowedMimeTypesField = new TextField(I18nUtil.get("step6.allowedMimeTypes"));
        uploadAllowedMimeTypesField.setWidthFull();
        uploadAllowedMimeTypesField.setPlaceholder("image/png,image/jpeg,image/gif");
        uploadAllowedMimeTypesField.setHelperText(I18nUtil.get("step6.allowedMimeTypes.helper"));
        uploadAllowedMimeTypesField.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null && !e.getValue().isEmpty()) {
                state.setUploadAllowedMimeTypes(Arrays.asList(e.getValue().split(",")));
            }
        });

        section.add(simpleUploadUrlField, simpleUploadWithCredentialsCheckbox,
                   uploadMaxFileSizeField, uploadAllowedMimeTypesField);

        return section;
    }

    private VerticalLayout createBehaviorSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.addClassName("config-section");

        // Autosave
        HorizontalLayout autosaveRow = new HorizontalLayout();
        autosaveRow.setWidthFull();
        autosaveRow.setAlignItems(FlexComponent.Alignment.BASELINE);

        autosaveEnabledCheckbox = new Checkbox(I18nUtil.get("step6.autosave"));
        autosaveEnabledCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setAutosaveEnabled(e.getValue());
                autosaveIntervalField.setEnabled(e.getValue());
            }
        });

        autosaveIntervalField = new IntegerField(I18nUtil.get("step6.autosaveInterval"));
        autosaveIntervalField.setValue(5000);
        autosaveIntervalField.setMin(1000);
        autosaveIntervalField.setMax(60000);
        autosaveIntervalField.setStep(1000);
        autosaveIntervalField.setEnabled(false);
        autosaveIntervalField.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null) {
                state.setAutosaveInterval(e.getValue());
            }
        });

        autosaveRow.add(autosaveEnabledCheckbox, autosaveIntervalField);

        // Read Only / View Only
        HorizontalLayout readOnlyRow = new HorizontalLayout();
        readOnlyRow.setWidthFull();

        readOnlyCheckbox = new Checkbox(I18nUtil.get("step6.readOnly"));
        readOnlyCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setReadOnly(e.getValue());
            }
        });

        viewOnlyCheckbox = new Checkbox(I18nUtil.get("step6.viewOnly"));
        viewOnlyCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setViewOnly(e.getValue());
                if (e.getValue()) {
                    readOnlyCheckbox.setValue(true);
                    readOnlyCheckbox.setEnabled(false);
                } else {
                    readOnlyCheckbox.setEnabled(true);
                }
            }
        });

        readOnlyRow.add(readOnlyCheckbox, viewOnlyCheckbox);

        // GHS and Minimap
        HorizontalLayout featuresRow = new HorizontalLayout();
        featuresRow.setWidthFull();

        ghsEnabledCheckbox = new Checkbox(I18nUtil.get("step6.ghs"));
        ghsEnabledCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setGhsEnabled(e.getValue());
            }
        });

        minimapEnabledCheckbox = new Checkbox(I18nUtil.get("step6.minimap"));
        minimapEnabledCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setMinimapEnabled(e.getValue());
            }
        });

        featuresRow.add(ghsEnabledCheckbox, minimapEnabledCheckbox);

        // Fallback Mode
        fallbackModeComboBox = new ComboBox<>(I18nUtil.get("step6.fallbackMode"));
        fallbackModeComboBox.setItems(FallbackMode.values());
        fallbackModeComboBox.setItemLabelGenerator(FallbackMode::getDisplayName);
        fallbackModeComboBox.setValue(FallbackMode.TEXTAREA);
        fallbackModeComboBox.setWidthFull();
        fallbackModeComboBox.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null) {
                state.setFallbackMode(e.getValue());
            }
        });

        // Sanitization Policy
        sanitizationPolicyComboBox = new ComboBox<>(I18nUtil.get("step6.sanitizationPolicy"));
        sanitizationPolicyComboBox.setItems(SanitizationPolicy.values());
        sanitizationPolicyComboBox.setItemLabelGenerator(SanitizationPolicy::getDisplayName);
        sanitizationPolicyComboBox.setValue(SanitizationPolicy.RELAXED);
        sanitizationPolicyComboBox.setWidthFull();
        sanitizationPolicyComboBox.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null) {
                state.setSanitizationPolicy(e.getValue());
            }
        });

        // Dependency Mode
        dependencyModeComboBox = new ComboBox<>(I18nUtil.get("step6.dependencyMode"));
        dependencyModeComboBox.setItems(DependencyMode.values());
        dependencyModeComboBox.setItemLabelGenerator(DependencyMode::getDisplayName);
        dependencyModeComboBox.setValue(DependencyMode.AUTO_RESOLVE);
        dependencyModeComboBox.setWidthFull();
        dependencyModeComboBox.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null) {
                state.setDependencyMode(e.getValue());
            }
        });

        section.add(autosaveRow, readOnlyRow, featuresRow,
                   fallbackModeComboBox, sanitizationPolicyComboBox, dependencyModeComboBox);

        return section;
    }

    private VerticalLayout createSizeSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.addClassName("config-section");

        // Width and Height
        HorizontalLayout sizeRow = new HorizontalLayout();
        sizeRow.setWidthFull();

        editorWidthField = new TextField(I18nUtil.get("step6.width"));
        editorWidthField.setValue("100%");
        editorWidthField.setPlaceholder("100%, 800px");
        editorWidthField.addValueChangeListener(e -> {
            if (state != null) {
                state.setEditorWidth(e.getValue());
            }
        });

        editorHeightField = new TextField(I18nUtil.get("step6.height"));
        editorHeightField.setValue("400px");
        editorHeightField.setPlaceholder("400px, 50vh");
        editorHeightField.addValueChangeListener(e -> {
            if (state != null) {
                state.setEditorHeight(e.getValue());
            }
        });

        sizeRow.add(editorWidthField, editorHeightField);

        // Initial Value
        initialValueField = new TextArea(I18nUtil.get("step6.initialValue"));
        initialValueField.setWidthFull();
        initialValueField.setHeight("150px");
        initialValueField.setPlaceholder("<p>Hello World!</p>");
        initialValueField.addValueChangeListener(e -> {
            if (state != null) {
                state.setInitialValue(e.getValue());
            }
        });

        section.add(sizeRow, initialValueField);

        return section;
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;
        // Ensure content is initialized before refreshFromState accesses fields
        getContent();
        refreshFromState();
    }

    private void refreshFromState() {
        if (state == null) return;

        // Premium
        updatePremiumPluginsSelection();

        // Editor Config
        placeholderField.setValue(state.getPlaceholder() != null ? state.getPlaceholder() : "");
        fontSizesField.setValue(String.join(",", state.getFontSizes()));
        allowAnyFontSizeCheckbox.setValue(state.isAllowAnyFontSize());
        fontFamiliesField.setValue(String.join(",", state.getFontFamilies()));
        linkDefaultProtocolField.setValue(state.getLinkDefaultProtocol());
        addTargetToExternalLinksCheckbox.setValue(state.isAddTargetToExternalLinks());

        // Upload
        simpleUploadUrlField.setValue(state.getSimpleUploadUrl() != null ? state.getSimpleUploadUrl() : "");
        simpleUploadWithCredentialsCheckbox.setValue(state.isSimpleUploadWithCredentials());
        uploadMaxFileSizeField.setValue((double)state.getUploadMaxFileSize() / 1_000_000);
        uploadAllowedMimeTypesField.setValue(String.join(",", state.getUploadAllowedMimeTypes()));

        // Behavior
        autosaveEnabledCheckbox.setValue(state.isAutosaveEnabled());
        autosaveIntervalField.setValue(state.getAutosaveInterval());
        autosaveIntervalField.setEnabled(state.isAutosaveEnabled());
        readOnlyCheckbox.setValue(state.isReadOnly());
        viewOnlyCheckbox.setValue(state.isViewOnly());
        readOnlyCheckbox.setEnabled(!state.isViewOnly());
        ghsEnabledCheckbox.setValue(state.isGhsEnabled());
        minimapEnabledCheckbox.setValue(state.isMinimapEnabled());
        fallbackModeComboBox.setValue(state.getFallbackMode());
        sanitizationPolicyComboBox.setValue(state.getSanitizationPolicy());
        dependencyModeComboBox.setValue(state.getDependencyMode());

        // Size
        editorWidthField.setValue(state.getEditorWidth());
        editorHeightField.setValue(state.getEditorHeight());
        initialValueField.setValue(state.getInitialValue() != null ? state.getInitialValue() : "");
    }

    private void updatePremiumPluginsSelection() {
        List<String> enabledPlugins = new ArrayList<>();
        for (CustomPluginConfig config : state.getPremiumPlugins()) {
            enabledPlugins.add(config.getName());
        }

        premiumPluginsList.getChildren().forEach(component -> {
            if (component instanceof Div card) {
                String pluginName = card.getElement().getAttribute("data-plugin");
                if (pluginName != null) {
                    if (enabledPlugins.contains(pluginName)) {
                        card.addClassName("selected");
                    } else {
                        card.removeClassName("selected");
                    }
                }
            }
        });
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        // Check upload URL format
        String uploadUrl = state.getSimpleUploadUrl();
        if (uploadUrl != null && !uploadUrl.isEmpty()) {
            if (!uploadUrl.startsWith("/") && !uploadUrl.startsWith("http")) {
                return ValidationResult.warning(I18nUtil.get("validation.invalidUploadUrl"));
            }
        }

        // Check if Minimap is used in Decoupled mode
        if (state.isMinimapEnabled() && state.getEditorType() != com.wontlost.ckeditor.CKEditorType.DECOUPLED) {
            return ValidationResult.warning(I18nUtil.get("validation.minimapDecoupled"));
        }

        return ValidationResult.ok();
    }

    @Override
    public boolean isSkippable() {
        return true; // Advanced config can be skipped, using defaults
    }
}
