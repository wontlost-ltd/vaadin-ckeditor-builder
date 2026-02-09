// Vaadin 25 兼容的共享样式 - 使用 Lit (深度响应式美化版)
import { css, registerStyles } from '@vaadin/vaadin-themable-mixin/vaadin-themable-mixin.js';

// App layout 主题 - 现代简洁导航栏
registerStyles('vaadin-app-layout', css`
  :host {
    background: #f9fafb;
  }

  [part="content"] {
    height: 100%;
    background: #f9fafb;
  }

  [part="navbar"] {
    z-index: 200;
    box-shadow: 0 1px 0 #e5e7eb;
    align-items: center;
    padding: 0 var(--lumo-space-l);
    min-height: 56px;
    background: #ffffff;
  }

  /* 导航栏内的文字颜色 */
  [part="navbar"] ::slotted(h1) {
    color: #1f2937 !important;
    font-size: 16px !important;
    font-weight: 600 !important;
  }

  [part="navbar"] ::slotted(span) {
    color: #6b7280 !important;
  }
`);

// 全局样式
const globalStyles = document.createElement('style');
globalStyles.textContent = `
  /* ===== 进度加载动画 ===== */
  @keyframes v-progress-start {
    0% { width: 0%; }
    100% { width: 50%; }
  }

  @keyframes gradient-shift {
    0% { background-position: 0% 50%; }
    50% { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
  }

  .v-loading-indicator,
  .v-system-error,
  .v-reconnect-dialog {
    position: absolute;
    left: 0;
    top: 0;
    border: none;
    z-index: 10000;
    pointer-events: none;
  }

  .v-system-error,
  .v-reconnect-dialog {
    display: flex;
    right: 0;
    bottom: 0;
    background: rgba(30, 30, 46, 0.85);
    backdrop-filter: blur(8px);
    flex-direction: column;
    align-items: center;
    justify-content: center;
    align-content: center;
  }

  .v-system-error .caption,
  .v-system-error .message,
  .v-reconnect-dialog .text {
    width: 30em;
    max-width: 90%;
    padding: var(--lumo-space-xl);
    background: var(--lumo-base-color);
    border-radius: 16px;
    text-align: center;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  }

  .v-system-error .caption {
    padding-bottom: var(--lumo-space-s);
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
    font-weight: 600;
    color: var(--lumo-error-color);
  }

  .v-system-error .message {
    pointer-events: all;
    padding-top: var(--lumo-space-s);
    border-top-left-radius: 0;
    border-top-right-radius: 0;
    color: var(--lumo-secondary-text-color);
    line-height: 1.6;
  }

  .v-loading-indicator {
    position: fixed !important;
    width: 50%;
    opacity: 1;
    height: 3px;
    background: linear-gradient(90deg, #667eea, #764ba2, #f093fb, #667eea);
    background-size: 300% 100%;
    animation: v-progress-start 1000ms 200ms both, gradient-shift 2s ease infinite;
    border-radius: 0 2px 2px 0;
  }

  .v-loading-indicator[style*="none"] {
    display: block !important;
    width: 100% !important;
    opacity: 0;
    transition: opacity 500ms 300ms, width 300ms;
    animation: none;
  }

  /* ===== App Layout 内的标签页 ===== */
  vaadin-app-layout vaadin-tab {
    font-size: var(--lumo-font-size-s);
    padding-left: 1em;
    padding-right: 1em;
    color: rgba(255, 255, 255, 0.85);
    transition: all 0.2s ease;
  }

  vaadin-app-layout vaadin-tab:hover {
    color: white;
    background: rgba(255, 255, 255, 0.1);
  }

  vaadin-app-layout vaadin-tab a:hover {
    text-decoration: none;
  }

  vaadin-app-layout vaadin-tab:not([selected]) a {
    color: rgba(255, 255, 255, 0.75);
  }

  vaadin-app-layout vaadin-tab[selected] {
    color: white;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 8px;
  }

  vaadin-app-layout vaadin-tab vaadin-icon {
    margin: 0 4px;
    width: var(--lumo-icon-size-m);
    height: var(--lumo-icon-size-m);
    padding: .25rem;
    box-sizing: border-box !important;
  }

  vaadin-app-layout vaadin-tabs {
    max-width: 65%;
  }

  @media (min-width: 700px) {
    vaadin-app-layout vaadin-tab {
      font-size: var(--lumo-font-size-m);
      padding-left: 1.25em;
      padding-right: 1.25em;
    }
  }

  /* ===== 基础重置与工具类 ===== */
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }

  [hidden] {
    display: none !important;
  }

  /* ===== 标题样式 ===== */
  h1 {
    font-size: var(--lumo-font-size-xxxl);
    font-weight: 700;
    margin: 0;
    letter-spacing: -0.02em;
  }

  h2 {
    font-size: var(--lumo-font-size-xxl);
    font-weight: 600;
    margin-top: var(--lumo-space-l);
    margin-bottom: var(--lumo-space-m);
    color: var(--lumo-header-text-color);
  }

  h3 {
    font-size: var(--lumo-font-size-xl);
    font-weight: 600;
    margin-top: var(--lumo-space-m);
    margin-bottom: var(--lumo-space-s);
    color: var(--lumo-header-text-color);
  }

  h4 {
    font-size: var(--lumo-font-size-l);
    font-weight: 600;
    margin-top: var(--lumo-space-m);
    margin-bottom: var(--lumo-space-xs);
    color: var(--lumo-header-text-color);
  }

  /* ===== 工具类 ===== */
  .scrollable {
    padding: var(--lumo-space-m);
    overflow: auto;
    -webkit-overflow-scrolling: touch;
  }

  .flex {
    display: flex;
  }

  .flex-col {
    display: flex;
    flex-direction: column;
  }

  .flex1 {
    flex: 1 1 auto;
  }

  .flex-center {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .flex-between {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .gap-xs {
    gap: var(--lumo-space-xs);
  }

  .gap-s {
    gap: var(--lumo-space-s);
  }

  .gap-m {
    gap: var(--lumo-space-m);
  }

  .bold {
    font-weight: 600;
  }

  .text-secondary {
    color: var(--lumo-secondary-text-color);
  }

  .text-primary {
    color: var(--lumo-primary-color);
  }

  .text-success {
    color: var(--lumo-success-color);
  }

  .text-error {
    color: var(--lumo-error-color);
  }

  .text-center {
    text-align: center;
  }

  .text-sm {
    font-size: var(--lumo-font-size-s);
  }

  .text-xs {
    font-size: var(--lumo-font-size-xs);
  }

  /* ===== 响应式工具类 ===== */
  .show-on-mobile {
    display: none !important;
  }

  .hide-on-mobile {
    display: inline-flex;
  }

  .show-on-tablet {
    display: none !important;
  }

  .hide-on-tablet {
    display: inline-flex;
  }

  .stack-on-mobile {
    flex-direction: row;
  }

  .full-width-mobile {
    width: auto;
  }

  @media (max-width: 600px) {
    .show-on-mobile {
      display: inline-flex !important;
    }

    .hide-on-mobile {
      display: none !important;
    }

    .stack-on-mobile {
      flex-direction: column !important;
    }

    .full-width-mobile {
      width: 100% !important;
    }
  }

  @media (min-width: 601px) and (max-width: 991px) {
    .show-on-tablet {
      display: inline-flex !important;
    }

    .hide-on-tablet {
      display: none !important;
    }
  }

  /* ===== 渐变文本 ===== */
  .gradient-text {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  /* ===== 卡片悬停效果 ===== */
  .hover-lift {
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }

  .hover-lift:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  }

  /* ===== 玻璃态效果 ===== */
  .glass {
    background: rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.3);
  }

  /* ===== 暗色模式适配 ===== */
  [theme~="dark"] .glass {
    background: rgba(30, 30, 46, 0.7);
    border: 1px solid rgba(255, 255, 255, 0.1);
  }

  [theme~="dark"] vaadin-app-layout [part="content"] {
    background: linear-gradient(180deg, #1a1b26 0%, #24283b 100%);
  }

  /* ===== 动画工具类 ===== */
  .animate-fade-in {
    animation: fadeInUp 0.4s ease forwards;
  }

  @keyframes fadeInUp {
    from {
      opacity: 0;
      transform: translateY(20px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .animate-scale-in {
    animation: scaleIn 0.3s ease forwards;
  }

  @keyframes scaleIn {
    from {
      opacity: 0;
      transform: scale(0.9);
    }
    to {
      opacity: 1;
      transform: scale(1);
    }
  }

  /* ===== 间距工具类 ===== */
  .m-0 { margin: 0; }
  .mt-xs { margin-top: var(--lumo-space-xs); }
  .mt-s { margin-top: var(--lumo-space-s); }
  .mt-m { margin-top: var(--lumo-space-m); }
  .mt-l { margin-top: var(--lumo-space-l); }
  .mb-xs { margin-bottom: var(--lumo-space-xs); }
  .mb-s { margin-bottom: var(--lumo-space-s); }
  .mb-m { margin-bottom: var(--lumo-space-m); }
  .mb-l { margin-bottom: var(--lumo-space-l); }

  .p-0 { padding: 0; }
  .p-xs { padding: var(--lumo-space-xs); }
  .p-s { padding: var(--lumo-space-s); }
  .p-m { padding: var(--lumo-space-m); }
  .p-l { padding: var(--lumo-space-l); }

  /* ===== 圆角工具类 ===== */
  .rounded-sm { border-radius: 6px; }
  .rounded-md { border-radius: 10px; }
  .rounded-lg { border-radius: 16px; }
  .rounded-xl { border-radius: 24px; }
  .rounded-full { border-radius: 9999px; }

  /* ===== 阴影工具类 ===== */
  .shadow-sm { box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.12); }
  .shadow-md { box-shadow: 0 4px 6px rgba(0, 0, 0, 0.07), 0 2px 4px rgba(0, 0, 0, 0.06); }
  .shadow-lg { box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1), 0 6px 10px rgba(0, 0, 0, 0.08); }
  .shadow-xl { box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15); }

  /* ===== 背景工具类 ===== */
  .bg-gradient-primary {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  .bg-gradient-success {
    background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  }

  .bg-gradient-danger {
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  }

  .bg-gradient-subtle {
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  }

  /* ===== 移动端导航栏样式 ===== */
  .app-header {
    width: 100%;
    position: relative;
  }

  .app-header .nav-links {
    display: flex;
    align-items: center;
    gap: var(--app-space-s, var(--lumo-space-s));
  }

  .app-header .nav-link {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--lumo-space-xs);
    padding: var(--lumo-space-xs) var(--lumo-space-s);
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.1);
    transition: all 0.2s ease;
    text-decoration: none;
    color: white;
  }

  .app-header .nav-link:hover {
    background: rgba(255, 255, 255, 0.2);
  }

  .app-header .nav-link-label {
    display: none;
    font-size: var(--lumo-font-size-xs);
    color: rgba(255, 255, 255, 0.9);
  }

  .app-header .nav-menu-button {
    display: none;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    color: white;
    background: rgba(255, 255, 255, 0.15);
    border: none;
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .app-header .nav-menu-button:hover {
    background: rgba(255, 255, 255, 0.25);
  }

  @media (max-width: 600px) {
    .app-header .app-title {
      font-size: 1.05rem !important;
    }

    .app-header .app-logo {
      width: 32px !important;
      height: 32px !important;
      border-radius: 10px !important;
    }

    .app-header .app-logo vaadin-icon {
      width: 18px !important;
      height: 18px !important;
    }

    .app-header .nav-links {
      position: absolute;
      right: var(--app-space-m, var(--lumo-space-m));
      top: calc(var(--app-navbar-height, 56px) + 6px);
      background: rgba(255, 255, 255, 0.98);
      border-radius: 14px;
      border: 1px solid rgba(0, 0, 0, 0.08);
      box-shadow: 0 18px 30px rgba(0, 0, 0, 0.15);
      padding: var(--app-space-s, var(--lumo-space-s));
      display: none;
      flex-direction: column;
      align-items: stretch;
      gap: var(--app-space-xs, var(--lumo-space-xs));
      z-index: 300;
    }

    .app-header.menu-open .nav-links {
      display: flex;
    }

    .app-header .nav-menu-button {
      display: inline-flex;
    }

    .app-header .nav-link {
      background: transparent;
      color: var(--lumo-body-text-color);
      justify-content: flex-start;
      padding: var(--lumo-space-s) var(--lumo-space-m);
    }

    .app-header .nav-link:hover {
      background: var(--lumo-primary-color-10pct);
    }

    .app-header .nav-link-label {
      display: inline-flex;
      color: var(--lumo-body-text-color);
      font-size: var(--lumo-font-size-s);
    }

    .app-header .nav-link-icon {
      color: var(--lumo-primary-color);
    }

    .app-header .app-version {
      display: none !important;
    }
  }

  /* ===== 暗色模式响应式适配 ===== */
  [theme~="dark"] .app-header .nav-links {
    background: rgba(24, 24, 34, 0.98);
    border-color: rgba(255, 255, 255, 0.08);
  }

  [theme~="dark"] .app-header .nav-link {
    color: rgba(255, 255, 255, 0.9);
  }

  [theme~="dark"] .app-header .nav-link-label {
    color: rgba(255, 255, 255, 0.9);
  }

  [theme~="dark"] .app-header .nav-link:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  /* ===== 触控设备优化 ===== */
  @media (pointer: coarse) {
    .app-header .nav-link {
      min-height: 44px;
      min-width: 44px;
    }

    .app-header .nav-menu-button {
      min-height: 44px;
      min-width: 44px;
    }
  }

  /* ===== 减少动画偏好 ===== */
  @media (prefers-reduced-motion: reduce) {
    .animate-fade-in,
    .animate-scale-in {
      animation: none !important;
    }
  }
`;

