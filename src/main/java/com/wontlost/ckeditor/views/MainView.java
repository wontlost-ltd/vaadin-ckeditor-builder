package com.wontlost.ckeditor.views;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wontlost.ckeditor.i18n.I18nUtil;
import com.wontlost.ckeditor.i18n.LanguageSwitcher;
import com.wontlost.ckeditor.theme.ThemeSwitcher;
import com.wontlost.ckeditor.utils.Constant;

/**
 * 主视图 - CKEditor Builder 应用布局
 * 响应式设计：支持桌面端导航栏 + 移动端汉堡菜单
 */
@StyleSheet("https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap")
@CssImport("./styles/views/app.css")
@AnonymousAllowed
public class MainView extends AppLayout {

    /** 移动端下拉菜单的稳定 id，供汉堡按钮的 aria-controls 引用 */
    private static final String MOBILE_MENU_ID = "mobile-nav-menu";

    private Div mobileMenu;
    private Button menuButton;
    private boolean mobileMenuOpen = false;

    public MainView() {
        addClassName("app-layout");
        // 本应用的导航全部放在 navbar（桌面端）与自定义 mobileMenu 下拉（移动端），
        // 从不向 drawer 添加任何内容。但 AppLayout 始终渲染 drawer 插槽并占据左侧横向空间，
        // 表现为页面左侧一条空白间隙，故显式关闭。配套的插槽隐藏见 app.css 中 .app-layout 规则。
        setDrawerOpened(false);
        createHeader();
    }

    private void createHeader() {
        // Logo 图标
        Icon logo = VaadinIcon.EDIT.create();
        logo.setSize("20px");
        logo.getStyle().set("color", "var(--app-primary, #2563eb)");

        // 应用名称
        H1 appName = new H1("CKEditor Builder");
        appName.addClassName("app-name");
        appName.getStyle()
            .set("font-size", "16px")
            .set("margin", "0")
            .set("font-weight", "600")
            .set("color", "var(--app-text-primary, #1f2937)")
            .set("white-space", "nowrap");

        // 版本标签
        Span version = new Span("v5.3.*");
        version.addClassName("version-badge");
        version.getStyle()
            .set("font-size", "11px")
            .set("color", "var(--app-text-muted, #6b7280)")
            .set("background", "var(--app-bg-tertiary, #f3f4f6)")
            .set("padding", "2px 8px")
            .set("border-radius", "4px")
            .set("font-weight", "500");

        // 左侧品牌区
        HorizontalLayout brandSection = new HorizontalLayout(logo, appName, version);
        brandSection.addClassName("brand-section");
        brandSection.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        brandSection.setSpacing(true);
        brandSection.getStyle()
            .set("gap", "8px")
            .set("flex-shrink", "0");

        // 文档链接
        Anchor docsLink = createNavLink(
            VaadinIcon.BOOK,
            Constant.LINK_DOCS,
            I18nUtil.get("nav.docs")
        );
        docsLink.addClassName("nav-link");

        // GitHub 链接 - 使用自定义 SVG 图标
        Anchor githubLink = createGitHubLink(
            Constant.LINK_GITHUB,
            I18nUtil.get("nav.github")
        );
        githubLink.addClassName("nav-link");
        githubLink.addClassName("github-link");

        // 语言切换组件
        LanguageSwitcher languageSwitcher = new LanguageSwitcher();

        // 主题切换组件
        ThemeSwitcher themeSwitcher = new ThemeSwitcher();

        // 右侧链接区 - 桌面端显示（顺序：语言、主题、文档、GitHub）
        HorizontalLayout linksSection = new HorizontalLayout(languageSwitcher, themeSwitcher, docsLink, githubLink);
        linksSection.addClassName("nav-links");
        linksSection.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        linksSection.setSpacing(true);
        linksSection.getStyle()
            .set("gap", "var(--lumo-space-s)");

        // 移动端汉堡菜单按钮。按 WAI-ARIA disclosure 模式暴露展开状态：
        // aria-expanded 随开合更新，aria-controls 指向菜单的稳定 id，
        // 使屏幕阅读器能播报「已折叠/已展开」并跳转到被控制的菜单。
        menuButton = new Button(VaadinIcon.MENU.create());
        menuButton.addClassName("nav-menu-button");
        menuButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST);
        menuButton.getElement().setAttribute("aria-label", I18nUtil.get("nav.openMenu"));
        menuButton.getElement().setAttribute("title", I18nUtil.get("nav.menu"));
        // 不设 aria-haspopup：其值 "true" 等价于声明弹出一个 role=menu 容器，
        // 与本处受控对象 role=navigation 语义冲突；
        // W3C 的 disclosure navigation 模式也不使用该属性。
        menuButton.getElement().setAttribute("aria-expanded", "false");
        menuButton.getElement().setAttribute("aria-controls", MOBILE_MENU_ID);
        menuButton.getStyle()
            .set("color", "white")
            .set("min-width", "44px")
            .set("min-height", "44px")
            .set("display", "none"); // 默认隐藏，CSS 控制显示
        menuButton.addClickListener(e -> toggleMobileMenu());

