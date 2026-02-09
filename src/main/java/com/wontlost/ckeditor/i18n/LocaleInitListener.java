package com.wontlost.ckeditor.i18n;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Vaadin 服务初始化监听器
 * 在 UI 初始化时应用保存的语言设置
 */
@Component
public class LocaleInitListener implements VaadinServiceInitListener {

    private static final String LOCALE_SESSION_KEY = "app.locale";

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInitEvent -> {
            VaadinSession session = VaadinSession.getCurrent();
            if (session != null) {
                Locale savedLocale = (Locale) session.getAttribute(LOCALE_SESSION_KEY);
                if (savedLocale != null) {
                    uiInitEvent.getUI().setLocale(savedLocale);
                } else {
                    // 默认使用中文
                    uiInitEvent.getUI().setLocale(TranslationProvider.LOCALE_ZH);
                }
            }
        });
    }
}
