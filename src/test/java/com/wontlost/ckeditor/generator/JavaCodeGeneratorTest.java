package com.wontlost.ckeditor.generator;

import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorPreset;
import com.wontlost.ckeditor.domain.BuilderState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java 导出必须与实时预览使用同一套插件集：预设之外的选中插件要 addPlugin，
 * 从预设中去掉的要 removePlugin，否则导出代码的工具栏项（如 insertTableLayout）没有对应插件。
 */
class JavaCodeGeneratorTest {

    private final JavaCodeGenerator generator = new JavaCodeGenerator();

    @Test
    @DisplayName("预设之外选中的插件（如 TABLE_LAYOUT）导出为 addPlugin")
    void selectedPluginOutsidePresetIsExported() {
        BuilderState state = new BuilderState();
        state.setPreset(CKEditorPreset.STANDARD);
        state.addPlugin(CKEditorPlugin.TABLE_LAYOUT);

        String code = generator.generate(state);

        assertTrue(code.contains(".addPlugin(CKEditorPlugin.TABLE_LAYOUT)"), code);
    }

    @Test
    @DisplayName("从预设中取消的插件导出为 removePlugin")
    void deselectedPresetPluginIsRemoved() {
        BuilderState state = new BuilderState();
        state.setPreset(CKEditorPreset.STANDARD);
        assertTrue(state.hasPlugin(CKEditorPlugin.BLOCK_QUOTE), "STANDARD 预设应含 BlockQuote");
        state.removePlugin(CKEditorPlugin.BLOCK_QUOTE);

        String code = generator.generate(state);

        assertTrue(code.contains(".removePlugin(CKEditorPlugin.BLOCK_QUOTE)"), code);
        assertFalse(code.contains(".addPlugin(CKEditorPlugin.BLOCK_QUOTE)"));
    }

    @Test
    @DisplayName("选择与预设完全一致时不产生 addPlugin / removePlugin 噪音")
    void presetOnlySelectionEmitsNoPluginDiff() {
        BuilderState state = new BuilderState();
        state.setPreset(CKEditorPreset.STANDARD);

        String code = generator.generate(state);

        assertFalse(code.contains(".addPlugin("), code);
        assertFalse(code.contains(".removePlugin("), code);
    }
}
