package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wontlost.ckeditor.*;
import com.wontlost.ckeditor.config.CKEditorProperties;

/**
 * Document Editor View
 *
 * This view demonstrates a full-featured Document editor similar to the CKEditor 5 Builder
 * reference implementation. It uses the Decoupled editor type with Document Outline,
 * Fullscreen support, and A4 paper-like editing experience.
 *
 * Features:
 * - DecoupledEditor with menu bar
 * - Document Outline sidebar
 * - Fullscreen editing mode
 * - A4 paper-like editing area
 * - Premium features (requires license):
 *   - MergeFields, MultiLevelList, SlashCommand
 *   - DocumentOutline, TableOfContents, Template
 *   - FormatPainter, CaseChange, LineHeight
 *   - ExportWord, ExportPdf, ImportWord
 *   - PasteFromOfficeEnhanced, SourceEditingEnhanced
 *   - Footnotes
 */
@Route(value = "document-editor")
@PageTitle("Document Editor")
@AnonymousAllowed
@CssImport("./styles/document-editor.css")
public class DocumentEditorView extends VerticalLayout {

    private final boolean hasPremiumLicense;
    private final String licenseKey;

    public DocumentEditorView(CKEditorProperties ckEditorProperties) {
        // Get license key from Spring configuration (supports both properties file and env var)
        this.licenseKey = ckEditorProperties.getLicenseKey();
        this.hasPremiumLicense = ckEditorProperties.hasPremiumLicense();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("document-editor-view");

        // Title
        H2 title = new H2("Document Editor");
        title.getStyle()
            .set("margin", "16px 16px 16px 24px")
            .set("color", "var(--lumo-header-text-color)");

        // Create the Document editor
        VaadinCKEditor editor = createDocumentEditor();
        // Add document-editor class for application-specific CSS customizations
        editor.addClassName("document-editor");
        editor.setSizeFull();

        // Enable Document Outline if premium license is available
        // The Addon handles all the styling internally based on this property
        if (hasPremiumLicense) {
            editor.getElement().setProperty("documentOutlineEnabled", true);
        }

        // Enable Minimap for document navigation (works with GPL license)
        // Note: Minimap requires allowConfigRequiredPlugins since it needs a container element
        editor.getElement().setProperty("minimapEnabled", true);
        editor.getElement().setProperty("allowConfigRequiredPlugins", true);
        // Disable simple preview to show full content in minimap
        // Set to true if minimap updates too slowly with large documents
        editor.getElement().setProperty("minimapSimplePreview", false);

        // Wrapper for layout control
        Div editorWrapper = new Div(editor);
        editorWrapper.addClassName("document-editor-container");
        editorWrapper.setSizeFull();

        add(title, editorWrapper);
        setFlexGrow(1, editorWrapper);
    }

