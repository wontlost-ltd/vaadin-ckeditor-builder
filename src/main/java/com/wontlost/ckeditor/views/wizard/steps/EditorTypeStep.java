package com.wontlost.ckeditor.views.wizard.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.ValidationResult;
import com.wontlost.ckeditor.domain.WizardStep;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Step 2: Editor Type Selection
 * Two-column layout: left side for compact card list, right side for animated preview
 */
public class EditorTypeStep implements WizardStep {

    private BuilderState state;
    private VerticalLayout content;
    private final Map<CKEditorType, Div> typeCards = new HashMap<>();
    private Div previewContainer;

    @Override
    public String getId() { return "editor-type"; }

    @Override
    public String getTitle() { return I18nUtil.get("step2.title"); }

    @Override
    public String getDescription() { return I18nUtil.get("step2.description"); }

    @Override
    public VaadinIcon getIcon() { return VaadinIcon.VIEWPORT; }

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
        layout.addClassName("editor-type-step");

        // Two-column layout container
        Div twoColumnLayout = new Div();
        twoColumnLayout.addClassName("editor-type-layout");

        // Left column: compact cards
        Div cardColumn = createCardColumn();

        // Right column: animated preview
        Div previewColumn = createPreviewColumn();

        twoColumnLayout.add(cardColumn, previewColumn);
        layout.add(twoColumnLayout);

