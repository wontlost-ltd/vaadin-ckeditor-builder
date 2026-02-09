package com.wontlost.ckeditor.views.admin;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * 管理员登录视图
 */
@Route("login")
@PageTitle("管理员登录")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        add(new H1("CKEditor Builder 管理后台"), loginForm);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // 检查是否有登录错误
        if (event.getLocation()
            .getQueryParameters()
            .getParameters()
            .containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
