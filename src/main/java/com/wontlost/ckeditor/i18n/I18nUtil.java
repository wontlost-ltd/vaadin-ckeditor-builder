package com.wontlost.ckeditor.i18n;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import java.util.Locale;

/**
 * 国际化工具类
 * 提供便捷的翻译获取方法
 */
public final class I18nUtil {

    private static final String LOCALE_SESSION_KEY = "app.locale";

    private I18nUtil() {
        // 工具类不允许实例化
    }

    /**
     * 获取当前 UI 的语言设置
     */
    public static Locale getCurrentLocale() {
        // 优先从 session 获取
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            Locale sessionLocale = (Locale) session.getAttribute(LOCALE_SESSION_KEY);
            if (sessionLocale != null) {
                return sessionLocale;
            }
        }

        UI ui = UI.getCurrent();
        if (ui != null) {
            return ui.getLocale();
        }
        return TranslationProvider.LOCALE_EN; // 默认英语
    }

    /**
     * 设置当前 UI 的语言
     */
    public static void setLocale(Locale locale) {
        // 保存到 session
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(LOCALE_SESSION_KEY, locale);
        }

        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.setLocale(locale);
            // 刷新页面以应用新语言
            ui.getPage().reload();
        }
    }

    /**
     * 获取翻译文本
     */
    public static String get(String key) {
        return get(key, getCurrentLocale());
    }

    /**
     * 获取带参数的翻译文本
     */
    public static String get(String key, Object... params) {
        return get(key, getCurrentLocale(), params);
    }

    /**
     * 获取指定语言的翻译文本
     */
    public static String get(String key, Locale locale, Object... params) {
        I18NProvider provider = VaadinService.getCurrent().getInstantiator().getI18NProvider();
        if (provider != null) {
            return provider.getTranslation(key, locale, params);
        }
        return key;
    }

    /**
     * 切换语言
     */
    public static void toggleLanguage() {
        Locale current = getCurrentLocale();
        if (current.getLanguage().equals("zh")) {
            setLocale(TranslationProvider.LOCALE_EN);
        } else {
            setLocale(TranslationProvider.LOCALE_ZH);
        }
    }

    /**
     * 判断当前是否为中文
     */
    public static boolean isChinese() {
        return getCurrentLocale().getLanguage().equals("zh");
    }

    /**
     * 判断当前是否为英文
     */
    public static boolean isEnglish() {
        return getCurrentLocale().getLanguage().equals("en");
    }
}
