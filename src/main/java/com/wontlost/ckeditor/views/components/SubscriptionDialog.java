package com.wontlost.ckeditor.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.util.function.Consumer;

/**
 * 订阅邀请对话框
 * 在用户复制代码或下载文件时显示，邀请用户订阅更新
 */
public class SubscriptionDialog extends Dialog {

    private final EmailField emailField;
    private final Consumer<String> onSubscribe;
    private final Runnable onSkip;

    public SubscriptionDialog(Consumer<String> onSubscribe, Runnable onSkip) {
        this.onSubscribe = onSubscribe;
        this.onSkip = onSkip;

        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        setWidth("420px");
        addClassName("subscription-dialog");

        // 内容布局
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setAlignItems(FlexComponent.Alignment.CENTER);

        // 图标
        Div iconWrapper = new Div();
        iconWrapper.addClassName("subscription-icon-wrapper");
        Icon mailIcon = VaadinIcon.ENVELOPE_O.create();
        mailIcon.setSize("48px");
        mailIcon.addClassName("subscription-icon");
        iconWrapper.add(mailIcon);

        // 标题
        H3 title = new H3(I18nUtil.get("subscribe.dialog.title"));
        title.addClassName("subscription-title");

        // 提示文本
        Paragraph hint = new Paragraph(I18nUtil.get("subscribe.dialog.hint"));
        hint.addClassName("subscription-hint");

        // 邮箱输入框
        emailField = new EmailField();
        emailField.setPlaceholder(I18nUtil.get("subscribe.email.placeholder"));
        emailField.setWidthFull();
        emailField.setClearButtonVisible(true);
        emailField.setPrefixComponent(VaadinIcon.ENVELOPE.create());
        emailField.setErrorMessage(I18nUtil.get("subscribe.email.error"));

        // 隐私说明
        Paragraph privacy = new Paragraph(I18nUtil.get("subscribe.privacy"));
        privacy.addClassName("subscription-privacy");

        // 按钮区域
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.setSpacing(true);

        Button skipBtn = new Button(I18nUtil.get("subscribe.button.skip"));
        skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        skipBtn.addClickListener(e -> handleSkip());

        Button subscribeBtn = new Button(I18nUtil.get("subscribe.button.confirm"));
        subscribeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        subscribeBtn.addClickListener(e -> handleSubscribe());

        buttons.add(skipBtn, subscribeBtn);

        content.add(iconWrapper, title, hint, emailField, privacy, buttons);
        add(content);

        // 回车键提交
        emailField.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> handleSubscribe());
    }

    private void handleSubscribe() {
        String email = emailField.getValue();
        if (email != null && !email.isBlank() && emailField.isInvalid() == false) {
            // 验证邮箱格式
            if (isValidEmail(email)) {
                onSubscribe.accept(email.trim());
                close();
            } else {
                emailField.setInvalid(true);
            }
        } else {
            emailField.setInvalid(true);
            emailField.focus();
        }
    }

    private void handleSkip() {
        onSkip.run();
        close();
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }
}
