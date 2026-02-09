/**
 * Line Height 插件
 * 自定义 CKEditor 5 插件，用于设置行高
 */
import { Plugin } from 'ckeditor5';
import LineHeightEditing from './lineheightediting';
import LineHeightUI from './lineheightui';

export default class LineHeight extends Plugin {
    static get requires() {
        return [LineHeightEditing, LineHeightUI] as const;
    }

    static get pluginName() {
        return 'LineHeight' as const;
    }
}

export { LineHeightEditing, LineHeightUI };