document.head.appendChild(globalStyles);

// ===== Dark Mode Dynamic Style Helper =====
// This function applies dark mode styles to elements with inline styles
// that cannot be overridden by CSS
window.applyDarkModeToElements = function() {
  const isDark = document.documentElement.getAttribute('theme') === 'dark';

  // Search bar container
  document.querySelectorAll('.plugin-search-bar').forEach(function(el) {
    el.style.setProperty('background', isDark ? '#1f2937' : 'white', 'important');
    el.style.setProperty('border-color', isDark ? '#374151' : '#e5e7eb', 'important');
  });

  // Filter buttons and lang tabs containers
  document.querySelectorAll('.plugin-filter-buttons, .export-lang-tabs').forEach(function(el) {
    el.style.setProperty('background', isDark ? '#374151' : '#f3f4f6', 'important');
  });

  // Buttons inside filter/lang containers
  document.querySelectorAll('.plugin-filter-button, .export-lang-tab').forEach(function(btn) {
    const isActive = btn.classList.contains('active');
    if (isActive) {
      btn.style.setProperty('background', isDark ? '#1f2937' : 'white', 'important');
      btn.style.setProperty('color', isDark ? '#60a5fa' : '#2563eb', 'important');
    } else {
      btn.style.setProperty('background', 'transparent', 'important');
      btn.style.setProperty('color', isDark ? '#9ca3af' : '#6b7280', 'important');
    }
  });

  // Text field shadow DOM
  document.querySelectorAll('.plugin-search-input, .plugin-search-bar vaadin-text-field').forEach(function(el) {
    if (el.shadowRoot) {
      const inputField = el.shadowRoot.querySelector('[part="input-field"]');
      if (inputField) {
        inputField.style.setProperty('background', isDark ? '#374151' : '#f9fafb', 'important');
        inputField.style.setProperty('border-color', isDark ? '#4b5563' : '#e5e7eb', 'important');
        inputField.style.setProperty('color', isDark ? '#f3f4f6' : '#1f2937', 'important');
      }
    }
  });
};

// Set up MutationObserver for theme changes
if (typeof window !== 'undefined') {
  // Observer for theme attribute changes
  const themeObserver = new MutationObserver(function() {
    if (window.applyDarkModeToElements) {
      window.applyDarkModeToElements();
    }
  });
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['theme'] });

  // Observer for DOM changes (new elements added)
  const domObserver = new MutationObserver(function() {
    if (window.applyDarkModeToElements) {
      window.applyDarkModeToElements();
    }
  });
  domObserver.observe(document.body, { childList: true, subtree: true });

  // Apply on initial load
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      setTimeout(window.applyDarkModeToElements, 100);
    });
  } else {
    setTimeout(window.applyDarkModeToElements, 100);
  }
}