        // 中间分隔符（弹性空间）
        Div spacer = new Div();
        spacer.getStyle().set("flex", "1");

        // 主 Header 布局
        HorizontalLayout header = new HorizontalLayout(brandSection, spacer, linksSection, menuButton);
        header.addClassName("app-header");
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.getStyle()
            .set("padding", "0 16px")
            .set("min-height", "56px");

        // 创建移动端下拉菜单。必须与 header 一同挂到 navbar：
        // 菜单用 position:absolute + top:100% 相对 navbar 定位，
        // 且移除 drawer 后汉堡按钮是移动端唯一的导航入口，未挂载会导致点击无反应。
        mobileMenu = createMobileMenu(docsLink.getHref(), githubLink.getHref());

        Div navbarWrapper = new Div(header, mobileMenu);
        navbarWrapper.getStyle()
            .set("position", "relative")
            .set("width", "100%");

        addToNavbar(navbarWrapper);

        // 键盘可达性：Esc 关闭已展开的菜单，并把焦点交还汉堡按钮，
        // 避免键盘用户在展开态被困住（WAI-ARIA disclosure 模式要求）。
        Shortcuts.addShortcutListener(this, () -> {
            if (mobileMenuOpen) {
                setMobileMenuOpen(false);
                menuButton.focus();
            }
        }, Key.ESCAPE);
    }

    /**
     * 创建导航链接
     */
    private Anchor createNavLink(VaadinIcon iconType, String href, String ariaLabel) {
        Icon icon = iconType.create();
        icon.setSize("18px");

        Anchor link = new Anchor(href, "");
        link.setTarget("_blank");
        link.getElement().setAttribute("aria-label", ariaLabel);
        link.getElement().setAttribute("title", ariaLabel);
        link.add(icon);

        return link;
    }

    /**
     * 创建 GitHub 导航链接 - 使用自定义 SVG 图标
     */
    private Anchor createGitHubLink(String href, String ariaLabel) {
        // GitHub SVG 图标
        String githubSvg = "<svg aria-hidden=\"true\" focusable=\"false\" viewBox=\"0 0 24 24\" width=\"20\" height=\"20\" fill=\"currentColor\" style=\"display:block;\">" +
            "<path d=\"M12 1C5.923 1 1 5.923 1 12c0 4.867 3.149 8.979 7.521 10.436.55.096.756-.233.756-.522 0-.262-.013-1.128-.013-2.049-2.764.509-3.479-.674-3.699-1.292-.124-.317-.66-1.293-1.127-1.554-.385-.207-.936-.715-.014-.729.866-.014 1.485.797 1.691 1.128.99 1.663 2.571 1.196 3.204.907.096-.715.385-1.196.701-1.471-2.448-.275-5.005-1.224-5.005-5.432 0-1.196.426-2.186 1.128-2.956-.111-.275-.496-1.402.11-2.915 0 0 .921-.288 3.024 1.128a10.193 10.193 0 0 1 2.75-.371c.936 0 1.871.123 2.75.371 2.104-1.43 3.025-1.128 3.025-1.128.605 1.513.221 2.64.111 2.915.701.77 1.127 1.747 1.127 2.956 0 4.222-2.571 5.157-5.019 5.432.399.344.743 1.004.743 2.035 0 1.471-.014 2.654-.014 3.025 0 .289.206.632.756.522C19.851 20.979 23 16.854 23 12c0-6.077-4.922-11-11-11Z\"></path>" +
            "</svg>";

        Html githubIcon = new Html("<span style=\"display:flex;align-items:center;justify-content:center;\">" + githubSvg + "</span>");

        Anchor link = new Anchor(href, "");
        link.setTarget("_blank");
        link.getElement().setAttribute("aria-label", ariaLabel);
        link.getElement().setAttribute("title", ariaLabel);
        link.add(githubIcon);

        return link;
    }

    /**
     * 创建移动端下拉菜单
     */
    private Div createMobileMenu(String docsHref, String githubHref) {
        Div menu = new Div();
        menu.addClassName("mobile-nav-menu");
        // 稳定 id 供 aria-controls 引用；role=navigation + aria-label 让辅助技术
        // 将其识别为独立导航区域（内含链接而非 menuitem，故用 navigation 而非 menu role）
        menu.setId(MOBILE_MENU_ID);
        menu.getElement().setAttribute("role", "navigation");
        menu.getElement().setAttribute("aria-label", I18nUtil.get("nav.menu"));
        menu.getStyle()
            .set("position", "absolute")
            .set("top", "100%")
            .set("right", "0")
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "12px")
            .set("box-shadow", "0 8px 32px rgba(0, 0, 0, 0.15)")
            .set("padding", "var(--lumo-space-s)")
            .set("min-width", "200px")
            .set("z-index", "1000")
            .set("display", "none")
            .set("margin-top", "var(--lumo-space-s)")
            .set("margin-right", "var(--lumo-space-m)");

        // 文档链接
        Anchor docsItem = createMobileMenuItem(VaadinIcon.BOOK, I18nUtil.get("nav.docs"), docsHref);
        // GitHub 链接 - 使用自定义 SVG 图标
        Anchor githubItem = createMobileMenuGitHubItem(I18nUtil.get("nav.github"), githubHref);

        VerticalLayout menuContent = new VerticalLayout(docsItem, githubItem);
        menuContent.setPadding(false);
        menuContent.setSpacing(false);
        menuContent.getStyle().set("gap", "var(--lumo-space-xs)");

        menu.add(menuContent);
        return menu;
    }

    /**
     * 创建移动端菜单项
     */
    private Anchor createMobileMenuItem(VaadinIcon iconType, String label, String href) {
        Icon icon = iconType.create();
        icon.setSize("18px");
        icon.getStyle()
            .set("color", "var(--lumo-primary-color)")
            .set("margin-right", "var(--lumo-space-s)");

        Span text = new Span(label);
        text.getStyle()
            .set("color", "var(--lumo-body-text-color)")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "500");

        HorizontalLayout content = new HorizontalLayout(icon, text);
        content.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        content.setPadding(false);

        Anchor item = new Anchor(href, "");
        item.setTarget("_blank");
        item.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
            .set("border-radius", "8px")
            .set("text-decoration", "none")
            .set("transition", "background 0.15s ease")
            .set("min-height", "44px");
        item.add(content);

        return item;
    }

    /**
     * 创建移动端 GitHub 菜单项 - 使用自定义 SVG 图标
     */
    private Anchor createMobileMenuGitHubItem(String label, String href) {
        String githubSvg = "<svg aria-hidden=\"true\" viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"currentColor\">" +
            "<path d=\"M12 1C5.923 1 1 5.923 1 12c0 4.867 3.149 8.979 7.521 10.436.55.096.756-.233.756-.522 0-.262-.013-1.128-.013-2.049-2.764.509-3.479-.674-3.699-1.292-.124-.317-.66-1.293-1.127-1.554-.385-.207-.936-.715-.014-.729.866-.014 1.485.797 1.691 1.128.99 1.663 2.571 1.196 3.204.907.096-.715.385-1.196.701-1.471-2.448-.275-5.005-1.224-5.005-5.432 0-1.196.426-2.186 1.128-2.956-.111-.275-.496-1.402.11-2.915 0 0 .921-.288 3.024 1.128a10.193 10.193 0 0 1 2.75-.371c.936 0 1.871.123 2.75.371 2.104-1.43 3.025-1.128 3.025-1.128.605 1.513.221 2.64.111 2.915.701.77 1.127 1.747 1.127 2.956 0 4.222-2.571 5.157-5.019 5.432.399.344.743 1.004.743 2.035 0 1.471-.014 2.654-.014 3.025 0 .289.206.632.756.522C19.851 20.979 23 16.854 23 12c0-6.077-4.922-11-11-11Z\"></path>" +
            "</svg>";

        Html githubIcon = new Html("<span style=\"display:flex;align-items:center;color:var(--lumo-primary-color);margin-right:var(--lumo-space-s);\">" + githubSvg + "</span>");

        Span text = new Span(label);
        text.getStyle()
            .set("color", "var(--lumo-body-text-color)")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "500");

        HorizontalLayout content = new HorizontalLayout(githubIcon, text);
        content.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        content.setPadding(false);

        Anchor item = new Anchor(href, "");
        item.setTarget("_blank");
        item.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
            .set("border-radius", "8px")
            .set("text-decoration", "none")
            .set("transition", "background 0.15s ease")
            .set("min-height", "44px");
        item.add(content);

        return item;
    }

    /**
     * 切换移动端菜单显示状态
     */
    private void toggleMobileMenu() {
        setMobileMenuOpen(!mobileMenuOpen);
    }

    /**
     * 统一设置移动端菜单开合状态。
     * 视觉状态（display）与无障碍状态（aria-expanded）必须同步更新，
     * 否则屏幕阅读器会播报与实际相反的展开状态。
     */
    private void setMobileMenuOpen(boolean open) {
        mobileMenuOpen = open;
        if (mobileMenu != null) {
            mobileMenu.getStyle().set("display", open ? "block" : "none");
        }
        if (menuButton != null) {
            menuButton.getElement().setAttribute("aria-expanded", String.valueOf(open));
            menuButton.getElement().setAttribute("aria-label",
                I18nUtil.get(open ? "nav.closeMenu" : "nav.openMenu"));
        }
    }
}
