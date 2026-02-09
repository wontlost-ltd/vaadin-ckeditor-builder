package com.wontlost.ckeditor.theme;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.VaadinSession;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用主题切换组件
 * 支持浅色、深色和自适应三种模式
 * 独立于 CKEditor Addon 的主题配置
 */
public class ThemeSwitcher extends HorizontalLayout {

    /**
     * 主题模式枚举
     */
    public enum ThemeMode {
        LIGHT("light", VaadinIcon.SUN_O, "theme.light"),
        DARK("dark", VaadinIcon.MOON_O, "theme.dark"),
        AUTO("auto", VaadinIcon.ADJUST, "theme.auto");

        private final String value;
        private final VaadinIcon icon;
        private final String i18nKey;

        ThemeMode(String value, VaadinIcon icon, String i18nKey) {
            this.value = value;
            this.icon = icon;
            this.i18nKey = i18nKey;
        }

        public String getValue() {
            return value;
        }

        public VaadinIcon getIcon() {
            return icon;
        }

        public String getI18nKey() {
            return i18nKey;
        }

        public static ThemeMode fromValue(String value) {
            for (ThemeMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return AUTO;
        }
    }

    private static final String THEME_SESSION_KEY = "app-theme-mode";

    private final Button themeButton;
    private ThemeMode currentMode;

    public ThemeSwitcher() {
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.CENTER);
        addClassName("theme-switcher");

        // 获取当前主题模式
        currentMode = getCurrentThemeMode();

        // 创建主题按钮
        Icon initialIcon = currentMode.getIcon().create();
        initialIcon.setSize("18px");

        themeButton = new Button();
        themeButton.setIcon(initialIcon);
        themeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        themeButton.addClassName("theme-button");
        themeButton.getStyle()
            .set("padding", "8px")
            .set("min-width", "36px")
            .set("min-height", "36px")
            .set("cursor", "pointer")
            .set("border", "none")
            .set("background", "transparent")
            .set("border-radius", "8px");
        themeButton.getElement().setAttribute("aria-label", I18nUtil.get("theme.switch"));
        themeButton.getElement().setAttribute("title", I18nUtil.get("theme.switch"));

        // 创建上下文菜单
        ContextMenu contextMenu = new ContextMenu(themeButton);
        contextMenu.setOpenOnClick(true);

        // 添加所有主题选项
        for (ThemeMode mode : ThemeMode.values()) {
            HorizontalLayout itemLayout = new HorizontalLayout();
            itemLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            itemLayout.setSpacing(true);
            itemLayout.getStyle().set("gap", "8px");

            Icon icon = mode.getIcon().create();
            icon.setSize("16px");

            Span nameLabel = new Span(I18nUtil.get(mode.getI18nKey()));
            nameLabel.getStyle().set("font-size", "14px");

            itemLayout.add(icon, nameLabel);

            contextMenu.addItem(itemLayout, e -> selectTheme(mode));
        }

        add(themeButton);

        // 初始化时应用当前主题
        applyTheme(currentMode);
    }

    /**
     * 获取当前主题模式
     */
    private ThemeMode getCurrentThemeMode() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            String storedMode = (String) session.getAttribute(THEME_SESSION_KEY);
            if (storedMode != null) {
                return ThemeMode.fromValue(storedMode);
            }
        }
        return ThemeMode.AUTO;
    }

    /**
     * 选择主题
     */
    private void selectTheme(ThemeMode mode) {
        if (mode != currentMode) {
            currentMode = mode;

            // 保存到 session
            VaadinSession session = VaadinSession.getCurrent();
            if (session != null) {
                session.setAttribute(THEME_SESSION_KEY, mode.getValue());
            }

            // 更新按钮图标
            updateButtonIcon(mode);

            // 应用主题
            applyTheme(mode);
        }
    }

    /**
     * 更新按钮图标
     */
    private void updateButtonIcon(ThemeMode mode) {
        // Button 不支持 removeAll，使用 setIcon 代替
        Icon newIcon = mode.getIcon().create();
        newIcon.setSize("18px");
        themeButton.setIcon(newIcon);
    }

    /**
     * 应用主题到 UI
     */
    private void applyTheme(ThemeMode mode) {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        ui.getElement().executeJs(
            """
            (function() {
                const html = document.documentElement;
                const mode = $0;

                // 移除现有主题类
                html.removeAttribute('theme');

                if (mode === 'dark') {
                    html.setAttribute('theme', 'dark');
                } else if (mode === 'auto') {
                    // 检测系统偏好
                    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                    if (prefersDark) {
                        html.setAttribute('theme', 'dark');
                    }

                    // 监听系统主题变化
                    if (!window._themeMediaQueryListener) {
                        window._themeMediaQueryListener = window.matchMedia('(prefers-color-scheme: dark)');
                        window._themeMediaQueryListener.addEventListener('change', function(e) {
                            const storedMode = sessionStorage.getItem('app-theme-mode') || 'auto';
                            if (storedMode === 'auto') {
                                if (e.matches) {
                                    html.setAttribute('theme', 'dark');
                                } else {
                                    html.removeAttribute('theme');
                                }
                            }
                        });
                    }
                }
                // 'light' mode: no theme attribute (default)

                // 同步到 sessionStorage 供 JS 读取
                sessionStorage.setItem('app-theme-mode', mode);
            })();
            """,
            mode.getValue()
        );
    }

    /**
     * 获取当前主题模式
     */
    public ThemeMode getThemeMode() {
        return currentMode;
    }
}
