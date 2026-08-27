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
// 图标母版位于 META-INF/resources/icons/icon.png（@PWA 默认路径），
// 由 scripts/gen-favicon.mjs 从 icons/icon.svg 生成，其余尺寸由 Vaadin 自动派生。
@PWA(name = "Vaadin CKEditor", shortName = "CKEditor", display = "fullscreen",
        themeColor = "#2563eb", backgroundColor = "#2563eb")
@Viewport("width=device-width, minimum-scale=1, initial-scale=1, user-scalable=yes, viewport-fit=cover")
@Push
public class AppShell implements AppShellConfigurator {
}
