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
// ★offline = false：关闭 service worker 与离线缓存。
//
//   现象：用户访问站点看到 Vaadin 的「You are offline / Vaadin CKEditor requires an
//   internet connection to work」整屏，而服务端完全正常（实测首页连续 8 次 200、
//   UIDL POST 200）。
//
//   根因：@PWA 默认 offline=true，Vaadin 生成 offline-stub.html 并由 workbox
//   预缓存（实测该 stub 逐字包含上面那句文案）。生成的 sw.js 逻辑是：
//
//       if (!self.navigator.onLine) { 直接返回缓存的 offline 页 }   // 先短路，不试网络
//       try { 走网络 } catch { 也回落到 offline 页 }
//
//   于是只要浏览器有一瞬间把 navigator.onLine 报成 false（切网络/休眠唤醒/VPN 抖动），
//   或任一次导航请求失败，就会落到该页；而 service worker 已装好，**刷新仍是缓存**，
//   服务端健康也救不回来——用户只能手工清 SW 才能恢复。
//
//   为什么是关掉而不是改文案/改策略：本应用是**服务端驱动的 Vaadin 向导**
//   （@Push + UIDL 往返），离线状态下根本无法工作；缓存一个「看起来能打开」的外壳
//   没有任何正向价值，只会把瞬时抖动放大成持久故障。
//
//   ★关键副作用（正是我们要的）：offline=false 会让**已经装在用户浏览器里的
//   service worker 在下次访问时自动注销**，因此当前卡在离线页的用户无需手工清缓存。
@PWA(name = "Vaadin CKEditor", shortName = "CKEditor", display = "fullscreen",
        themeColor = "#2563eb", backgroundColor = "#2563eb", offline = false)
@Viewport("width=device-width, minimum-scale=1, initial-scale=1, user-scalable=yes, viewport-fit=cover")
@Push
public class AppShell implements AppShellConfigurator {
}
