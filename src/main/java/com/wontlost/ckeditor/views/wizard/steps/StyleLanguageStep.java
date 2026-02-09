package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.wontlost.ckeditor.CKEditorPreset;
import com.wontlost.ckeditor.CKEditorTheme;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Step 5: Style and Language Configuration
 * User configures theme, language, and custom CSS
 */
public class StyleLanguageStep implements WizardStep {

    private BuilderState state;
    private VerticalLayout content;
    private final Map<CKEditorTheme, Div> themeCards = new HashMap<>();
    private ComboBox<String> languageSelector;
    private TextArea customCssArea;
    private Div documentOptionsSection;
    private Checkbox documentOutlineCheckbox;
    private Checkbox minimapCheckbox;

    @Override
    public String getId() { return "style-language"; }

    @Override
    public String getTitle() { return I18nUtil.get("step5.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step5.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.PAINT_ROLL; }

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
        layout.addClassName("style-language-step");

        // Theme selection
        Div themeSection = createThemeSection();

        // Language selection
        Div languageSection = createLanguageSection();

        // Document preset specific options
        documentOptionsSection = createDocumentOptionsSection();

        // Custom CSS (Open Source feature)
        Div cssSection = createCssSection();

        layout.add(themeSection, languageSection, documentOptionsSection, cssSection);

        return layout;
    }

    private Div createThemeSection() {
        Div section = new Div();
        section.addClassName("config-section");
        section.setMaxWidth("800px");

        H4 title = new H4(I18nUtil.get("step5.theme"));
        title.addClassName("section-title");

        Span desc = new Span(I18nUtil.get("step5.description"));
        desc.addClassName("section-desc");

        HorizontalLayout themeCards = new HorizontalLayout();
        themeCards.setWidthFull();
        themeCards.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        themeCards.addClassName("theme-cards");

        // Light 主题
        Div lightCard = createThemeCard(
            CKEditorTheme.LIGHT,
            VaadinIcon.SUN_O,
            I18nUtil.get("step5.theme.light"),
            I18nUtil.get("step5.theme.light.desc")
        );

        // Dark 主题
        Div darkCard = createThemeCard(
            CKEditorTheme.DARK,
            VaadinIcon.MOON_O,
            I18nUtil.get("step5.theme.dark"),
            I18nUtil.get("step5.theme.dark.desc")
        );

        // Auto 主题
        Div autoCard = createThemeCard(
            CKEditorTheme.AUTO,
            VaadinIcon.ADJUST,
            I18nUtil.get("step5.theme.auto"),
            I18nUtil.get("step5.theme.auto.desc")
        );

        this.themeCards.put(CKEditorTheme.LIGHT, lightCard);
        this.themeCards.put(CKEditorTheme.DARK, darkCard);
        this.themeCards.put(CKEditorTheme.AUTO, autoCard);

        themeCards.add(lightCard, darkCard, autoCard);

        section.add(title, desc, themeCards);

        return section;
    }

    private Div createThemeCard(CKEditorTheme theme, VaadinIcon iconType,
                                 String title, String description) {
        Div card = new Div();
        card.addClassName("theme-card");
        card.getElement().setAttribute("data-theme", theme.name());

        // Preview area
        Div preview = new Div();
        preview.addClassName("theme-preview");
        preview.addClassName("theme-preview-" + theme.name().toLowerCase());

        // Mock editor appearance
        Div mockEditor = new Div();
        mockEditor.addClassName("mock-mini-editor");

        Div mockToolbar = new Div();
        mockToolbar.addClassName("mock-mini-toolbar");

        Div mockContent = new Div();
        mockContent.addClassName("mock-mini-content");

        mockEditor.add(mockToolbar, mockContent);
        preview.add(mockEditor);

        // Icon
        Icon icon = iconType.create();
        icon.setSize("20px");
        icon.addClassName("theme-icon");

        // Title
        Span titleSpan = new Span(title);
        titleSpan.addClassName("theme-title");

        // Description
        Span descSpan = new Span(description);
        descSpan.addClassName("theme-desc");

        card.add(preview, icon, titleSpan, descSpan);

        // Click to select
        card.addClickListener(e -> selectTheme(theme));

        return card;
    }

    private Div createLanguageSection() {
        Div section = new Div();
        section.addClassName("config-section");
        section.setMaxWidth("800px");

        H4 title = new H4(I18nUtil.get("step5.language"));
        title.addClassName("section-title");

        Span desc = new Span(I18nUtil.get("step5.language.desc"));
        desc.addClassName("section-desc");

        languageSelector = new ComboBox<>();
        languageSelector.setItems(
            "en", "zh-cn", "zh", "ja", "ko",
            "de", "fr", "es", "pt", "ru", "ar"
        );
        languageSelector.setItemLabelGenerator(this::getLanguageName);
        languageSelector.setWidthFull();
        languageSelector.setMaxWidth("400px");
        languageSelector.addClassName("language-selector");
        languageSelector.addValueChangeListener(e -> {
            if (state != null && e.getValue() != null) {
                state.setLanguage(e.getValue());
            }
        });

        // Quick language shortcut buttons
        HorizontalLayout quickLangs = new HorizontalLayout();
        quickLangs.addClassName("quick-langs");

        String[] commonLangs = {"en", "zh-cn", "ja", "de", "fr"};
        for (String lang : commonLangs) {
            Div chip = new Div();
            chip.addClassName("lang-chip");
            chip.setText(getLanguageFlag(lang) + " " + getLanguageShortName(lang));
            chip.addClickListener(e -> {
                languageSelector.setValue(lang);
            });
            quickLangs.add(chip);
        }

        section.add(title, desc, languageSelector, quickLangs);

        return section;
    }

    private Div createDocumentOptionsSection() {
        Div section = new Div();
        section.addClassName("config-section");
        section.setMaxWidth("800px");
        // Note: visibility is controlled in onEnter() based on preset

        H4 title = new H4(I18nUtil.get("step5.documentOptions"));
        title.addClassName("section-title");

        Span desc = new Span(I18nUtil.get("step5.documentOptions.desc"));
        desc.addClassName("section-desc");

        HorizontalLayout optionsRow = new HorizontalLayout();
        optionsRow.setWidthFull();
        optionsRow.setAlignItems(FlexComponent.Alignment.CENTER);
        optionsRow.addClassName("document-options-row");

        // Document Outline toggle (Premium feature)
        documentOutlineCheckbox = new Checkbox(I18nUtil.get("step5.documentOptions.documentOutline"));
        documentOutlineCheckbox.setTooltipText(I18nUtil.get("step5.documentOptions.documentOutline.tooltip"));
        documentOutlineCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setDocumentOutlineEnabled(e.getValue());
            }
        });

        // Minimap toggle
        minimapCheckbox = new Checkbox(I18nUtil.get("step5.documentOptions.minimap"));
        minimapCheckbox.setTooltipText(I18nUtil.get("step5.documentOptions.minimap.tooltip"));
        minimapCheckbox.addValueChangeListener(e -> {
            if (state != null) {
                state.setMinimapEnabled(e.getValue());
            }
        });

        optionsRow.add(documentOutlineCheckbox, minimapCheckbox);
        section.add(title, desc, optionsRow);

        return section;
    }

    private Div createCssSection() {
        Div section = new Div();
        section.addClassName("config-section");
        section.setMaxWidth("800px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H4 title = new H4(I18nUtil.get("step5.customCss"));
        title.addClassName("section-title");

        // Open source badge
        Span openSourceBadge = new Span(I18nUtil.get("step5.customCss.openSource"));
        openSourceBadge.addClassName("opensource-badge");

        header.add(title, openSourceBadge);

        Span desc = new Span(I18nUtil.get("step5.customCss.desc"));
        desc.addClassName("section-desc");

        // Preset style buttons area
        HorizontalLayout presetButtons = new HorizontalLayout();
        presetButtons.addClassName("css-preset-buttons");
        presetButtons.setSpacing(true);
        presetButtons.setWidthFull();

        Button rainbowBtn = new Button("🌈 " + I18nUtil.get("step5.customCss.importRainbow"), VaadinIcon.MAGIC.create());
        rainbowBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        rainbowBtn.addClassName("rainbow-preset-btn");
        rainbowBtn.addClickListener(e -> importRainbowToolbarStyle());

        Button clearBtn = new Button(I18nUtil.get("step5.customCss.clearStyle"), VaadinIcon.TRASH.create());
        clearBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_CONTRAST);
        clearBtn.addClassName("clear-style-btn");
        clearBtn.addClickListener(e -> {
            if (customCssArea != null) {
                customCssArea.setValue("");
                if (state != null) {
                    state.setCustomCss("");
                }
                Notification.show(I18nUtil.get("step5.customCss.notification.cleared"), 2000, Notification.Position.BOTTOM_CENTER);
            }
        });

        presetButtons.add(rainbowBtn, clearBtn);

        customCssArea = new TextArea();
        customCssArea.setPlaceholder(I18nUtil.get("step5.customCss.placeholder"));
        customCssArea.setWidthFull();
        customCssArea.setHeight("150px");
        customCssArea.addClassName("custom-css-area");
        customCssArea.addValueChangeListener(e -> {
            if (state != null) {
                state.setCustomCss(e.getValue());
            }
        });

        // Hint
        Div hint = new Div();
        hint.addClassName("css-hint");
        Icon infoIcon = VaadinIcon.INFO_CIRCLE.create();
        infoIcon.setSize("14px");
        Span hintText = new Span(I18nUtil.get("step5.customCss.hint"));
        hint.add(infoIcon, hintText);

        section.add(header, desc, presetButtons, customCssArea, hint);

        return section;
    }

    /**
     * Import rainbow toolbar style
     * Sky blue background with rainbow colors cycling through each tool button
     */
    private void importRainbowToolbarStyle() {
        String rainbowCss = getRainbowToolbarCss();

        if (customCssArea != null) {
            String currentCss = customCssArea.getValue();
            // Check if rainbow style is already imported to prevent duplicates
            if (currentCss != null && currentCss.contains("Rainbow Toolbar Style")) {
                Notification notification = Notification.show(
                    "🌈 " + I18nUtil.get("step5.customCss.notification.imported"),
                    3000,
                    Notification.Position.BOTTOM_CENTER
                );
                notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                return;
            }

            if (currentCss != null && !currentCss.isEmpty()) {
                // Append to existing styles
                customCssArea.setValue(currentCss + "\n\n" + rainbowCss);
            } else {
                customCssArea.setValue(rainbowCss);
            }

            if (state != null) {
                state.setCustomCss(customCssArea.getValue());
            }

            Notification notification = Notification.show(
                "🌈 " + I18nUtil.get("step5.customCss.notification.imported"),
                3000,
                Notification.Position.BOTTOM_CENTER
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }

    /**
     * Get rainbow toolbar CSS styles
     */
    private String getRainbowToolbarCss() {
        return """
            /* 🌈 Rainbow Toolbar Style */
            /* Sky blue toolbar background */
            .ck.ck-toolbar {
                background: linear-gradient(135deg, #87CEEB 0%, #B0E0E6 50%, #87CEEB 100%) !important;
                border: 1px solid #5DADE2 !important;
                border-radius: 8px !important;
                padding: 6px 10px !important;
                box-shadow: 0 2px 8px rgba(135, 206, 235, 0.3) !important;
            }

            /* Rainbow colors - 7 classic rainbow colors */
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+1) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+1) .ck-button__label {
                color: #FF6B6B !important; /* Red */
            }
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+2) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+2) .ck-button__label {
                color: #FFA94D !important; /* Orange */
            }
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+3) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+3) .ck-button__label {
                color: #FFD93D !important; /* Yellow */
            }
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+4) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+4) .ck-button__label {
                color: #6BCB77 !important; /* Green */
            }
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+5) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+5) .ck-button__label {
                color: #4D96FF !important; /* Blue */
            }
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+6) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+6) .ck-button__label {
                color: #9B59B6 !important; /* Indigo */
            }
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+7) .ck-icon,
            .ck.ck-toolbar .ck-toolbar__items > *:nth-child(7n+7) .ck-button__label {
                color: #E056FD !important; /* Violet */
            }

            /* Button hover effect */
            .ck.ck-toolbar .ck-button:hover {
                background: rgba(255, 255, 255, 0.5) !important;
                border-radius: 4px !important;
            }

            /* Button active state */
            .ck.ck-toolbar .ck-button.ck-on {
                background: rgba(255, 255, 255, 0.7) !important;
                border-radius: 4px !important;
            }

            /* Separator style */
            .ck.ck-toolbar .ck-toolbar__separator {
                background: linear-gradient(to bottom, #FF6B6B, #FFA94D, #FFD93D, #6BCB77, #4D96FF, #9B59B6, #E056FD) !important;
                width: 2px !important;
                margin: 0 8px !important;
            }

            /* Dropdown button */
            .ck.ck-toolbar .ck-dropdown__button .ck-icon {
                transition: transform 0.3s ease !important;
            }

            .ck.ck-toolbar .ck-dropdown__button:hover .ck-icon {
                transform: scale(1.1) !important;
            }
            """;
    }

    private void selectTheme(CKEditorTheme theme) {
        if (state == null) return;

        state.setTheme(theme);

        // Update card styles
        themeCards.values().forEach(card ->
            card.getElement().getClassList().remove("selected")
        );
        themeCards.get(theme).getElement().getClassList().add("selected");
    }

    private String getLanguageName(String code) {
        return switch (code) {
            case "en" -> "English (英语)";
            case "zh-cn" -> "简体中文";
            case "zh" -> "繁體中文";
            case "ja" -> "日本語 (日语)";
            case "ko" -> "한국어 (韩语)";
            case "de" -> "Deutsch (德语)";
            case "fr" -> "Français (法语)";
            case "es" -> "Español (西班牙语)";
            case "pt" -> "Português (葡萄牙语)";
            case "ru" -> "Русский (俄语)";
            case "ar" -> "العربية (阿拉伯语)";
            default -> code;
        };
    }

    private String getLanguageFlag(String code) {
        return switch (code) {
            case "en" -> "🇺🇸";
            case "zh-cn" -> "🇨🇳";
            case "zh" -> "🇹🇼";
            case "ja" -> "🇯🇵";
            case "ko" -> "🇰🇷";
            case "de" -> "🇩🇪";
            case "fr" -> "🇫🇷";
            case "es" -> "🇪🇸";
            case "pt" -> "🇵🇹";
            case "ru" -> "🇷🇺";
            case "ar" -> "🇸🇦";
            default -> "🌐";
        };
    }

    private String getLanguageShortName(String code) {
        return switch (code) {
            case "en" -> "EN";
            case "zh-cn" -> "中文";
            case "zh" -> "繁中";
            case "ja" -> "日本語";
            case "ko" -> "한국어";
            case "de" -> "DE";
            case "fr" -> "FR";
            case "es" -> "ES";
            case "pt" -> "PT";
            case "ru" -> "RU";
            case "ar" -> "AR";
            default -> code.toUpperCase();
        };
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;

        // Initialize UI state
        if (themeCards.containsKey(state.getTheme())) {
            selectTheme(state.getTheme());
        }

        if (languageSelector != null) {
            languageSelector.setValue(state.getLanguage());
        }

        if (customCssArea != null) {
            customCssArea.setValue(state.getCustomCss());
        }

        // Document preset specific options (Document, AI Document, or DECOUPLED editor type)
        if (documentOptionsSection != null) {
            documentOptionsSection.setVisible(state.shouldShowDocumentOptions());
        }

        if (documentOutlineCheckbox != null) {
            documentOutlineCheckbox.setValue(state.isDocumentOutlineEnabled());
        }

        if (minimapCheckbox != null) {
            minimapCheckbox.setValue(state.isMinimapEnabled());
        }
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        // This step usually has no required fields
        return ValidationResult.ok();
    }

    @Override
    public boolean isSkippable() {
        return true; // Style configuration can be skipped, use defaults
    }
}
