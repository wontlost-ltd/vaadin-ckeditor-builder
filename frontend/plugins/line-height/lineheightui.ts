/**
 * Line Height UI 组件
 */
import { Plugin } from 'ckeditor5';
import {
    ViewModel,
    Collection,
    createDropdown,
    addListToDropdown
} from 'ckeditor5';
import { isSupported, normalizeOptions, type LineHeightOption } from './utils';
import type LineHeightCommand from './lineheightcommand';

// Line height icon SVG
const lineHeightIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
    <path fill="currentColor" d="M10,13H22V11H10M10,19H22V17H10M10,7H22V5H10M6,7H8.5L5,3.5L1.5,7H4V17H1.5L5,20.5L8.5,17H6V7Z"/>
</svg>`;

export default class LineHeightUI extends Plugin {
    static get pluginName() {
        return 'LineHeightUI' as const;
    }

    init(): void {
        const editor = this.editor;
        const t = editor.t;

        const options = this._getLocalizedOptions();
        const command = editor.commands.get('lineHeight') as LineHeightCommand;

        // Register UI component
        editor.ui.componentFactory.add('lineHeight', locale => {
            const dropdownView = createDropdown(locale);
            addListToDropdown(dropdownView, _prepareListOptions(options, command) as any);

            // Create dropdown model
            dropdownView.buttonView.set({
                label: t('Line Height'),
                icon: (editor.config.get('lineHeight.icon') as string) || lineHeightIcon,
                tooltip: true
            });

            dropdownView.extendTemplate({
                attributes: {
                    class: ['ck-line-height-dropdown']
                }
            });

            dropdownView.bind('isEnabled').to(command);

            // Execute command when an item from the dropdown is selected
            this.listenTo(dropdownView, 'execute', (evt: any) => {
                editor.execute(evt.source.commandName, { value: evt.source.commandParam });
                editor.editing.view.focus();
            });

            return dropdownView;
        });
    }

    private _getLocalizedOptions(): LineHeightOption[] {
        const editor = this.editor;
        const t = editor.t;

        const localizedTitles: Record<string, string> = {
            Default: t('Default')
        };

        const configOptions = editor.config.get('lineHeight.options') as (string | number)[];
        const options = normalizeOptions(
            configOptions.filter(option => isSupported(option))
        );

        return options.map(option => {
            const title = localizedTitles[option.title];

            if (title && title !== option.title) {
                return { ...option, title };
            }

            return option;
        });
    }
}

function _prepareListOptions(options: LineHeightOption[], command: LineHeightCommand): Collection<any> {
    const itemDefinitions = new Collection<any>();

    for (const option of options) {
        const def = {
            type: 'button' as const,
            model: new ViewModel({
                commandName: 'lineHeight',
                commandParam: option.model,
                label: option.title,
                withText: true
            })
        };

        (def.model as any).bind('isOn').to(command, 'value', (value: string) => {
            const newValue = value ? parseFloat(value) : undefined;
            return newValue === option.model;
        });

        itemDefinitions.add(def);
    }

    return itemDefinitions;
}
