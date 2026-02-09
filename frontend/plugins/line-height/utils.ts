/**
 * Line Height 插件工具函数
 */

export interface LineHeightOption {
    title: string;
    model: number | undefined;
    view?: {
        name: string;
        styles: {
            'line-height': string;
        };
        priority: number;
        classes?: string;
    };
}

export function isSupported(option: string | number): boolean {
    return /^\d(.\d+)?$/mg.test(String(option));
}

export function normalizeOptions(configuredOptions: (string | number | LineHeightOption)[]): LineHeightOption[] {
    return configuredOptions.map(optionDefinition).filter((option): option is LineHeightOption => !!option);
}

export function buildDefinition(options: string[]) {
    const definition: {
        model: {
            key: string;
            values: string[];
        };
        view: Record<string, { key: string; value: string }>;
    } = {
        model: {
            key: 'lineHeight',
            values: options.slice()
        },
        view: {}
    };

    for (const option of options) {
        definition.view[option] = {
            key: 'style',
            value: `line-height: ${option}`
        };
    }

    return definition;
}

function optionDefinition(option: string | number | LineHeightOption): LineHeightOption | undefined {
    if (typeof option === 'object') {
        return option as LineHeightOption;
    }

    if (option === 'default') {
        return {
            model: undefined,
            title: 'Default'
        };
    }

    const sizePreset = parseFloat(String(option));

    if (isNaN(sizePreset)) {
        return undefined;
    }

    return generatePixelPreset(sizePreset);
}

function generatePixelPreset(size: number): LineHeightOption {
    const sizeName = String(size);

    return {
        title: sizeName,
        model: size,
        view: {
            name: 'span',
            styles: {
                'line-height': sizeName
            },
            priority: 5
        }
    };
}
