/**
 * Line Height 编辑功能
 */
import { Plugin, type Editor } from 'ckeditor5';
import { isSupported, buildDefinition } from './utils';
import LineHeightCommand from './lineheightcommand';

export default class LineHeightEditing extends Plugin {
    constructor(editor: Editor) {
        super(editor);

        editor.config.define('lineHeight', {
            options: [0.5, 1, 1.5, 2, 2.5, 3]
        });
    }

    static get pluginName() {
        return 'LineHeightEditing' as const;
    }

    init(): void {
        const editor = this.editor;
        const schema = editor.model.schema;

        // Filter out unsupported options
        const configOptions = editor.config.get('lineHeight.options') as (string | number)[];
        const enabledOptions = configOptions
            .map(option => String(option))
            .filter(isSupported);

        // Allow lineHeight attribute on all blocks
        schema.extend('$block', { allowAttributes: 'lineHeight' });
        editor.model.schema.setAttributeProperties('lineHeight', { isFormatting: true });

        const definition = buildDefinition(enabledOptions);

        editor.conversion.attributeToAttribute(definition);

        editor.commands.add('lineHeight', new LineHeightCommand(editor));
    }
}
