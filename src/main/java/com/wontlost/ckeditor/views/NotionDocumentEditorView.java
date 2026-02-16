package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.wontlost.ckeditor.*;
import com.wontlost.ckeditor.config.CKEditorProperties;
import com.wontlost.ckeditor.config.CollaborationProperties;
import com.wontlost.ckeditor.config.TurnstileProperties;
import com.wontlost.ckeditor.i18n.I18nUtil;
import com.wontlost.ckeditor.security.TurnstileLoginFormSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import static com.wontlost.ckeditor.JsonUtil.createObjectNode;
import static com.wontlost.ckeditor.JsonUtil.toArrayNode;

/**
 * Notion-like Document Editor View
 *
 * 基于 CKEditor 5 Balloon 编辑器的 Notion 风格协作编辑器。
 * 无固定顶部工具栏和菜单栏，使用块级工具栏（blockToolbar）和浮动工具栏（balloonToolbar）。
 *
 * 功能特性：
 * - 块级工具栏（BlockToolbar）— 每个段落左侧显示 6 点拖拽图标 + 工具按钮
 * - 浮动工具栏（BalloonToolbar）— 选中文本时自动弹出完整工具栏
 * - 实时协作编辑（RealTimeCollaboration）
 * - 在线用户列表（PresenceList）
 * - 行内评论（Comments）
 * - 修订追踪（TrackChanges）
 * - 版本历史（RevisionHistory）
 * - 斜杠命令（SlashCommand）
 * - 批注侧栏
 *
 * 与 CollaborativeDocumentEditorView 的核心差异：
 * - 编辑器类型：BALLOON（非 DECOUPLED）
 * - 无固定工具栏、无菜单栏
 * - 有 blockToolbar（左侧 6 点按钮）
 * - 无分页（Pagination）、无 Document Outline
 */
