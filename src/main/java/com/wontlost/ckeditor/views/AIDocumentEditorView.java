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
import com.wontlost.ckeditor.config.AIProperties;
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

/**
 * AI Document Editor View
 *
 * 基于 CKEditor 5 Decoupled 编辑器 + AI 侧栏的智能文档编辑器。
 * 参考 CKEditor 5 Builder 的 AI Document Editor 预设实现。
 *
 * 功能特性：
 * - AI Chat 侧栏 — 对话式 AI 辅助内容创作
 * - AI Quick Actions — 选中文本后一键重写、摘要、扩展、翻译
 * - AI Review Mode — AI 质量审查（语法、风格、语调）
 * - AI Translate — AI 翻译（作为编辑建议）
 * - Track Changes — AI 建议以修订模式呈现
 * - 格式刷、斜杠命令、行高调节
 * - 菜单栏 + 完整工具栏
 * - A4 纸张样式编辑体验
 *
 * AI 请求通过后端代理（/api/ai/proxy）转发到 Grok/OpenAI API，
 * API Key 仅在服务端注入，前端无需持有密钥。
 */
@Route(value = "ai-document-editor")
@PageTitle("AI Document Editor")
@AnonymousAllowed
@CssImport("./styles/ai-document-editor.css")
public class AIDocumentEditorView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(AIDocumentEditorView.class);

    private final boolean hasPremiumLicense;
    private final String licenseKey;
    private final CollaborationProperties collaborationProperties;
    private final AIProperties aiProperties;
    private final TurnstileProperties turnstileProperties;

    public AIDocumentEditorView(CKEditorProperties ckEditorProperties,
                                CollaborationProperties collaborationProperties,
                                AIProperties aiProperties,
                                TurnstileProperties turnstileProperties) {
        this.licenseKey = ckEditorProperties.getLicenseKey();
        this.hasPremiumLicense = ckEditorProperties.hasPremiumLicense();
        this.collaborationProperties = collaborationProperties;
        this.aiProperties = aiProperties;
        this.turnstileProperties = turnstileProperties;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("ai-document-editor-view");
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
            add(titleBar, createNotConfiguredNotice(
                "Premium license 未配置，AI 功能需要有效的 CKEditor 5 Premium 许可证。"));
            return;
        }

        if (!collaborationProperties.isConfigured()) {
            add(titleBar, createNotConfiguredNotice(
                "CKEditor Cloud Services 未配置。请在 .env 中设置 CKEDITOR_CS_ENVIRONMENT_ID、" +
                "CKEDITOR_CS_API_SECRET 和 CKEDITOR_CS_WS_URL。"));
            return;
        }

        if (!aiProperties.isConfigured()) {
            add(titleBar, createNotConfiguredNotice(
                "AI 功能未配置。请在 .env 中设置 AI_API_KEY、AI_MODEL 和 AI_API_URL。"));
            return;
        }

        Div loadingOverlay = createLoadingOverlay();

        VaadinCKEditor editor = createAIDocumentEditor();
        editor.addClassName("ai-editor");
        editor.getStyle().set("visibility", "hidden");

        editor.addEditorReadyListener(e -> {
            loadingOverlay.getStyle().set("display", "none");
            editor.getStyle().remove("visibility");
        });

        // AI 侧栏容器（AI sidebar container 模式需要 DOM 元素）
        editor.setAiSidebarEnabled(true);

        Div editorWrapper = new Div(loadingOverlay, editor);
        editorWrapper.addClassName("ai-editor-container");
        editorWrapper.setWidthFull();

        add(titleBar, editorWrapper);
        setFlexGrow(1, editorWrapper);
    }

    private Div createTitleBar() {
        H2 title = new H2("AI Document Editor");
        title.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-header-text-color)");

        Span badge = new Span("AI-Powered");
        badge.getStyle()
            .set("font-size", "11px")
            .set("color", "#8b5cf6")
            .set("background", "rgba(139, 92, 246, 0.1)")
            .set("padding", "2px 8px")
            .set("border-radius", "4px")
            .set("font-weight", "500");

        Span modelBadge = new Span(aiProperties.getModel());
        modelBadge.getStyle()
            .set("font-size", "11px")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("padding", "2px 8px")
            .set("border-radius", "4px")
            .set("font-family", "monospace");

        Div leftGroup = new Div(title, badge, modelBadge);
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

        Span text = new Span("Loading AI Document Editor...");
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
        loginForm.setAction("login?redirect=/ai-document-editor");
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
     * 创建 AI Document Editor — DECOUPLED 类型 + AI 侧栏
     * 参考 CKEditor 5 Builder 的 AI Document Editor 预设
     */
    private VaadinCKEditor createAIDocumentEditor() {
        VaadinCKEditorBuilder builder = VaadinCKEditor.create()
            .withType(CKEditorType.DECOUPLED)
            .withLicenseKey(licenseKey)
            .withLanguage("en")
            // 使用 AI_DOCUMENT 预设（包含完整标准插件集）
            .withPreset(CKEditorPreset.AI_DOCUMENT)
            .withWidth("100%")
            .withHeight("calc(100vh - 80px)")
            // AI 插件（v47.5.0 模块化）
            .addCustomPlugin(CustomPlugin.fromPremium("AIEditorIntegration"))
            .addCustomPlugin(CustomPlugin.fromPremium("AIQuickActions"))
            .addCustomPlugin(CustomPlugin.fromPremium("AIReviewMode"))
            .addCustomPlugin(CustomPlugin.fromPremium("AITranslate"))
            // Comments + Track Changes — AI 建议以修订模式呈现（TrackChanges 依赖 Comments）
            .addCustomPlugin(CustomPlugin.builder("Comments").premium()
                .withToolbarItems("comment").build())
            .addCustomPlugin(CustomPlugin.builder("TrackChanges").premium()
                .withToolbarItems("trackChanges").build())
            // 额外 premium 插件
            .addCustomPlugin(CustomPlugin.fromPremium("FormatPainter"))
            .addCustomPlugin(CustomPlugin.fromPremium("PasteFromOfficeEnhanced"))
            .addCustomPlugin(CustomPlugin.fromPremium("SlashCommand"))
            .addCustomPlugin(CustomPlugin.fromPremium("LineHeight"))
            .addCustomPlugin(CustomPlugin.fromPremium("AIChat"));

        // 工具栏（AI toolbar items + 标准编辑工具）
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
        config.setLanguage("en");
        config.set("placeholder", StringNode.valueOf("Start writing or ask AI for help..."));

        // Cloud Services — AI 插件初始化要求 tokenUrl
        ObjectNode cloudServicesNode = createObjectNode();
        cloudServicesNode.put("tokenUrl", "/api/ckeditor/token");
        cloudServicesNode.put("webSocketUrl", collaborationProperties.getWebSocketUrl());
        config.set("cloudServices", cloudServicesNode);

        // collaboration — channelId（按用户隔离）
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        String userChannelId = "ai-document-editor-" +
            currentAuth.getName().replaceAll("\\s+", "-").toLowerCase();
        ObjectNode collaborationNode = createObjectNode();
        collaborationNode.put("channelId", userChannelId);
        config.set("collaboration", collaborationNode);

        // 初始内容
        config.set("initialData", StringNode.valueOf(getInitialContent()));

        // comments 配置 — 消除 "Missing comments editor configuration" 警告
        ObjectNode commentsNode = createObjectNode();
        commentsNode.set("editorConfig", createObjectNode());
        config.set("comments", commentsNode);

        // AI 配置（sidebar container 模式）
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
        openAINode.put("apiUrl", aiProperties.getApiUrl());
        openAINode.put("model", aiProperties.getModel());
        ObjectNode requestHeadersNode = createObjectNode();
        requestHeadersNode.put("Content-Type", "application/json");
        openAINode.set("requestHeaders", requestHeadersNode);

        ObjectNode aiNode = createObjectNode();
        aiNode.set("container", containerNode);
        aiNode.set("chat", chatNode);
        aiNode.set("openAI", openAINode);

        config.set("ai", aiNode);

        // menuBar 可见
        ObjectNode menuBarNode = createObjectNode();
        menuBarNode.put("isVisible", true);
        config.set("menuBar", menuBarNode);

        builder.withConfig(config);

        return builder.build();
    }

    /**
     * 初始文档内容 — 展示 AI 编辑器能力
     */
    private String getInitialContent() {
        return """
            <h2>AI-Powered Document Editor</h2>
            <p>Welcome to the <strong>AI Document Editor</strong> — a full-featured writing environment \
            with an integrated AI assistant. Use the AI sidebar to chat, generate content, and get \
            writing suggestions powered by <mark class="marker-blue">%s</mark>.</p>
            <hr>
            <h3>Getting Started with AI</h3>
            <p>There are several ways to interact with the AI assistant:</p>
            <ol>
                <li><strong>AI Chat</strong> — Click the <em>Toggle AI</em> button in the toolbar to open \
                the AI sidebar. Ask questions, generate content, or get writing assistance.</li>
                <li><strong>Quick Actions</strong> — Select any text, then click <em>AI Quick Actions</em> \
                to rewrite, summarize, expand, or translate the selected content.</li>
                <li><strong>AI Review</strong> — Ask the AI to review your document for grammar, style, \
                and tone improvements.</li>
                <li><strong>AI Translate</strong> — Translate selected content into another language.</li>
            </ol>
            <h3>Document Context</h3>
            <p>The AI assistant has access to the <strong>full document context</strong>, meaning it can:</p>
            <ul>
                <li>Understand the overall structure and topic of your document</li>
                <li>Generate content that matches your writing style</li>
                <li>Answer questions about specific sections</li>
                <li>Suggest improvements based on the complete context</li>
            </ul>
            <h3>Example: Technical Specification</h3>
            <p>Below is a sample section you can experiment with. Try selecting the text and using \
            AI Quick Actions to improve, expand, or translate it.</p>
            <blockquote><p>The system processes incoming requests through a pipeline of middleware \
            components. Each component can inspect, modify, or reject the request before passing it \
            to the next handler. The pipeline supports both synchronous and asynchronous processing \
            modes, with automatic fallback to synchronous when the async runtime is unavailable.</p></blockquote>
            <h3>Code Example</h3>
            <p>Ask the AI to explain this code, add documentation, or suggest improvements:</p>
            <pre><code class="language-java">public class DocumentProcessor {
    private final List&lt;Middleware&gt; pipeline;

    public CompletableFuture&lt;Document&gt; process(Document doc) {
        return pipeline.stream()
            .reduce(
                CompletableFuture.completedFuture(doc),
                (future, middleware) -&gt; future.thenCompose(middleware::apply),
                (a, b) -&gt; a.thenCombine(b, (d1, d2) -&gt; d2)
            );
    }
}</code></pre>
            <h3>Data Table</h3>
            <p>You can ask the AI to analyze, extend, or reformat tabular data:</p>
            <figure class="table">
                <table>
                    <thead>
                        <tr><th>Feature</th><th>Status</th><th>Priority</th><th>Notes</th></tr>
                    </thead>
                    <tbody>
                        <tr><td>AI Chat</td><td><mark class="marker-green">Complete</mark></td><td>P0</td><td>Multi-turn conversations with document context</td></tr>
                        <tr><td>Quick Actions</td><td><mark class="marker-green">Complete</mark></td><td>P0</td><td>Rewrite, summarize, expand, translate</td></tr>
                        <tr><td>AI Review</td><td><mark class="marker-yellow">In Progress</mark></td><td>P1</td><td>Grammar, style, and tone analysis</td></tr>
                        <tr><td>Custom Prompts</td><td>Planned</td><td>P2</td><td>User-defined AI commands</td></tr>
                    </tbody>
                </table>
            </figure>
            <hr>
            <p><em>Try it now: Select any text above and click the AI Quick Actions button, \
            or open the AI sidebar to start a conversation.</em></p>
            """.formatted(aiProperties.getModel());
    }
}
