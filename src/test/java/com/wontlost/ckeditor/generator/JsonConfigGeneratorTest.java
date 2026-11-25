package com.wontlost.ckeditor.generator;

import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.CodeHighlightTheme;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.BuilderState.FullscreenPageMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON / TypeScript 导出携带 ckeditor-vaadin 6.2 的选项，且 JSON 保持合法。
 */
class JsonConfigGeneratorTest {

    private static BuilderState configuredState() {
        BuilderState state = new BuilderState();
        state.addPlugin(CKEditorPlugin.FULLSCREEN);
        state.addPlugin(CKEditorPlugin.CODE_BLOCK);
        state.setFullscreenPageMode(FullscreenPageMode.CUSTOM);
        state.setFullscreenCustomWidth("1200px");
        state.setWordCountVisible(true);
        state.setMaxWords(100);
        state.setCodeBlockHighlighting(true);
        state.setCodeHighlightTheme(CodeHighlightTheme.ATOM_ONE_DARK);
        state.setGhsEnabled(true);
        state.setHtmlSupportAllowedElements(List.of("div"));
        return state;
    }

    @Test
    @DisplayName("JSON 导出合法并包含全屏页面、字数、代码高亮与 htmlSupport")
    void jsonContainsAddon62Options() {
        JsonNode config = new ObjectMapper().readTree(new JsonConfigGenerator().generate(configuredState())).get("config");

        assertEquals("custom", config.get("fullscreenPage").get("mode").asText());
        assertEquals("1200px", config.get("fullscreenPage").get("width").asText());
        assertTrue(config.get("wordCount").get("visible").asBoolean());
        assertEquals(100, config.get("wordCount").get("maxWords").asInt());
        assertEquals("atom-one-dark", config.get("codeBlockHighlighting").asText());
        assertTrue(config.get("htmlSupport").has("allow"));
    }

    @Test
    @DisplayName("BALLOON 的 JSON / TypeScript 导出没有全屏页面与 fullscreen 工具栏项")
    void balloonHasNoFullscreen() {
        BuilderState state = configuredState();
        state.setEditorType(CKEditorType.BALLOON);

        String json = new JsonConfigGenerator().generate(state);
        JsonNode root = new ObjectMapper().readTree(json);
        assertTrue(root.get("config").get("fullscreenPage").isNull());
        assertFalse(json.contains("\"fullscreen\""), json);

        String ts = new TypeScriptCodeGenerator().generate(state);
        assertTrue(ts.contains("fullscreenPage: null"), ts);
        assertFalse(ts.contains("'fullscreen'"), ts);
    }
}
