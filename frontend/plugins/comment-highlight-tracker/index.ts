/**
 * CommentHighlightTracker 插件
 *
 * 监听编辑器光标位置变化，当光标处于 .ck-comment-marker 内时，
 * 在批注侧栏对应的 .ck-sidebar-item 上添加 .ck-sidebar-item--comment-selected 类，
 * 使其显示黄色左边框（与 hover 效果一致）。
 */
import { Plugin, type Editor } from 'ckeditor5';

const SELECTED_CLASS = 'ck-sidebar-item--comment-selected';

export default class CommentHighlightTracker extends Plugin {
    static get pluginName() {
        return 'CommentHighlightTracker' as const;
    }

    init(): void {
        const editor = this.editor;

        // 监听 model selection 变化
        editor.model.document.selection.on('change:range', () => {
            this._updateSidebarHighlight();
        });
    }

    /**
     * 检查 DOM 中光标是否在 .ck-comment-marker 内，
     * 若是则高亮对应侧栏项，否则清除所有高亮。
     */
    private _updateSidebarHighlight(): void {
        const editor = this.editor;

        // 从编辑器的 DOM 根元素向上查找 vaadin-ckeditor 容器
        const domRoot = editor.editing.view.getDomRoot();
        const container = domRoot?.closest('vaadin-ckeditor');
        if (!container) return;

        // 清除之前的所有高亮
        const prevSelected = container.querySelectorAll(`.${SELECTED_CLASS}`);
        prevSelected.forEach((el: Element) => el.classList.remove(SELECTED_CLASS));

        // 获取 DOM selection
        const domSelection = window.getSelection();
        if (!domSelection || domSelection.rangeCount === 0) return;

        const anchorNode = domSelection.anchorNode;
        if (!anchorNode) return;

        // 从光标位置向上查找 .ck-comment-marker
        const element = anchorNode.nodeType === Node.TEXT_NODE
            ? anchorNode.parentElement
            : anchorNode as Element;
        if (!element) return;

        const commentMarker = element.closest('.ck-comment-marker');
        if (!commentMarker) return;

        // 获取评论线程 ID（data-comment 属性）
        const threadId = commentMarker.getAttribute('data-comment');
        if (!threadId) return;

        // 在侧栏中查找对应的 sidebar item
        const sidebarItems = container.querySelectorAll(
            '.annotation-sidebar-container .ck-sidebar-item'
        );

        for (const item of sidebarItems) {
            // 查找 ck-thread 内的 data-thread-id 匹配
            const thread = item.querySelector(`.ck-thread[data-thread-id="${threadId}"]`);
            if (thread) {
                item.classList.add(SELECTED_CLASS);
                break;
            }
        }
    }
}
