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
//   现象：部分用户访问站点看到「You are offline / Vaadin CKEditor requires an
//   internet connection to work」整屏，而服务端完全正常（实测首页 200、UIDL POST 200）。
//   同一时刻手机与 Safari 正常——差异不在浏览器种类，而在**该浏览器有没有装 SW**
//   （Safari 约 7 天无访问即回收 SW 与缓存）。
//
//   实测到的事实（勿凭直觉改写，均已逐条核对）：
//     · @PWA 默认 offline=true，构建出 sw.js；线上实测已注册 1 个 SW，
//       workbox 预缓存 36 条，其中含 offline-stub.html?__WB_REVISION__=55903736。
//     · offline-stub.html 由 PwaHandler **无条件**注册为服务端路由并进预缓存清单，
//       它的存在与 offline 标志无关；实测其正文逐字包含上面那句文案。
//     · sw.js 的导航回落逻辑（见 flow-server 的 sw.ts）是：
//           if (!navigator.onLine) { 返回 matchPrecache(OFFLINE_PATH) }  // 先短路，不试网络
//           try { 走网络 } catch { 同样回落 }
//       本项目未设 offlinePath，故 OFFLINE_PATH 编译为 "."，即**缓存的应用外壳首页**。
//       ★注意：sw.js 里 "offline-stub" 出现 0 次——回落目标不是那个 stub。
//
//   因此可确证的因果只有一条：装了 SW 之后，导航会被 SW 接管并可能改为吐缓存内容，
//   使瞬时网络抖动（切网 / 休眠唤醒 / VPN）被放大成「刷新也回不来」的持久故障；
//   而未装 SW 的设备不受影响。至于那块 stub 具体经由哪条路径显示给用户
//   （PWA 独立窗口 start_url、抑或客户端自身的连接丢失提示），未逐条证实，
//   故此处不做断言——但它随 SW 一并消失。
//
//   为什么是关掉而不是改文案/改策略：本应用是**服务端驱动的 Vaadin 向导**
//   （@Push + UIDL 往返），离线状态下根本无法工作；缓存一个「看起来能打开」的外壳
//   没有任何正向价值，只会把瞬时抖动放大成持久故障。
//
//   ★关键副作用（正是我们要的）：PWA#offline() 的 javadoc 明确——
//   「The active service worker, if one is running in the browser, will be
//   unregistered on the user's next visit.」故绝大多数已卡住的用户在下次成功
//   加载页面时自动恢复；极少数仍被旧 SW 拦住的，硬刷新（Shift+F5）即可。
@PWA(name = "Vaadin CKEditor", shortName = "CKEditor", display = "fullscreen",
        themeColor = "#2563eb", backgroundColor = "#2563eb", offline = false)
@Viewport("width=device-width, minimum-scale=1, initial-scale=1, user-scalable=yes, viewport-fit=cover")
@Push
public class AppShell implements AppShellConfigurator {
}