    /**
     * Create a full-featured Document editor
     * Configuration based on CKEditor 5 Builder reference implementation
     */
    private VaadinCKEditor createDocumentEditor() {
        VaadinCKEditorBuilder builder = VaadinCKEditor.create()
            // Use Decoupled editor for document editing
            .withType(CKEditorType.DECOUPLED)
            // Set license key for premium features
            .withLicenseKey(licenseKey)
            // Set language
            .withLanguage("en")
            // Use Document preset as base
            .withPreset(CKEditorPreset.DOCUMENT)
            // Add additional standard plugins
            .addPlugin(CKEditorPlugin.AUTOFORMAT)
            .addPlugin(CKEditorPlugin.TEXT_TRANSFORMATION)
            .addPlugin(CKEditorPlugin.MENTION)
            .addPlugin(CKEditorPlugin.IMAGE_BLOCK)
            .addPlugin(CKEditorPlugin.IMAGE_INLINE)
            .addPlugin(CKEditorPlugin.LINK_IMAGE)
            .addPlugin(CKEditorPlugin.AUTO_IMAGE)
            .addPlugin(CKEditorPlugin.TABLE_CAPTION)
            .addPlugin(CKEditorPlugin.TABLE_COLUMN_RESIZE)
            .addPlugin(CKEditorPlugin.EMOJI)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_ESSENTIALS)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_ARROWS)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_CURRENCY)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_LATIN)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_MATHEMATICAL)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_TEXT)
            .addPlugin(CKEditorPlugin.FULLSCREEN)
            // Minimap for document navigation
            .addPlugin(CKEditorPlugin.MINIMAP)
            // Set toolbar
            .withToolbar(getDocumentToolbar())
            // Set editor dimensions
            .withHeight("700px")
            .withWidth("100%")
            // Set initial content
            .withValue(getInitialContent());

        // Add premium plugins if license key is available
        if (hasPremiumLicense) {
            builder
                // Document features
                .addCustomPlugin(CustomPlugin.fromPremium("DocumentOutline"))
                .addCustomPlugin(CustomPlugin.builder("TableOfContents").premium()
                    .withToolbarItems("tableOfContents").build())
                .addCustomPlugin(CustomPlugin.builder("Template").premium()
                    .withToolbarItems("insertTemplate").build())
                .addCustomPlugin(CustomPlugin.builder("MergeFields").premium()
                    .withToolbarItems("insertMergeField", "previewMergeFields").build())
                .addCustomPlugin(CustomPlugin.builder("Footnotes").premium()
                    .withToolbarItems("insertFootnote").build())
                // Editing enhancements
                .addCustomPlugin(CustomPlugin.builder("FormatPainter").premium()
                    .withToolbarItems("formatPainter").build())
                .addCustomPlugin(CustomPlugin.builder("CaseChange").premium()
                    .withToolbarItems("caseChange").build())
                .addCustomPlugin(CustomPlugin.fromPremium("SlashCommand"))
                .addCustomPlugin(CustomPlugin.builder("LineHeight").premium()
                    .withToolbarItems("lineHeight").build())
                // List features
                .addCustomPlugin(CustomPlugin.builder("MultiLevelList").premium()
                    .withToolbarItems("multiLevelList").build())
                // Import/Export
                .addCustomPlugin(CustomPlugin.builder("ExportPdf").premium()
                    .withToolbarItems("exportPdf").build())
                .addCustomPlugin(CustomPlugin.builder("ExportWord").premium()
                    .withToolbarItems("exportWord").build())
                .addCustomPlugin(CustomPlugin.builder("ImportWord").premium()
                    .withToolbarItems("importWord").build())
                .addCustomPlugin(CustomPlugin.fromPremium("PasteFromOfficeEnhanced"));
        }

        return builder.build();
    }

    /**
     * Get the document editor toolbar configuration
     * Returns different toolbar based on license availability
     */
    private String[] getDocumentToolbar() {
        if (hasPremiumLicense) {
            // Full toolbar with premium features
            return new String[] {
                "undo", "redo", "|",
                "insertMergeField", "previewMergeFields", "|",
                "importWord", "exportWord", "exportPdf",
                "formatPainter", "caseChange", "findAndReplace", "fullscreen", "|",
                "heading", "|",
                "fontSize", "fontFamily", "fontColor", "fontBackgroundColor", "|",
                "bold", "italic", "underline", "strikethrough",
                "subscript", "superscript", "removeFormat", "|",
                "emoji", "specialCharacters", "horizontalLine", "pageBreak",
                "link", "insertFootnote", "insertImage", "insertTable",
                "tableOfContents", "insertTemplate", "|",
                "alignment", "lineHeight", "|",
                "bulletedList", "numberedList", "multiLevelList", "todoList",
                "outdent", "indent"
            };
        } else {
            // GPL toolbar without premium features
            return new String[] {
                "undo", "redo", "|",
                "findAndReplace", "fullscreen", "|",
                "heading", "|",
                "fontSize", "fontFamily", "fontColor", "fontBackgroundColor", "|",
                "bold", "italic", "underline", "strikethrough",
                "subscript", "superscript", "removeFormat", "|",
                "emoji", "specialCharacters", "horizontalLine", "pageBreak",
                "link", "insertImage", "insertTable", "|",
                "alignment", "|",
                "bulletedList", "numberedList", "todoList",
                "outdent", "indent"
            };
        }
    }

    /**
     * Get initial content for the editor
     */
    private String getInitialContent() {
        return """
            <h2>Congratulations on setting up CKEditor 5! </h2>
            <p>
                You've successfully created a CKEditor 5 project. This powerful text editor
                will enhance your application, enabling rich text editing capabilities that
                are customizable and easy to use.
            </p>
            <h3>What's next?</h3>
            <ol>
                <li>
                    <strong>Integrate into your app</strong>: time to bring the editing into
                    your application. Take the code you created and add to your application.
                </li>
                <li>
                    <strong>Explore features:</strong> Experiment with different plugins and
                    toolbar options to discover what works best for your needs.
                </li>
                <li>
                    <strong>Customize your editor:</strong> Tailor the editor's
                    configuration to match your application's style and requirements. Or
                    even write your plugin!
                </li>
            </ol>
            <p>
                Keep experimenting, and don't hesitate to push the boundaries of what you
                can achieve with CKEditor 5. Your feedback is invaluable to us as we strive
                to improve and evolve. Happy editing!
            </p>
            <h3>Helpful resources</h3>
            <ul>
                <li><a href="https://portal.ckeditor.com/checkout?plan=free">Trial sign up</a></li>
                <li><a href="https://ckeditor.com/docs/ckeditor5/latest/installation/index.html">Documentation</a></li>
                <li><a href="https://github.com/ckeditor/ckeditor5">GitHub</a></li>
                <li><a href="https://ckeditor.com">CKEditor Homepage</a></li>
                <li><a href="https://ckeditor.com/ckeditor-5/demo/">CKEditor 5 Demos</a></li>
            </ul>
            <h3>Need help?</h3>
            <p>
                See this text, but the editor is not starting up? Check the browser's
                console for clues and guidance. It may be related to an incorrect license
                key if you use premium features or another feature-related requirement. If
                you cannot make it work, file a GitHub issue, and we will help as soon as
                possible!
            </p>
            """;
    }
}
