/**
 * 自定义插件预加载入口
 * 将自定义插件注册到全局 window 对象，供 VaadinCKEditor 动态加载
 */
import LineHeight from './plugins/line-height/index';
import CommentHighlightTracker from './plugins/comment-highlight-tracker/index';

// 使用与 plugin-resolver.ts 相同的 Symbol 键注册到全局
const CKEDITOR_PLUGIN_REGISTRY_KEY = Symbol.for('vaadin-ckeditor-custom-plugins');

interface GlobalPluginRegistry {
    [pluginName: string]: unknown;
}

interface WindowWithPluginRegistry extends Window {
    [CKEDITOR_PLUGIN_REGISTRY_KEY]?: GlobalPluginRegistry;
}

const win = window as WindowWithPluginRegistry;
if (!win[CKEDITOR_PLUGIN_REGISTRY_KEY]) {
    win[CKEDITOR_PLUGIN_REGISTRY_KEY] = {};
}

// 注册 LineHeight 插件
win[CKEDITOR_PLUGIN_REGISTRY_KEY]['LineHeight'] = LineHeight;

// 注册 CommentHighlightTracker 插件
win[CKEDITOR_PLUGIN_REGISTRY_KEY]['CommentHighlightTracker'] = CommentHighlightTracker;

// 同时保持旧的注册方式以兼容其他可能的使用场景
(window as any).__CKEDITOR_CUSTOM_PLUGINS__ = {
    ...(window as any).__CKEDITOR_CUSTOM_PLUGINS__,
    LineHeight,
    CommentHighlightTracker,
};

console.log('[CustomPlugins] LineHeight, CommentHighlightTracker plugins registered to global registry');

export { LineHeight, CommentHighlightTracker };