@Route(value = "notion-document-editor")
@PageTitle("Notion-like Document Editor")
@AnonymousAllowed
@CssImport("./styles/notion-document-editor.css")
public class NotionDocumentEditorView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(NotionDocumentEditorView.class);

    private final boolean hasPremiumLicense;
    private final String licenseKey;
    private final CollaborationProperties collaborationProperties;
    private final TurnstileProperties turnstileProperties;

    public NotionDocumentEditorView(CKEditorProperties ckEditorProperties,
                                    CollaborationProperties collaborationProperties,
                                    TurnstileProperties turnstileProperties) {
        this.licenseKey = ckEditorProperties.getLicenseKey();
        this.hasPremiumLicense = ckEditorProperties.hasPremiumLicense();
        this.collaborationProperties = collaborationProperties;
        this.turnstileProperties = turnstileProperties;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("notion-document-editor-view");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!isAuthenticated()) {
            removeAll();
            showLoginForm();
            return;
        }

        removeAll();
        setAlignItems(Alignment.STRETCH);
        setJustifyContentMode(JustifyContentMode.START);

        Div titleBar = createTitleBar();

        if (!hasPremiumLicense) {
            add(titleBar, createNotConfiguredNotice("Premium license 未配置，协作功能需要有效的 CKEditor 5 Premium 许可证。"));
            return;
        }

        if (!collaborationProperties.isConfigured()) {
            add(titleBar, createNotConfiguredNotice(
                "CKEditor Cloud Services 未配置。请在 .env 中设置 CKEDITOR_CS_ENVIRONMENT_ID、" +
                "CKEDITOR_CS_API_SECRET 和 CKEDITOR_CS_WS_URL。"));
            return;
        }

        Div loadingOverlay = createLoadingOverlay();

        VaadinCKEditor editor = createNotionEditor();
        editor.addClassName("notion-editor");
        editor.getStyle().set("visibility", "hidden");

        editor.addEditorReadyListener(e -> {
            loadingOverlay.getStyle().set("display", "none");
            editor.getStyle().remove("visibility");

            // 侧栏批注对齐 — 与 Collaborative 编辑器相同的 JS 逻辑
            editor.getElement().executeJs(
                "const el = this;" +
                "const wrapper = el.querySelector('.editor-container__editor-wrapper');" +
                "const sidebar = el.querySelector('.annotation-sidebar-container');" +
                "if (!wrapper || !sidebar) return;" +
                "const editable = wrapper.querySelector('.ck-editor__editable');" +
                "if (!editable) return;" +
                "sidebar.style.overflow = 'visible';" +
                "sidebar.style.position = 'relative';" +
                "function syncPositions() {" +
                "  const items = sidebar.querySelectorAll('.ck-sidebar-item');" +
                "  const markers = editable.querySelectorAll('.ck-comment-marker');" +
                "  if (!items.length || !markers.length) return;" +
                "  const wrapperRect = wrapper.getBoundingClientRect();" +
                "  const sidebarRect = sidebar.getBoundingClientRect();" +
                "  const sidebarOffset = sidebarRect.top - wrapperRect.top;" +
                "  let lastBottom = -Infinity;" +
                "  const n = Math.min(items.length, markers.length);" +
                "  for (let i = 0; i < n; i++) {" +
                "    const markerRect = markers[i].getBoundingClientRect();" +
                "    const markerRelY = markerRect.top - wrapperRect.top;" +
                "    let desiredTop = markerRelY - sidebarOffset;" +
                "    if (desiredTop < lastBottom + 4) {" +
                "      desiredTop = lastBottom + 4;" +
                "    }" +
                "    items[i].style.position = 'absolute';" +
                "    items[i].style.top = desiredTop + 'px';" +
                "    items[i].style.width = '100%';" +
                "    lastBottom = desiredTop + items[i].offsetHeight;" +
                "  }" +
                "  for (let i = n; i < items.length; i++) {" +
                "    items[i].style.position = 'absolute';" +
                "    items[i].style.top = (lastBottom + 4) + 'px';" +
                "    items[i].style.width = '100%';" +
                "    lastBottom += items[i].offsetHeight + 4;" +
                "  }" +
                "}" +
                "wrapper.addEventListener('scroll', syncPositions);" +
                "editable.addEventListener('scroll', syncPositions);" +
                "syncPositions();" +
                "new MutationObserver(syncPositions).observe(sidebar, {childList:true, subtree:true, attributes:true});"
            );
        });

        // Notion 风格：无 Document Outline，有批注侧栏
        editor.setDocumentOutlineEnabled(false);
        editor.setAnnotationSidebarEnabled(true);

        Div editorWrapper = new Div(loadingOverlay, editor);
        editorWrapper.addClassName("notion-editor-container");
        editorWrapper.setWidthFull();

        add(titleBar, editorWrapper);
        setFlexGrow(1, editorWrapper);
    }

    private Div createTitleBar() {
        H2 title = new H2("Notion-like Document Editor");
        title.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-header-text-color)");

        Span badge = new Span("Real-time");
        badge.getStyle()
            .set("font-size", "11px")
            .set("color", "var(--lumo-primary-color)")
            .set("background", "var(--lumo-primary-color-10pct)")
            .set("padding", "2px 8px")
            .set("border-radius", "4px")
            .set("font-weight", "500");

        Div leftGroup = new Div(title, badge);
        leftGroup.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "12px");

        Div titleBar = new Div(leftGroup);
        titleBar.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "space-between")
            .set("padding", "16px 16px 16px 24px");

        if (isAuthenticated()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Span userLabel = new Span(auth.getName());
            userLabel.getStyle()
                .set("font-size", "14px")
                .set("color", "var(--lumo-secondary-text-color)");

            Button logoutBtn = new Button(VaadinIcon.SIGN_OUT.create());
            logoutBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
            logoutBtn.setAriaLabel("Sign out");
            logoutBtn.getStyle()
                .set("font-size", "11px")
                .set("color", "var(--lumo-error-color)")
                .set("background", "var(--lumo-error-color-10pct)")
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-weight", "500")
                .set("cursor", "pointer");
            logoutBtn.addClickListener(e ->
                getUI().ifPresent(ui -> ui.getPage().setLocation("/logout")));

            Div rightGroup = new Div(userLabel, logoutBtn);
            rightGroup.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "12px");

            titleBar.add(rightGroup);
        }

        return titleBar;
    }

    private Div createNotConfiguredNotice(String message) {
        Div notice = new Div();
        notice.getStyle()
            .set("padding", "40px")
            .set("text-align", "center")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "14px")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "8px")
            .set("margin", "24px");

        Span icon = new Span("\u26A0\uFE0F");
        icon.getStyle().set("font-size", "32px").set("display", "block").set("margin-bottom", "12px");

        notice.add(icon, new Span(message));
        return notice;
    }

    private Div createLoadingOverlay() {
        Div spinner = new Div();
        spinner.addClassName("loading-spinner");

        Span text = new Span("Loading Notion-like editor...");
        text.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "14px")
            .set("margin-top", "16px");

        Div overlay = new Div(spinner, text);
        overlay.addClassName("loading-overlay");
        return overlay;
    }

    private void showLoginForm() {
        LoginForm loginForm = new LoginForm();
        loginForm.setAction("login?redirect=/notion-document-editor");
        loginForm.setForgotPasswordButtonVisible(false);
        TurnstileLoginFormSupport.inject(loginForm, turnstileProperties);

        Span description = new Span(I18nUtil.get("collab.login.description"));
        description.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "14px")
            .set("margin-bottom", "8px");

        Span testHint = new Span("alice/alice ; bob/bob");
        testHint.getStyle()
            .set("color", "var(--lumo-tertiary-text-color)")
            .set("font-size", "12px")
            .set("font-family", "monospace");

        Div loginContainer = new Div(description, testHint, loginForm);
        loginContainer.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("flex-grow", "1");

        add(loginContainer);
        setFlexGrow(1, loginContainer);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * 创建 Notion 风格编辑器 — BALLOON 类型 + blockToolbar + balloonToolbar
     * 无固定顶部工具栏、无菜单栏
     */
    private VaadinCKEditor createNotionEditor() {
        VaadinCKEditorBuilder builder = VaadinCKEditor.create()
            .withType(CKEditorType.BALLOON)
            .withLicenseKey(licenseKey)
            .withLanguage("en")
            // 基础插件
            .addPlugin(CKEditorPlugin.ESSENTIALS)
            .addPlugin(CKEditorPlugin.PARAGRAPH)
            .addPlugin(CKEditorPlugin.AUTOFORMAT)
            .addPlugin(CKEditorPlugin.TEXT_TRANSFORMATION)
            .addPlugin(CKEditorPlugin.AUTOSAVE)
            // 文本样式
            .addPlugin(CKEditorPlugin.BOLD)
            .addPlugin(CKEditorPlugin.ITALIC)
            .addPlugin(CKEditorPlugin.UNDERLINE)
            .addPlugin(CKEditorPlugin.STRIKETHROUGH)
            .addPlugin(CKEditorPlugin.CODE)
            // 段落
            .addPlugin(CKEditorPlugin.HEADING)
            .addPlugin(CKEditorPlugin.BLOCK_QUOTE)
            .addPlugin(CKEditorPlugin.INDENT)
            .addPlugin(CKEditorPlugin.INDENT_BLOCK)
            // 列表
            .addPlugin(CKEditorPlugin.LIST)
            .addPlugin(CKEditorPlugin.LIST_PROPERTIES)
            .addPlugin(CKEditorPlugin.TODO_LIST)
            // 链接
            .addPlugin(CKEditorPlugin.LINK)
            .addPlugin(CKEditorPlugin.AUTO_LINK)
            .addPlugin(CKEditorPlugin.LINK_IMAGE)
            // 图片（块级为主，Notion 风格）
            .addPlugin(CKEditorPlugin.IMAGE)
            .addPlugin(CKEditorPlugin.IMAGE_BLOCK)
            .addPlugin(CKEditorPlugin.IMAGE_TOOLBAR)
            .addPlugin(CKEditorPlugin.IMAGE_CAPTION)
            .addPlugin(CKEditorPlugin.IMAGE_STYLE)
            .addPlugin(CKEditorPlugin.IMAGE_RESIZE)
            .addPlugin(CKEditorPlugin.IMAGE_INSERT)
            .addPlugin(CKEditorPlugin.IMAGE_UPLOAD)
            .addPlugin(CKEditorPlugin.AUTO_IMAGE)
            .addPlugin(CKEditorPlugin.BASE64_UPLOAD_ADAPTER)
            // 表格
            .addPlugin(CKEditorPlugin.TABLE)
            .addPlugin(CKEditorPlugin.TABLE_TOOLBAR)
            // 媒体
            .addPlugin(CKEditorPlugin.MEDIA_EMBED)
            // 代码块
            .addPlugin(CKEditorPlugin.CODE_BLOCK)
            // 特殊字符
            .addPlugin(CKEditorPlugin.HORIZONTAL_LINE)
            .addPlugin(CKEditorPlugin.EMOJI)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_ESSENTIALS)
            // 编辑
            .addPlugin(CKEditorPlugin.FIND_AND_REPLACE)
            .addPlugin(CKEditorPlugin.HIGHLIGHT)
            .addPlugin(CKEditorPlugin.MENTION)
            // 标题（Notion 风格文档标题）
            .addPlugin(CKEditorPlugin.TITLE)
            .addPlugin(CKEditorPlugin.PASTE_FROM_OFFICE)
            // 块级工具栏 — Notion 核心特性
            .addPlugin(CKEditorPlugin.BLOCK_TOOLBAR)
            // 浮动工具栏 — 选中文本时弹出
            .addPlugin(CKEditorPlugin.BALLOON_TOOLBAR)
            // Cloud Services
            .addPlugin(CKEditorPlugin.CLOUD_SERVICES)
            // Premium 插件 — 协作
            .addCustomPlugin(CustomPlugin.fromPremium("RealTimeCollaborativeEditing"))
            .addCustomPlugin(CustomPlugin.fromPremium("RealTimeCollaborativeComments"))
            .addCustomPlugin(CustomPlugin.fromPremium("RealTimeCollaborativeTrackChanges"))
            .addCustomPlugin(CustomPlugin.fromPremium("RealTimeCollaborativeRevisionHistory"))
            .addCustomPlugin(CustomPlugin.fromPremium("PresenceList"))
            .addCustomPlugin(CustomPlugin.builder("Comments").premium()
                .withToolbarItems("comment").build())
            .addCustomPlugin(CustomPlugin.builder("TrackChanges").premium()
                .withToolbarItems("trackChanges").build())
            .addCustomPlugin(CustomPlugin.builder("RevisionHistory").premium()
                .withToolbarItems("revisionHistory").build())
            .addCustomPlugin(CustomPlugin.builder("CommentsArchive").premium()
                .withToolbarItems("commentsArchive").build())
            // Premium 插件 — 生产力
            .addCustomPlugin(CustomPlugin.builder("CaseChange").premium()
                .withToolbarItems("caseChange").build())
            .addCustomPlugin(CustomPlugin.fromPremium("SlashCommand"))
            .addCustomPlugin(CustomPlugin.builder("TableOfContents").premium()
                .withToolbarItems("tableOfContents").build())
            .addCustomPlugin(CustomPlugin.builder("Template").premium()
                .withToolbarItems("insertTemplate").build())
            .addCustomPlugin(CustomPlugin.fromPremium("PasteFromOfficeEnhanced"))
            // Premium 插件 — 导入导出
            .addCustomPlugin(CustomPlugin.builder("ExportPdf").premium()
                .withToolbarItems("exportPdf").build())
            .addCustomPlugin(CustomPlugin.builder("ExportWord").premium()
                .withToolbarItems("exportWord").build())
            .addCustomPlugin(CustomPlugin.builder("ImportWord").premium()
                .withToolbarItems("importWord").build())
            // 光标选中评论时高亮对应侧栏项
            .addCustomPlugin(CustomPlugin.builder("CommentHighlightTracker")
                .withImportPath("custom-comment-highlight-tracker")
                .build())
            // Balloon 编辑器不需要 withToolbar — 工具栏由 balloonToolbar 和 blockToolbar 提供
            .withHeight("calc(100vh - 80px)")
            .withWidth("100%");

        // 配置
        CKEditorConfig config = new CKEditorConfig();
        config.setLanguage("en");

        // Cloud Services
        ObjectNode cloudServicesNode = createObjectNode();
        cloudServicesNode.put("tokenUrl", "/api/ckeditor/token");
        cloudServicesNode.put("webSocketUrl", collaborationProperties.getWebSocketUrl());
        config.set("cloudServices", cloudServicesNode);

        // 协作 channel — 使用独立 channelId（与 Collaborative 编辑器不共享）
        ObjectNode collaborationNode = createObjectNode();
        collaborationNode.put("channelId", "notion-collaborative-doc-v2");
        config.set("collaboration", collaborationNode);

        // 初始内容
        config.set("initialData", StringNode.valueOf(getInitialContent()));

        // comments 配置
        ObjectNode commentsNode = createObjectNode();
        commentsNode.set("editorConfig", createObjectNode());
        config.set("comments", commentsNode);

        // blockToolbar — Notion 核心：每个段落左侧的 6 点按钮
        config.set("blockToolbar", toArrayNode(new String[] {
            "comment", "|",
            "bold", "italic", "|",
            "link", "insertImage", "insertTable", "|",
            "bulletedList", "numberedList", "outdent", "indent"
        }));

        // balloonToolbar — 选中文本时弹出的完整工具栏
        config.set("balloonToolbar", toArrayNode(new String[] {
            "undo", "redo", "|",
            "revisionHistory", "trackChanges", "comment", "commentsArchive", "|",
            "importWord", "exportWord", "exportPdf", "caseChange", "findAndReplace", "|",
            "heading", "|",
            "bold", "italic", "underline", "strikethrough", "code", "|",
            "emoji", "specialCharacters", "horizontalLine",
            "link", "insertImage", "mediaEmbed",
            "insertTable", "tableOfContents", "insertTemplate",
            "highlight", "blockQuote", "codeBlock", "|",
            "bulletedList", "numberedList", "todoList",
            "outdent", "indent"
        }));

        // image 工具栏配置（Notion 风格 — 块级图片）
        config.setImage(
            new String[] {
                "toggleImageCaption", "imageTextAlternative", "|",
                "imageStyle:alignBlockLeft", "imageStyle:block", "imageStyle:alignBlockRight", "|",
                "resizeImage"
            },
            null
        );

        // placeholder
        config.set("placeholder", StringNode.valueOf("Type or paste your content here!"));

        builder.withConfig(config);

        return builder.build();
    }

    /**
     * 初始文档内容 — Notion 风格知识库文档
     */
    private String getInitialContent() {
        return """
            <h2>Product Launch Playbook</h2>
            <p>This playbook outlines our <strong>Q1 2026 product launch</strong> strategy, \
            covering everything from pre-launch preparation to post-launch monitoring. \
            All team members should review their assigned sections and update progress regularly.</p>
            <hr>
            <h3>Launch Timeline</h3>
            <figure class="table">
                <table>
                    <thead>
                        <tr><th>Phase</th><th>Dates</th><th>Owner</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                        <tr><td><strong>Pre-launch</strong></td><td>Jan 6 - Feb 14</td><td>Engineering</td><td><mark class="marker-green">On Track</mark></td></tr>
                        <tr><td><strong>Beta Testing</strong></td><td>Feb 17 - Mar 7</td><td>QA Team</td><td><mark class="marker-yellow">In Progress</mark></td></tr>
                        <tr><td><strong>Marketing Push</strong></td><td>Mar 10 - Mar 21</td><td>Marketing</td><td>Not Started</td></tr>
                        <tr><td><strong>GA Release</strong></td><td>Mar 24</td><td>All Teams</td><td>Not Started</td></tr>
                    </tbody>
                </table>
            </figure>
            <h3>Pre-launch Checklist</h3>
            <ul class="todo-list">
                <li><label class="todo-list__label"><input type="checkbox" checked="checked" disabled="disabled"><span class="todo-list__label__description">Finalize feature spec and API contracts</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" checked="checked" disabled="disabled"><span class="todo-list__label__description">Complete core feature implementation</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" checked="checked" disabled="disabled"><span class="todo-list__label__description">Set up staging environment with production-like data</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Load testing — target 10k concurrent users</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Security audit and penetration testing</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Documentation review and user guides</span></label></li>
            </ul>
            <hr>
            <h3>Architecture Overview</h3>
            <p>The new release introduces a <strong>real-time collaboration engine</strong> built on WebSocket connections \
            with <mark class="marker-blue">CKEditor Cloud Services</mark> providing the backend infrastructure.</p>
            <p>Key components:</p>
            <ul>
                <li><strong>Collaboration Server</strong> — Manages document channels, user presence, and operational transforms</li>
                <li><strong>Token Service</strong> — Issues short-lived JWT tokens for authenticated editor sessions</li>
                <li><strong>Revision Store</strong> — Persists document revision history for audit and rollback</li>
                <li><strong>Notification Hub</strong> — Delivers real-time updates for comments and @mentions</li>
            </ul>
            <h3>API Configuration</h3>
            <p>The collaboration endpoint requires the following environment variables:</p>
            <pre><code class="language-plaintext">CKEDITOR_CS_ENVIRONMENT_ID=your-environment-id
CKEDITOR_CS_API_SECRET=your-api-secret
CKEDITOR_CS_WS_URL=wss://your-websocket-url
CKEDITOR_LICENSE_KEY=your-license-key</code></pre>
            <blockquote><p><strong>Note:</strong> Never commit secrets to version control. \
            Use <code>.env</code> files locally and secrets management in CI/CD.</p></blockquote>
            <h3>Feature Highlights</h3>
            <ol>
                <li><strong>Real-time Co-editing</strong> — Multiple users edit simultaneously with live cursor tracking</li>
                <li><strong>Inline Comments</strong> — Select any text to start a discussion thread</li>
                <li><strong>Track Changes</strong> — Accept or reject edits with full attribution</li>
                <li><strong>Revision History</strong> — Browse and restore any previous version</li>
                <li><strong>Slash Commands</strong> — Type <code>/</code> to quickly insert blocks, tables, or templates</li>
            </ol>
            <h3>Known Limitations</h3>
            <p>The following items are <mark class="marker-pink">known limitations</mark> for the initial release:</p>
            <ul>
                <li>Offline editing support is planned for v2.1 (Q3 2026)</li>
                <li>Maximum document size: 2MB of rich content</li>
                <li>Image uploads require CKBox integration (not included in base license)</li>
                <li>Real-time collaboration limited to 50 concurrent users per document</li>
            </ul>
            <hr>
            <h3>Meeting Notes — Feb 14, 2026</h3>
            <p><em>Attendees: Alice, Bob, Charlie, Diana</em></p>
            <p><strong>Decisions made:</strong></p>
            <ol>
                <li>Beta testing window extended by one week to accommodate additional QA feedback</li>
                <li>Marketing team to prepare demo videos using the Notion-like editor preset</li>
                <li>Engineering to prioritize <mark class="marker-yellow">comment permission controls</mark> before GA</li>
            </ol>
            <p><strong>Action items:</strong></p>
            <ul class="todo-list">
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Alice: Update release notes with new collaboration features</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Bob: Run load test with 50 concurrent editors on staging</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Charlie: Prepare marketing screenshots and demo script</span></label></li>
                <li><label class="todo-list__label"><input type="checkbox" disabled="disabled"><span class="todo-list__label__description">Diana: Review and finalize API documentation</span></label></li>
            </ul>
            """;
    }
}
