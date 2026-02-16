package com.wontlost.ckeditor.security;

import com.vaadin.flow.component.login.LoginForm;
import com.wontlost.ckeditor.config.TurnstileProperties;

/**
 * 向 Vaadin LoginForm 注入 Cloudflare Turnstile（Invisible 模式）。
 *
 * Vaadin 25 的 LoginForm 将 form 元素渲染为 light DOM 子元素（非 Shadow DOM），
 * 通过 executeJs 在 form 元素中：
 * 1. 添加 hidden input（cf-turnstile-response）
 * 2. 添加 Turnstile 容器并渲染 widget
 * 3. 拦截 form submit，确保 token 生成后再提交
 */
public final class TurnstileLoginFormSupport {

    /**
     * JavaScript 注入脚本：
     * - 加载 Turnstile API 脚本（幂等）
     * - 在 host（vaadin-login-form）的 light DOM 子级 form 中插入 hidden input 和容器
     * - 拦截 submit 事件，token 未就绪时阻止提交并触发 execute
     *
     * Vaadin 25 的 LoginForm 将 form 渲染为 light DOM 子元素，不在 Shadow DOM 内。
     */
    private static final String TURNSTILE_SCRIPT = """
        const host = this;
        const siteKey = $0;
        if (host.__turnstileInit) {
          return;
        }
        host.__turnstileInit = true;

        const ensureScript = () => {
          if (window.turnstile && window.turnstile.render) {
            return Promise.resolve();
          }
          if (window.__turnstileReady) {
            return window.__turnstileReady;
          }
          window.__turnstileReady = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
            script.async = true;
            script.defer = true;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error('Turnstile script load failed'));
            document.head.appendChild(script);
          });
          return window.__turnstileReady;
        };

        const setup = (attempts = 0) => {
          const form = host.querySelector('form');
          if (!form) {
            if (attempts < 20) {
              requestAnimationFrame(() => setup(attempts + 1));
            }
            return;
          }

          let input = form.querySelector('input[name="cf-turnstile-response"]');
          if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'cf-turnstile-response';
            input.autocomplete = 'off';
            form.appendChild(input);
          }

          let container = form.querySelector('.turnstile-container');
          if (!container) {
            container = document.createElement('div');
            container.className = 'turnstile-container';
            container.style.display = 'flex';
            container.style.justifyContent = 'center';
            container.style.marginTop = '8px';
            form.appendChild(container);
          }

          if (container.dataset.widgetId) {
            return;
          }

          let pendingSubmit = false;
          let turnstileErrored = false;
          const submitForm = () => {
            if (typeof form.requestSubmit === 'function') {
              form.requestSubmit();
            } else {
              form.submit();
            }
          };

          const widgetId = window.turnstile.render(container, {
            sitekey: siteKey,
            appearance: 'interaction-only',
            callback: (token) => {
              input.value = token;
              turnstileErrored = false;
              if (pendingSubmit) {
                pendingSubmit = false;
                submitForm();
              }
            },
            'error-callback': () => {
              input.value = '';
              turnstileErrored = true;
              if (pendingSubmit) {
                pendingSubmit = false;
                submitForm();
              }
            },
            'expired-callback': () => {
              input.value = '';
            }
          });
          container.dataset.widgetId = String(widgetId);

          if (!form.__turnstileBound) {
            form.__turnstileBound = true;
            form.addEventListener('submit', (event) => {
              if (input.value || turnstileErrored) {
                return;
              }
              event.preventDefault();
              pendingSubmit = true;
              window.turnstile.execute(widgetId);
            }, { capture: true });
          }
        };

        ensureScript()
          .then(() => setup())
          .catch((err) => {
            console.warn('Turnstile initialization failed:', err);
          });
        """;

    private TurnstileLoginFormSupport() {
    }

    /**
     * 向 LoginForm 注入 Turnstile。当 Turnstile 未配置时为空操作。
     */
    public static void inject(LoginForm loginForm, TurnstileProperties properties) {
        if (loginForm == null || properties == null || !properties.isConfigured()) {
            return;
        }
        loginForm.getElement().executeJs(TURNSTILE_SCRIPT, properties.getSiteKey());
    }
}