        return layout;
    }

    /**
     * Create left card column
     */
    private Div createCardColumn() {
        Div column = new Div();
        column.addClassName("editor-type-column");

        // Classic Editor - Recommended
        Div classicCard = createCompactTypeCard(
            CKEditorType.CLASSIC,
            VaadinIcon.EDIT,
            I18nUtil.get("step2.classic.name"),
            I18nUtil.get("step2.classic.description"),
            true
        );

        // Balloon Editor
        Div balloonCard = createCompactTypeCard(
            CKEditorType.BALLOON,
            VaadinIcon.COMMENT_ELLIPSIS,
            I18nUtil.get("step2.balloon.name"),
            I18nUtil.get("step2.balloon.description"),
            false
        );

        // Inline Editor
        Div inlineCard = createCompactTypeCard(
            CKEditorType.INLINE,
            VaadinIcon.CURSOR,
            I18nUtil.get("step2.inline.name"),
            I18nUtil.get("step2.inline.description"),
            false
        );

        // Decoupled Editor
        Div decoupledCard = createCompactTypeCard(
            CKEditorType.DECOUPLED,
            VaadinIcon.SPLIT,
            I18nUtil.get("step2.decoupled.name"),
            I18nUtil.get("step2.decoupled.description"),
            false
        );

        typeCards.put(CKEditorType.CLASSIC, classicCard);
        typeCards.put(CKEditorType.BALLOON, balloonCard);
        typeCards.put(CKEditorType.INLINE, inlineCard);
        typeCards.put(CKEditorType.DECOUPLED, decoupledCard);

        column.add(classicCard, balloonCard, inlineCard, decoupledCard);

        return column;
    }

    /**
     * Create right preview column
     */
    private Div createPreviewColumn() {
        Div column = new Div();
        column.addClassName("editor-type-preview");

        previewContainer = new Div();
        previewContainer.addClassName("type-preview-content");
        previewContainer.setId("type-preview-content");
        previewContainer.add(createAnimatedPreview(CKEditorType.CLASSIC));

        column.add(previewContainer);

        return column;
    }

    /**
     * Create compact type card
     */
    private Div createCompactTypeCard(CKEditorType type, VaadinIcon iconType,
                                       String title, String description, boolean recommended) {
        Div card = new Div();
        card.addClassName("type-card-compact");
        card.getElement().setAttribute("data-type", type.name());

        // Keyboard navigation support
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().setAttribute("role", "button");
        card.getElement().setAttribute("aria-label", title + ": " + description);

        // Left icon
        Div iconWrapper = new Div();
        iconWrapper.addClassName("type-card-icon-compact");
        Icon icon = iconType.create();
        icon.setSize("20px");
        iconWrapper.add(icon);

        // Center text
        Div textWrapper = new Div();
        textWrapper.addClassName("type-card-text");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("type-card-title-compact");

        Span descSpan = new Span(description);
        descSpan.addClassName("type-card-desc-compact");

        textWrapper.add(titleSpan, descSpan);

        // Recommended badge
        if (recommended) {
            Span badge = new Span(I18nUtil.get("step2.recommended"));
            badge.addClassName("recommended-badge-compact");
            card.add(badge);
        }

        card.add(iconWrapper, textWrapper);

        // Click to select
        card.addClickListener(e -> selectType(type));

        // Keyboard event support (Enter/Space)
        card.getElement().addEventListener("keydown", e -> selectType(type))
            .setFilter("event.key === 'Enter' || event.key === ' '");

        return card;
    }

    /**
     * Create animated preview area
     */
    private Component createAnimatedPreview(CKEditorType type) {
        Div preview = new Div();
        preview.addClassName("type-preview-animated");
        preview.getElement().setAttribute("data-type", type.name());

        switch (type) {
            case CLASSIC -> preview.add(createClassicPreview());
            case BALLOON -> preview.add(createBalloonPreview());
            case INLINE -> preview.add(createInlinePreview());
            case DECOUPLED -> preview.add(createDecoupledPreview());
        }

        return preview;
    }

    /**
     * Classic editor preview: static display
     */
    private Component createClassicPreview() {
        Div mockEditor = new Div();
        mockEditor.addClassName("mock-editor-animated");
        mockEditor.addClassName("mock-classic");

        // Toolbar (using real icons, consistent with inline editor)
        Div toolbar = new Div();
        toolbar.addClassName("mock-toolbar-animated");
        toolbar.addClassName("mock-toolbar-real-static");
        toolbar.add(createRealToolbarButtons());

        // Editor area
        Div editorArea = new Div();
        editorArea.addClassName("mock-editor-area-animated");

        Div line1 = new Div();
        line1.addClassName("mock-text-line");
        line1.setText(I18nUtil.get("step2.preview.classic.title"));

        Div line2 = new Div();
        line2.addClassName("mock-text-line");
        line2.addClassName("mock-text-secondary");
        line2.setText(I18nUtil.get("step2.preview.classic.desc"));

        editorArea.add(line1, line2);
        mockEditor.add(toolbar, editorArea);

        return mockEditor;
    }

    /**
     * Balloon editor preview: text selection animation + toolbar popup
     */
    private Component createBalloonPreview() {
        Div mockEditor = new Div();
        mockEditor.addClassName("mock-editor-animated");
        mockEditor.addClassName("mock-balloon");
        mockEditor.addClassName("is-active");

        // Editor area
        Div editorArea = new Div();
        editorArea.addClassName("mock-editor-area-animated");
        editorArea.addClassName("mock-balloon-area");

        // Normal text
        Span prefix = new Span(I18nUtil.get("step2.preview.balloon.prefix"));
        prefix.addClassName("mock-text-normal");

        // Selectable text container
        Div selectableWrapper = new Div();
        selectableWrapper.addClassName("mock-selectable-wrapper");

        // Selectable text
        Span selectableText = new Span(I18nUtil.get("step2.preview.balloon.selectMe"));
        selectableText.addClassName("mock-selectable-text");

        // Selection highlight layer
        Div selection = new Div();
        selection.addClassName("mock-selection");

        // Floating toolbar (using simplified real icons for complete display)
        Div balloonToolbar = new Div();
        balloonToolbar.addClassName("mock-balloon-toolbar-animated");
        balloonToolbar.addClassName("mock-toolbar-real-static");
        balloonToolbar.add(createBalloonToolbarButtons());

        selectableWrapper.add(selectableText, selection, balloonToolbar);

        // Suffix text
        Span suffix = new Span(I18nUtil.get("step2.preview.balloon.suffix"));
        suffix.addClassName("mock-text-normal");

        editorArea.add(prefix, selectableWrapper, suffix);

        // Mock cursor
        Div cursor = new Div();
        cursor.addClassName("mock-cursor");
        cursor.addClassName("mock-cursor-balloon");

        mockEditor.add(editorArea, cursor);

        return mockEditor;
    }

    /**
     * Inline editor preview: auto animation simulating mouse click, toolbar show/hide
     */
    private Component createInlinePreview() {
        Div container = new Div();
        container.addClassName("mock-inline-container");
        container.addClassName("mock-inline-animated");

        // Toolbar (using real icons)
        Div toolbar = new Div();
        toolbar.addClassName("mock-inline-toolbar-real");
        toolbar.add(createRealToolbarButtons());

        // Editor area
        Div editorArea = new Div();
        editorArea.addClassName("mock-inline-editor-area");

        // First line: cursor + text
        Div line1 = new Div();
        line1.addClassName("mock-text-line");
        line1.addClassName("mock-text-line-with-cursor");

        Span blinkingCursor = new Span("|");
        blinkingCursor.addClassName("mock-blinking-cursor");

        Span text1 = new Span(I18nUtil.get("step2.preview.inline.placeholder"));
        text1.addClassName("mock-placeholder-text");

        line1.add(blinkingCursor, text1);

        // Second line
        Div line2 = new Div();
        line2.addClassName("mock-text-line");
        line2.addClassName("mock-text-secondary");
        line2.setText(I18nUtil.get("step2.preview.inline.desc"));

        editorArea.add(line1, line2);

        // Mock mouse pointer
        Div cursor = new Div();
        cursor.addClassName("mock-cursor");
        cursor.addClassName("mock-cursor-inline-anim");

        // Click ripple effect
        Div clickRipple = new Div();
        clickRipple.addClassName("mock-click-ripple");

        container.add(toolbar, editorArea, cursor, clickRipple);

        return container;
    }

    /**
     * Create real toolbar buttons (using VaadinIcon)
     */
    private Component createRealToolbarButtons() {
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.addClassName("mock-real-toolbar-buttons");
        buttons.setSpacing(false);

        // Formatting group
        buttons.add(createToolbarIconBtn(VaadinIcon.TEXT_LABEL));
        buttons.add(createToolbarSep());

        // Basic formatting
        buttons.add(createToolbarIconBtn(VaadinIcon.BOLD));
        buttons.add(createToolbarIconBtn(VaadinIcon.ITALIC));
        buttons.add(createToolbarIconBtn(VaadinIcon.UNDERLINE));
        buttons.add(createToolbarSep());

        // Insert
        buttons.add(createToolbarIconBtn(VaadinIcon.LINK));
        buttons.add(createToolbarIconBtn(VaadinIcon.PICTURE));
        buttons.add(createToolbarIconBtn(VaadinIcon.TABLE));
        buttons.add(createToolbarSep());

        // Lists
        buttons.add(createToolbarIconBtn(VaadinIcon.LIST_UL));
        buttons.add(createToolbarIconBtn(VaadinIcon.LIST_OL));

        return buttons;
    }

    /**
     * Create simplified toolbar buttons for balloon editor (ensure complete display above selected text)
     */
    private Component createBalloonToolbarButtons() {
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.addClassName("mock-real-toolbar-buttons");
        buttons.setSpacing(false);

        // Basic formatting (simplified, only 3 buttons)
        buttons.add(createToolbarIconBtn(VaadinIcon.BOLD));
        buttons.add(createToolbarIconBtn(VaadinIcon.ITALIC));
        buttons.add(createToolbarIconBtn(VaadinIcon.UNDERLINE));

        return buttons;
    }

    /**
     * Create toolbar icon button
     */
    private Div createToolbarIconBtn(VaadinIcon iconType) {
        Div btn = new Div();
        btn.addClassName("mock-toolbar-icon-btn");
        Icon icon = iconType.create();
        icon.setSize("16px");
        btn.add(icon);
        return btn;
    }

    /**
     * Create toolbar separator
     */
    private Div createToolbarSep() {
        Div sep = new Div();
        sep.addClassName("mock-toolbar-sep");
        return sep;
    }

    /**
     * Decoupled editor preview: Document Editor thumbnail
     */
    private Component createDecoupledPreview() {
        Div mockEditor = new Div();
        mockEditor.addClassName("mock-editor-animated");
        mockEditor.addClassName("mock-decoupled");

        // Mock menu bar
        Div menuBar = new Div();
        menuBar.addClassName("mock-menubar");

        String[] menus = {"File", "Edit", "View", "Insert", "Format"};
        for (String menu : menus) {
            Span menuItem = new Span(menu);
            menuItem.addClassName("mock-menu-item");
            menuBar.add(menuItem);
        }

        // Toolbar (standalone, using real icons, consistent with inline editor)
        Div toolbar = new Div();
        toolbar.addClassName("mock-toolbar-animated");
        toolbar.addClassName("mock-toolbar-decoupled");
        toolbar.addClassName("mock-toolbar-real-static");
        toolbar.add(createRealToolbarButtons());

        // Document container (with shadow)
        Div documentContainer = new Div();
        documentContainer.addClassName("mock-document-container");

        // Left outline
        Div outline = new Div();
        outline.addClassName("mock-outline");

        Div outlineTitle = new Div();
        outlineTitle.addClassName("mock-outline-title");
        outlineTitle.setText("Outline");

        Div outlineItems = new Div();
        outlineItems.addClassName("mock-outline-items");

        String[] items = {"Chapter 1", "  Section 1.1", "  Section 1.2", "Chapter 2"};
        for (String item : items) {
            Div outlineItem = new Div();
            outlineItem.addClassName("mock-outline-item");
            if (item.startsWith("  ")) {
                outlineItem.addClassName("mock-outline-indent");
                outlineItem.setText(item.trim());
            } else {
                outlineItem.setText(item);
            }
            outlineItems.add(outlineItem);
        }

        outline.add(outlineTitle, outlineItems);

        // Editor area (document page style)
        Div documentPage = new Div();
        documentPage.addClassName("mock-document-page");

        Div pageTitle = new Div();
        pageTitle.addClassName("mock-page-title");
        pageTitle.setText("Document Title");

        Div pageLine1 = new Div();
        pageLine1.addClassName("mock-page-line");

        Div pageLine2 = new Div();
        pageLine2.addClassName("mock-page-line");
        pageLine2.addClassName("mock-page-line-short");

        documentPage.add(pageTitle, pageLine1, pageLine2);

        // Right Minimap
        Div minimap = new Div();
        minimap.addClassName("mock-minimap");

        Div minimapViewport = new Div();
        minimapViewport.addClassName("mock-minimap-viewport");

        Div minimapContent = new Div();
        minimapContent.addClassName("mock-minimap-content");

        minimap.add(minimapViewport, minimapContent);

        documentContainer.add(outline, documentPage, minimap);
        mockEditor.add(menuBar, toolbar, documentContainer);

        return mockEditor;
    }

    /**
     * Create animated toolbar buttons
     */
    private Component createAnimatedToolbarButtons() {
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.addClassName("mock-toolbar-buttons-animated");
        buttons.setSpacing(false);

        String[] labels = {"B", "I", "U", "|", "H", "L", "|", "Q"};
        for (String label : labels) {
            if ("|".equals(label)) {
                Div separator = new Div();
                separator.addClassName("mock-toolbar-separator");
                buttons.add(separator);
            } else {
                Div btn = new Div();
                btn.addClassName("mock-btn-animated");
                btn.setText(label);
                buttons.add(btn);
            }
        }

        return buttons;
    }

    private void selectType(CKEditorType type) {
        if (state == null) return;

        state.setEditorType(type);

        // Update card styles
        typeCards.values().forEach(card ->
            card.getElement().getClassList().remove("selected")
        );
        typeCards.get(type).getElement().getClassList().add("selected");

        // Update preview
        updatePreview(type);
    }

    private void updatePreview(CKEditorType type) {
        if (previewContainer != null) {
            previewContainer.removeAll();
            previewContainer.add(createAnimatedPreview(type));
        }
    }

    @Override
    public void onEnter(BuilderState state) {
        this.state = state;

        // Initialize UI state
        if (content != null && state.getEditorType() != null) {
            selectType(state.getEditorType());
        }
    }

    @Override
    public ValidationResult validate(BuilderState state) {
        if (state.getEditorType() == null) {
            return ValidationResult.error(I18nUtil.get("validation.selectEditorType"));
        }
        return ValidationResult.ok();
    }
}
