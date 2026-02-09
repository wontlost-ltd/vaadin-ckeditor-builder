/**
 * Line Height 命令
 */
import { Command, type Editor } from 'ckeditor5';
import { first } from 'ckeditor5';

const LINE_HEIGHT = 'lineHeight';

export default class LineHeightCommand extends Command {
    declare value: string;

    constructor(editor: Editor) {
        super(editor);
    }

    override refresh(): void {
        const firstBlock = first(this.editor.model.document.selection.getSelectedBlocks());

        this.isEnabled = !!firstBlock && this._canSetLineHeight(firstBlock);

        this.value = (this.isEnabled && firstBlock?.hasAttribute(LINE_HEIGHT))
            ? firstBlock.getAttribute(LINE_HEIGHT) as string
            : '1';
    }

    override execute(options: { value?: string } = {}): void {
        const editor = this.editor;
        const model = editor.model;
        const doc = model.document;

        const value = options.value;

        model.change(writer => {
            const blocks = Array.from(doc.selection.getSelectedBlocks())
                .filter(block => this._canSetLineHeight(block));

            if (blocks.length === 0) return;

            const currentLineHeight = blocks[0].getAttribute(LINE_HEIGHT) as string | undefined;

            const removeLineHeight = currentLineHeight === value || typeof value === 'undefined';

            if (removeLineHeight) {
                this._removeLineHeightFromSelection(blocks, writer);
            } else {
                this._setLineHeightOnSelection(blocks, writer, value!);
            }
        });
    }

    private _canSetLineHeight(block: any): boolean {
        return this.editor.model.schema.checkAttribute(block, LINE_HEIGHT);
    }

    private _removeLineHeightFromSelection(blocks: any[], writer: any): void {
        for (const block of blocks) {
            writer.removeAttribute(LINE_HEIGHT, block);
        }
    }

    private _setLineHeightOnSelection(blocks: any[], writer: any, lineHeight: string): void {
        for (const block of blocks) {
            writer.setAttribute(LINE_HEIGHT, lineHeight, block);
        }
    }
}
