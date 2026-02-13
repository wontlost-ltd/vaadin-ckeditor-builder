/**
 * CommentPermissionEnforcer 插件
 *
 * CKEditor Cloud Services 的 `comment:write` 权限只允许用户编辑/删除自己的评论，
 * 但前端 UI 默认为所有评论都显示 Edit/Remove 按钮。
 * 本插件通过 MutationObserver 监听侧栏 DOM 变化，
 * 对非当前用户的评论隐藏 Edit/Remove 菜单按钮（"Show more items"），
 * 使 UI 与权限模型保持一致。
 */
import { Plugin, type Editor } from 'ckeditor5';

export default class CommentPermissionEnforcer extends Plugin {
    static get pluginName() {
        return 'CommentPermissionEnforcer' as const;
    }

    private _observer: MutationObserver | null = null;
    private _enforcing = false;

    init(): void {
        const editor = this.editor;

        // 等待 editor ready 后启动观察
        editor.on('ready', () => {
            this._startObserving();
        });
    }

    override destroy(): void {
        this._observer?.disconnect();
        this._observer = null;
        super.destroy();
    }

    private _startObserving(): void {
        const editor = this.editor;

        // 获取当前用户 ID
        let currentUserId: string | undefined;
        try {
            const usersPlugin = editor.plugins.get('Users') as { me?: { id?: string } };
            currentUserId = usersPlugin.me?.id;
        } catch {
            return;
        }
        if (!currentUserId) return;

        // 获取侧栏容器
        const domRoot = editor.editing.view.getDomRoot();
        const container = domRoot?.closest('vaadin-ckeditor');
        if (!container) return;

        const sidebar = container.querySelector('.annotation-sidebar-container')
            || container.querySelector('#annotation-sidebar');
        if (!sidebar) return;

        // 获取 CommentsRepository 插件，用于查找评论作者
        let commentsRepo: any;
        try {
            commentsRepo = editor.plugins.get('CommentsRepository');
        } catch {
            return;
        }

        // 执行一次初始检查
        this._enforcePermissions(sidebar as HTMLElement, commentsRepo, currentUserId);

        // 监听侧栏 DOM 变化（评论渲染、菜单展开等）
        // 使用 _enforcing 标志避免 MutationObserver 与 style 修改之间的无限循环
        this._observer = new MutationObserver(() => {
            if (this._enforcing) return;
            this._enforcePermissions(sidebar as HTMLElement, commentsRepo, currentUserId!);
        });

        this._observer.observe(sidebar, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class', 'aria-expanded']
        });
    }

    /**
     * 遍历侧栏中的评论项，对非当前用户的评论隐藏"更多操作"按钮。
     *
     * DOM 结构（CKEditor 5 annotations sidebar）：
     * .ck-annotation（comment thread）
     *   .ck-annotation（单条 comment）
     *     .ck-comment__main / .ck-annotation__main
     *       .ck-annotation__info-name → 作者名
     *       .ck-annotation__actions → 包含"更多操作"按钮
     */
    private _enforcePermissions(
        sidebar: HTMLElement,
        commentsRepo: any,
        currentUserId: string
    ): void {
        this._enforcing = true;
        try {
            this._doEnforce(sidebar, commentsRepo, currentUserId);
        } finally {
            this._enforcing = false;
        }
    }

    private _doEnforce(
        sidebar: HTMLElement,
        commentsRepo: any,
        currentUserId: string
    ): void {
        // 查找所有评论注释项（.ck-comment 和 .ck-annotation 在同一元素上）
        const commentAnnotations = sidebar.querySelectorAll(
            '.ck-comment.ck-annotation'
        );

        for (const commentEl of commentAnnotations) {
            // 获取作者名元素
            const nameEl = commentEl.querySelector('.ck-annotation__info-name');
            if (!nameEl) continue;

            const authorName = nameEl.textContent?.trim();
            if (!authorName) continue;

            // 通过 CommentsRepository 查找此评论的作者 ID
            const isOwnComment = this._isCommentByCurrentUser(
                commentsRepo, authorName, currentUserId
            );

            // 查找"更多操作"下拉菜单容器（.ck-dropdown 在 .ck-annotation__actions 内）
            // CKEditor 按钮不使用 aria-label，而是通过文本内容标识
            const actionsContainer = commentEl.querySelector('.ck-annotation__actions');
            if (!actionsContainer) continue;

            const dropdown = actionsContainer.querySelector('.ck-dropdown') as HTMLElement | null;

            if (dropdown) {
                if (isOwnComment) {
                    dropdown.style.removeProperty('display');
                } else {
                    // 使用 !important 覆盖 CKEditor 的 CSS 规则
                    dropdown.style.setProperty('display', 'none', 'important');
                }
            }
        }
    }

    /**
     * 判断指定作者名的评论是否属于当前用户。
     * 通过遍历所有评论线程，匹配作者名与当前用户 ID。
     */
    private _isCommentByCurrentUser(
        commentsRepo: any,
        authorName: string,
        currentUserId: string
    ): boolean {
        try {
            for (const thread of commentsRepo.getCommentThreads()) {
                for (const comment of thread.comments) {
                    if (comment.author?.name === authorName) {
                        return comment.author?.id === currentUserId;
                    }
                }
            }
        } catch {
            // 查询失败时默认不隐藏
        }
        return false;
    }
}
