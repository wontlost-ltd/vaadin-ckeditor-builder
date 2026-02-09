package com.wontlost.ckeditor.i18n;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 语言切换组件
 * 点击显示下拉菜单，用户可自由选择语言
 * 使用 Vaadin 原生图标
 * 默认语言为英语
 */
public class LanguageSwitcher extends HorizontalLayout {

    /**
     * 支持的语言映射：Locale -> 语言信息（图标 + 名称）
     * 英语放在第一位作为默认语言
     */
    private static final Map<Locale, LanguageInfo> SUPPORTED_LANGUAGES = new LinkedHashMap<>();

    static {
        // 英语作为默认语言放在第一位
        SUPPORTED_LANGUAGES.put(TranslationProvider.LOCALE_EN, new LanguageInfo(VaadinIcon.FLAG, "English", "EN"));
        SUPPORTED_LANGUAGES.put(TranslationProvider.LOCALE_ZH, new LanguageInfo(VaadinIcon.FLAG_O, "中文", "ZH"));
        SUPPORTED_LANGUAGES.put(TranslationProvider.LOCALE_ES, new LanguageInfo(VaadinIcon.FLAG, "Español", "ES"));
        SUPPORTED_LANGUAGES.put(TranslationProvider.LOCALE_FR, new LanguageInfo(VaadinIcon.FLAG_O, "Français", "FR"));
        SUPPORTED_LANGUAGES.put(TranslationProvider.LOCALE_RU, new LanguageInfo(VaadinIcon.FLAG, "Русский", "RU"));
        SUPPORTED_LANGUAGES.put(TranslationProvider.LOCALE_AR, new LanguageInfo(VaadinIcon.FLAG_O, "العربية", "AR"));
    }

    private final Button langButton;
    private final Span langCodeLabel;

    public LanguageSwitcher() {
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.CENTER);
        addClassName("language-switcher");

        // 获取当前语言代码
        Locale currentLocale = I18nUtil.getCurrentLocale();
        String currentCode = getLanguageCode(currentLocale);

        // 创建语言代码按钮 - 无边框样式
        langCodeLabel = new Span(currentCode);
        langCodeLabel.getStyle()
            .set("font-weight", "600")
            .set("font-size", "13px")
            .set("color", "var(--lumo-secondary-text-color)");

        langButton = new Button(langCodeLabel);
        langButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        langButton.addClassName("language-code-button");
        langButton.getStyle()
            .set("padding", "4px 8px")
            .set("min-width", "auto")
            .set("cursor", "pointer")
            .set("border", "none")
            .set("background", "transparent");

        // 创建上下文菜单
        ContextMenu contextMenu = new ContextMenu(langButton);
        contextMenu.setOpenOnClick(true);

        // 添加所有语言选项
        for (Map.Entry<Locale, LanguageInfo> entry : SUPPORTED_LANGUAGES.entrySet()) {
            Locale locale = entry.getKey();
            LanguageInfo info = entry.getValue();

            // 创建菜单项内容
            HorizontalLayout itemLayout = new HorizontalLayout();
            itemLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            itemLayout.setSpacing(true);
            itemLayout.getStyle().set("gap", "8px");

            // 语言代码标签
            Span codeLabel = new Span(info.code());
            codeLabel.getStyle()
                .set("font-weight", "600")
                .set("font-size", "12px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "2px 6px")
                .set("border-radius", "4px")
                .set("min-width", "28px")
                .set("text-align", "center");

            // 语言名称
            Span nameLabel = new Span(info.name());
            nameLabel.getStyle().set("font-size", "14px");

            itemLayout.add(codeLabel, nameLabel);

            // 添加菜单项 - 不使用 checkable
            contextMenu.addItem(itemLayout, e -> selectLanguage(locale));
        }

        add(langButton);
    }

    /**
     * 获取语言代码
     */
    private String getLanguageCode(Locale locale) {
        LanguageInfo info = SUPPORTED_LANGUAGES.get(locale);
        return info != null ? info.code() : "EN";
    }

    /**
     * 选择语言
     */
    private void selectLanguage(Locale locale) {
        if (locale != null && !locale.equals(I18nUtil.getCurrentLocale())) {
            I18nUtil.setLocale(locale);
        }
    }

    /**
     * 获取支持的语言列表
     */
    public static Map<Locale, LanguageInfo> getSupportedLanguages() {
        return new LinkedHashMap<>(SUPPORTED_LANGUAGES);
    }

    /**
     * 语言信息记录
     * @param icon Vaadin 图标
     * @param name 语言原生名称
     * @param code 语言代码（2字母）
     */
    public record LanguageInfo(VaadinIcon icon, String name, String code) {}
}
