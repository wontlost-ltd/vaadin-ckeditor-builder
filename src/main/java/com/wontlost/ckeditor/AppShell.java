package com.wontlost.ckeditor;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;

/**
 * AppShell 配置 - PWA 和视口设置
 */
@Theme("ckeditor-builder")
@PWA(name = "Vaadin CKEditor", shortName = "CKEditor", display = "fullscreen")
@Viewport("width=device-width, minimum-scale=1, initial-scale=1, user-scalable=yes, viewport-fit=cover")
@Push
public class AppShell implements AppShellConfigurator {
}
