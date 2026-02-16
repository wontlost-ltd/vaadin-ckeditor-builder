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
 * Collaborative Document Editor View
 *
 * 基于 CKEditor 5 Cloud Services 的实时协作编辑器，参考 CKEditor 5 Builder 的
 * Collaborative Document Editor 实现。
 *
 * 功能特性：
 * - 实时协作编辑（RealTimeCollaboration）
 * - 在线用户列表（PresenceList）
 * - 行内评论（Comments）
 * - 修订追踪（TrackChanges）
 * - 版本历史（RevisionHistory）
 * - 分页布局（Pagination）
 * - 多级列表（MultiLevelList）
 * - 三栏布局：Document Outline | 编辑器 | 批注侧栏
 * - A4 纸张样式编辑体验
 *
 * 用户身份从 Spring Security 认证上下文获取（由 CollaborationTokenController 处理）。
 * 需要配置 CKEditor Cloud Services 凭证（environment-id、api-secret、web-socket-url）。
 */
@Route(value = "collaborative-document-editor")
@PageTitle("Collaborative Document Editor")
@AnonymousAllowed
@CssImport("./styles/collaborative-document-editor.css")
public class CollaborativeDocumentEditorView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(CollaborativeDocumentEditorView.class);

    private final boolean hasPremiumLicense;
    private final String licenseKey;
    private final CollaborationProperties collaborationProperties;
    private final TurnstileProperties turnstileProperties;

    public CollaborativeDocumentEditorView(CKEditorProperties ckEditorProperties,
                                           CollaborationProperties collaborationProperties,
                                           TurnstileProperties turnstileProperties) {
        this.licenseKey = ckEditorProperties.getLicenseKey();
        this.hasPremiumLicense = ckEditorProperties.hasPremiumLicense();
        this.collaborationProperties = collaborationProperties;
        this.turnstileProperties = turnstileProperties;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("collaborative-document-editor-view");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // 未登录用户显示登录弹窗，登录成功后刷新页面加载编辑器
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

        // 加载指示器 — 编辑器初始化完成前显示
        Div loadingOverlay = createLoadingOverlay();

        // 创建协作编辑器 — 用户身份由 token 端点从 Spring Security 认证上下文获取
        VaadinCKEditor editor = createCollaborativeEditor();
        editor.addClassName("collaborative-editor");
        editor.getStyle().set("visibility", "hidden");

        // 编辑器就绪后隐藏加载指示器、显示编辑器，并启用侧栏滚动同步
        editor.addEditorReadyListener(e -> {
            loadingOverlay.getStyle().set("display", "none");
            editor.getStyle().remove("visibility");

            // 侧栏批注对齐：将每个 ck-sidebar-item 绝对定位到对应 ck-comment-marker 的视口 Y 坐标。
            // 编辑器有两层滚动：wrapper（外层）和 editable（内层），侧栏不滚动。
            // 当批注重叠时自动堆叠（stack），保证最近的 marker 对应的批注排在最上面。
            editor.getElement().executeJs(
                "const el = this;" +
                "const wrapper = el.querySelector('.editor-container__editor-wrapper');" +
                "const sidebar = el.querySelector('.annotation-sidebar-container');" +
                "if (!wrapper || !sidebar) return;" +
                "const editable = wrapper.querySelector('.ck-editor__editable');" +
                "if (!editable) return;" +
                // 侧栏不滚动，由 JS 绝对定位每个批注卡片
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
                // 堆叠：如果与上一个批注重叠，向下推
                "    if (desiredTop < lastBottom + 4) {" +
                "      desiredTop = lastBottom + 4;" +
                "    }" +
                "    items[i].style.position = 'absolute';" +
                "    items[i].style.top = desiredTop + 'px';" +
                "    items[i].style.width = '100%';" +
                "    lastBottom = desiredTop + items[i].offsetHeight;" +
                "  }" +
                // 超出 markers 数量的 sidebar items（如新建评论的输入框）也绝对定位
                "  for (let i = n; i < items.length; i++) {" +
                "    items[i].style.position = 'absolute';" +
                "    items[i].style.top = (lastBottom + 4) + 'px';" +
                "    items[i].style.width = '100%';" +
                "    lastBottom += items[i].offsetHeight + 4;" +
                "  }" +
                "}" +
                "wrapper.addEventListener('scroll', syncPositions);" +
                "editable.addEventListener('scroll', syncPositions);" +
                // 初始对齐
                "syncPositions();" +
                // MutationObserver 监听侧栏变化（新增/删除评论），自动重新对齐
                "new MutationObserver(syncPositions).observe(sidebar, {childList:true, subtree:true, attributes:true});"
            );
        });

        // 启用三栏布局：Document Outline（左）| 编辑器（中）| 批注侧栏（右）
        editor.setDocumentOutlineEnabled(true);
        editor.setAnnotationSidebarEnabled(true);
        // 编辑器容器
        Div editorWrapper = new Div(loadingOverlay, editor);
        editorWrapper.addClassName("collaborative-editor-container");
        editorWrapper.setWidthFull();

        add(titleBar, editorWrapper);
        setFlexGrow(1, editorWrapper);
    }

    private Div createTitleBar() {
        H2 title = new H2("Collaborative Document Editor");
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

        // 左侧：标题 + 徽章
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

        // 右侧：已认证时显示用户名 + 退出按钮
        if (isAuthenticated()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Span userLabel = new Span(auth.getName());
            userLabel.getStyle()
                .set("font-size", "14px")
                .set("color", "var(--lumo-secondary-text-color)");

            Button logoutBtn = new Button(VaadinIcon.SIGN_OUT.create());
            logoutBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
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
            rightGroup.addClassName("title-bar-user-actions");
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
        notice.addClassName("collaboration-not-configured");
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

        Span text = new Span(message);

        notice.add(icon, text);
        return notice;
    }

    private Div createLoadingOverlay() {
        Div spinner = new Div();
        spinner.addClassName("loading-spinner");

        Span text = new Span("Loading collaborative editor...");
        text.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "14px")
            .set("margin-top", "16px");

        Div overlay = new Div(spinner, text);
        overlay.addClassName("loading-overlay");
        return overlay;
    }

    /**
     * 在编辑器区域内显示登录表单（非全屏覆盖层），提交到 Spring Security 的 /login 端点。
     * 登录成功后 Spring Security 重定向回当前页面，触发 beforeEnter 重新加载编辑器。
     */
    private void showLoginForm() {
        LoginForm loginForm = new LoginForm();
        loginForm.setAction("login?redirect=/collaborative-document-editor");
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
        loginContainer.addClassName("login-form-container");
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
     * 创建协作编辑器 — 对齐 CKEditor 5 官方 Collaborative Document Editor 功能集
     * 用户身份由 token 端点从 Spring Security 认证上下文获取
     */
    private VaadinCKEditor createCollaborativeEditor() {
        VaadinCKEditorBuilder builder = VaadinCKEditor.create()
            .withType(CKEditorType.DECOUPLED)
            .withLicenseKey(licenseKey)
            .withLanguage("en")
            .withPreset(CKEditorPreset.DOCUMENT)
            // 基础插件
            .addPlugin(CKEditorPlugin.AUTOFORMAT)
            .addPlugin(CKEditorPlugin.TEXT_TRANSFORMATION)
            .addPlugin(CKEditorPlugin.MENTION)
            .addPlugin(CKEditorPlugin.IMAGE_BLOCK)
            .addPlugin(CKEditorPlugin.IMAGE_INLINE)
            .addPlugin(CKEditorPlugin.IMAGE_TOOLBAR)
            .addPlugin(CKEditorPlugin.IMAGE_CAPTION)
            .addPlugin(CKEditorPlugin.IMAGE_STYLE)
            .addPlugin(CKEditorPlugin.IMAGE_RESIZE)
            .addPlugin(CKEditorPlugin.LINK_IMAGE)
            .addPlugin(CKEditorPlugin.AUTO_IMAGE)
            .addPlugin(CKEditorPlugin.TABLE_CAPTION)
            .addPlugin(CKEditorPlugin.TABLE_COLUMN_RESIZE)
            .addPlugin(CKEditorPlugin.EMOJI)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS)
            .addPlugin(CKEditorPlugin.SPECIAL_CHARACTERS_ESSENTIALS)
            .addPlugin(CKEditorPlugin.FULLSCREEN)
            .addPlugin(CKEditorPlugin.HIGHLIGHT)
            // 浮动工具栏 — 选中文本时自动弹出
            .addPlugin(CKEditorPlugin.BALLOON_TOOLBAR)
            // 文档增强插件
            .addCustomPlugin(CustomPlugin.fromPremium("DocumentOutline"))
            .addCustomPlugin(CustomPlugin.builder("TableOfContents").premium()
                .withToolbarItems("tableOfContents").build())
            .addCustomPlugin(CustomPlugin.builder("FormatPainter").premium()
                .withToolbarItems("formatPainter").build())
            .addCustomPlugin(CustomPlugin.builder("CaseChange").premium()
                .withToolbarItems("caseChange").build())
            .addCustomPlugin(CustomPlugin.fromPremium("SlashCommand"))
            .addCustomPlugin(CustomPlugin.builder("MultiLevelList").premium()
                .withToolbarItems("multiLevelList").build())
            .addCustomPlugin(CustomPlugin.fromPremium("PasteFromOfficeEnhanced"))
            .addCustomPlugin(CustomPlugin.fromPremium("Pagination"))
            // 协作插件（使用具体插件名，非 meta 插件）
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
            // 导入导出
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
            // 工具栏
            .withToolbar(getCollaborativeToolbar())
            // 尺寸
            .withHeight("700px")
            .withWidth("100%");

        // Cloud Services 配置
        CKEditorConfig config = new CKEditorConfig();
        config.setLanguage("en");

        // cloudServices 配置 — 用户身份由 token endpoint 从 Spring Security 认证会话获取
        ObjectNode cloudServicesNode = createObjectNode();
        cloudServicesNode.put("tokenUrl", "/api/ckeditor/token");
        cloudServicesNode.put("webSocketUrl", collaborationProperties.getWebSocketUrl());
        config.set("cloudServices", cloudServicesNode);

        // collaboration 配置 — channelId 用于标识协作文档
        ObjectNode collaborationNode = createObjectNode();
        collaborationNode.put("channelId", "collab-services-agreement-v3");
        config.set("collaboration", collaborationNode);

        // initialData — 首次创建 channel 时的初始文档内容
        config.set("initialData", StringNode.valueOf(getInitialContent()));

        // comments 配置 — 提供 editorConfig 消除 "Missing comments editor configuration" 警告
        ObjectNode commentsNode = createObjectNode();
        commentsNode.set("editorConfig", createObjectNode());
        config.set("comments", commentsNode);

        // pagination 配置 — A4 页面尺寸，匹配官方示例
        config.setPagination("21cm", "29.7cm",
            new CKEditorConfig.PaginationMargins("20mm", "12mm", "20mm", "12mm"));

        // image 工具栏配置
        config.setImage(
            new String[] {
                "imageTextAlternative", "toggleImageCaption",
                "|", "imageStyle:inline", "imageStyle:wrapText", "imageStyle:breakText",
                "|", "resizeImage"
            },
            null
        );

        // balloonToolbar — 选中文本时弹出的浮动工具栏
        config.set("balloonToolbar", toArrayNode(new String[] {
            "comment", "|",
            "bold", "italic", "|",
            "link", "insertImage", "|",
            "bulletedList", "numberedList"
        }));

        // menuBar 可见
        ObjectNode menuBarNode = createObjectNode();
        menuBarNode.put("isVisible", true);
        config.set("menuBar", menuBarNode);

        builder.withConfig(config);

        return builder.build();
    }

    /**
     * 初始文档内容 — 参照 CKEditor 5 Builder 的 SERVICES AGREEMENT 示例
     */
    private String getInitialContent() {
        return """
            <h2>SERVICES AGREEMENT</h2>
            <p>This Contract for Services Agreement (the "<strong>Agreement</strong>") is made and entered into \
            as of <em>January 1, 2025</em> (the "<strong>Effective Date</strong>"), by and between \
            <strong>Acme Corporation</strong>, a corporation with its principal place of business at \
            123 Main Street (the "<strong>Client</strong>"), and <strong>TechBuild Solutions</strong>, \
            a corporation with its principal place of business at 456 Innovation Drive \
            (the "<strong>Service Provider</strong>").</p>\
            <h3>Scope of Services</h3>\
            <p>The Service Provider shall provide the following services to the Client (the "<strong>Services</strong>"):</p>\
            <ol>\
                <li>Requirements Gathering.\
                    <ol>\
                        <li>Initial Consultation.\
                            <ol>\
                                <li>Meet with the client to discuss project needs.</li>\
                                <li>Document key requirements and objectives.</li>\
                            </ol>\
                        </li>\
                        <li>Feasibility Analysis.</li>\
                        <li>Requirements Specification.</li>\
                    </ol>\
                </li>\
                <li>Development.\
                    <ol>\
                        <li>System Design.</li>\
                        <li>Coding and Implementation.\
                            <ol>\
                                <li>Conduct regular code reviews and refactor as needed.</li>\
                            </ol>\
                        </li>\
                        <li>Testing and Quality Assurance.</li>\
                    </ol>\
                </li>\
                <li>Deployment and Support.\
                    <ol>\
                        <li>Deployment Preparation.\
                            <ol>\
                                <li>Set up the production environment.</li>\
                                <li>Create deployment scripts and configuration files.</li>\
                            </ol>\
                        </li>\
                    </ol>\
                </li>\
            </ol>\
            <h3>Term</h3>\
            <p>This Agreement shall commence on the Effective Date and shall continue until \
            <em>December 31, 2025</em>, unless earlier terminated as provided herein (the "<strong>Term</strong>").</p>\
            <h3>Compensation</h3>\
            <p>In consideration of the Services to be provided by the Service Provider, the Client shall \
            pay the Service Provider the fees set forth in <a href="http://example.com/">Exhibit A</a> \
            attached hereto and incorporated herein by reference (the "<strong>Fees</strong>").</p>\
            <p>The Client shall pay the Fees within <strong>30</strong> days of receipt of an invoice \
            from the Service Provider.</p>\
            <p>If any Fees are not paid when due, the Service Provider may, in its sole discretion, \
            suspend or terminate the Services.</p>\
            <h4>Late Fees</h4>\
            <p>If any payment is not received by the Service Provider within <strong>15</strong> days \
            of its due date, the Client shall pay a late fee equal to <strong>5%</strong> of the unpaid amount. \
            The following table sets forth the specific late fee percentages:</p>\
            <figure class="table">\
                <table>\
                    <thead>\
                        <tr><th>Days Past Due</th><th><strong>Late Fee Percentage</strong></th></tr>\
                    </thead>\
                    <tbody>\
                        <tr><td>1-30 days</td><td>2%</td></tr>\
                        <tr><td>31-60 days</td><td>5%</td></tr>\
                        <tr><td>61-90 days</td><td>8%</td></tr>\
                        <tr><td>Over 90 days</td><td>12%</td></tr>\
                    </tbody>\
                </table>\
            </figure>\
            <h3>Termination</h3>\
            <p>This Agreement may be terminated:</p>\
            <ol>\
                <li>By either party upon <strong>30</strong> days' written notice to the other party;</li>\
                <li>By the Client upon the occurrence of a material breach by the Service Provider \
                that is not cured within <strong>15</strong> days after written notice; or</li>\
                <li>By the Service Provider upon the occurrence of a material breach by the Client \
                that is not cured within <strong>15</strong> days after written notice.</li>\
            </ol>\
            <h3>Effect of Termination</h3>\
            <p>Upon termination of this Agreement for any reason, the Service Provider shall immediately \
            cease providing the Services, and the Client shall pay the Service Provider for all Services \
            performed prior to the effective date of termination.</p>\
            <h3>Confidentiality</h3>\
            <p>The Service Provider agrees to keep confidential all information and materials disclosed by \
            the Client to the Service Provider in connection with the Services \
            (the "<strong>Confidential Information</strong>").</p>\
            <p>The Service Provider shall not use the Confidential Information for any purpose other than \
            to perform the Services.</p>\
            <p>The Service Provider shall take reasonable measures to protect the confidentiality of the \
            Confidential Information.</p>\
            <h3>Exceptions</h3>\
            <p>The obligations of confidentiality shall not apply to any Confidential Information that:</p>\
            <ul>\
                <li>is already known to the Service Provider prior to its disclosure by the Client;</li>\
                <li>is or becomes publicly known through no fault of the Service Provider; or</li>\
                <li>is obtained by the Service Provider from a third party without a breach of any obligation.</li>\
            </ul>\
            <h3>Representations and Warranties</h3>\
            <p>The Service Provider represents and warrants that it has the necessary expertise, \
            qualifications, and experience to perform the Services.</p>\
            <p>The Client represents and warrants that it has the legal right to engage the Service Provider \
            to perform the Services.</p>\
            <h3>Disclaimer of Other Warranties</h3>\
            <p>Except for the express warranties set forth in this Agreement, the Service Provider makes \
            no other warranties, express or implied, with respect to the Services, including, without limitation, \
            any implied warranties of merchantability or fitness for a particular purpose.</p>\
            """;
    }

    /**
     * 协作编辑器工具栏 — 对齐官方 Collaborative Document Editor 示例
     */
    private String[] getCollaborativeToolbar() {
        return new String[] {
            "undo", "redo", "|",
            "revisionHistory", "|",
            "trackChanges", "comment", "|",
            "importWord", "exportWord", "exportPdf",
            "formatPainter", "caseChange", "findAndReplace", "fullscreen", "|",
            "heading", "|",
            "fontSize", "fontFamily", "fontColor", "fontBackgroundColor", "|",
            "bold", "italic", "underline", "strikethrough",
            "subscript", "superscript", "removeFormat", "|",
            "emoji", "specialCharacters", "horizontalLine", "pageBreak",
            "link", "insertImage", "highlight", "insertTable",
            "tableOfContents", "|",
            "alignment", "|",
            "bulletedList", "numberedList", "multiLevelList", "todoList",
            "outdent", "indent"
        };
    }

}
