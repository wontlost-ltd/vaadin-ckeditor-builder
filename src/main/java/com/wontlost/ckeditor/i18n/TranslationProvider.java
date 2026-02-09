package com.wontlost.ckeditor.i18n;

import com.vaadin.flow.i18n.I18NProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.*;

/**
 * 国际化翻译提供器
 * 支持6种全球主要语言：中文、英语、西班牙语、阿拉伯语、法语、俄语
 */
@Component
public class TranslationProvider implements I18NProvider {

    private static final Logger log = LoggerFactory.getLogger(TranslationProvider.class);

    // 6种全球最广泛使用的语言
    public static final Locale LOCALE_ZH = Locale.SIMPLIFIED_CHINESE;  // 中文
    public static final Locale LOCALE_EN = Locale.ENGLISH;              // 英语
    public static final Locale LOCALE_ES = new Locale("es");            // 西班牙语
    public static final Locale LOCALE_AR = new Locale("ar");            // 阿拉伯语
    public static final Locale LOCALE_FR = Locale.FRENCH;               // 法语
    public static final Locale LOCALE_RU = new Locale("ru");            // 俄语

    // 英语作为默认语言放在第一位
    private final List<Locale> locales = List.of(
        LOCALE_EN, LOCALE_ZH, LOCALE_ES, LOCALE_FR, LOCALE_RU, LOCALE_AR
    );

    private final Map<String, ResourceBundle> bundles = new HashMap<>();

    public TranslationProvider() {
        // 预加载资源包
        for (Locale locale : locales) {
            try {
                bundles.put(locale.getLanguage(),
                    ResourceBundle.getBundle("i18n/messages", locale));
            } catch (MissingResourceException e) {
                log.warn("Could not find resource bundle for locale: {}", locale);
            }
        }
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return locales;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) {
            log.warn("Got null key for translation");
            return "";
        }

        ResourceBundle bundle = bundles.get(locale.getLanguage());
        if (bundle == null) {
            // 如果没有找到对应语言，使用英语作为默认
            bundle = bundles.get(LOCALE_EN.getLanguage());
        }

        if (bundle == null) {
            log.warn("No resource bundle found for locale: {}", locale);
            return key;
        }

        try {
            String value = bundle.getString(key);
            if (params != null && params.length > 0) {
                return MessageFormat.format(value, params);
            }
            return value;
        } catch (MissingResourceException e) {
            log.debug("Missing translation for key: {} in locale: {}", key, locale);
            return key;
        }
    }
}
