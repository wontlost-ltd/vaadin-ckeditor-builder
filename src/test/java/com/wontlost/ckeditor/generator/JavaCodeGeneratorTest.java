package com.wontlost.ckeditor.generator;

import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorPreset;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.CodeHighlightTheme;
import com.wontlost.ckeditor.VaadinCKEditor;
import com.wontlost.ckeditor.domain.BuilderState;
import com.wontlost.ckeditor.domain.BuilderState.CodeBlockLanguageConfig;
import com.wontlost.ckeditor.domain.BuilderState.CustomPluginConfig;
import com.wontlost.ckeditor.domain.BuilderState.FullscreenPageMode;
import com.wontlost.ckeditor.domain.BuilderState.MentionFeedConfig;
import com.wontlost.ckeditor.domain.BuilderState.ToolbarStyleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ========== addon 6.2：导出代码可编译，且使用 6.2 的新 API ==========

    @Test
    @DisplayName("全量配置导出的 Java 代码可对 ckeditor-vaadin 6.2 编译并运行（构建出编辑器）")
    void fullyConfiguredExportCompilesAndRuns() throws Exception {
        String code = generator.generate(fullyConfiguredState());
        Path classes = assertCompiles(code);

        // 运行导出代码：addon 在构建期校验 FullscreenPage / HtmlSupport / 插件依赖，非法值会在这里抛出
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> snippet = loader.loadClass("GeneratedSnippet");
            Object layout = snippet.getDeclaredConstructor().newInstance();
            Method build = snippet.getDeclaredMethod("build");
            build.setAccessible(true);
            build.invoke(layout);
            VaadinCKEditor editor = (VaadinCKEditor) ((com.vaadin.flow.component.orderedlayout.VerticalLayout) layout)
                .getComponentAt(0);
            assertEquals(com.wontlost.ckeditor.FullscreenPage.custom("min(1400px, 90%)"), editor.getFullscreenPage());
            assertTrue(editor.isCodeBlockHighlighting());
            assertTrue(editor.isWordCountVisible());
        }
    }

    @ParameterizedTest
    @EnumSource(CKEditorPreset.class)
    @DisplayName("每个预设的默认导出都可编译")
    void everyPresetExportCompiles(CKEditorPreset preset) throws IOException {
        BuilderState state = new BuilderState();
        state.setPreset(preset);
        assertCompiles(generator.generate(state));
    }

    @Test
    @DisplayName("导出 6.2 新功能：全屏页面、字数上限、代码高亮、HtmlSupport、点号 set")
    void exportsAddon62Features() {
        String code = generator.generate(fullyConfiguredState());

        assertTrue(code.contains(".withFullscreenPage(FullscreenPage.custom(\"min(1400px, 90%)\"))"), code);
        assertTrue(code.contains(".withWordCountVisible(true)"), code);
        assertTrue(code.contains(".withMaxWords(500)"), code);
        assertTrue(code.contains(".withMaxCharacters(3000)"), code);
        assertTrue(code.contains(".withCodeBlockHighlighting(CodeHighlightTheme.GITHUB_DARK)"), code);
        assertTrue(code.contains("HtmlSupport.Rule.elements(\"div\", \"section\")"), code);
        assertTrue(code.contains(".set(\"ckbox.tokenUrl\""), code);
        assertTrue(code.contains(".addPlugin(CKEditorPlugin.CKBOX)"), code);
        assertTrue(code.contains(".set(\"ai.assistant.adapter.openAI.apiUrl\""), code);
        assertTrue(code.contains("CustomPlugin.fromPremium(\"OpenAITextAdapter\")"), code);
        assertTrue(code.contains("\"previousPage\", \"nextPage\", \"pageNavigation\""), code);
        assertTrue(code.contains("editor.addWordCountChangeListener"), code);
        assertFalse(code.contains("fromPremium(\"CKBox\")"), code);
        assertFalse(code.contains("setGhsEnabled"), "addon 中不存在 setGhsEnabled");
        assertFalse(code.contains("withCustomConfig"), "addon 中不存在 withCustomConfig");
    }

    @Test
    @DisplayName("GHS 未列元素时导出 HtmlSupport.allowAll()")
    void ghsWithoutElementsExportsAllowAll() {
        BuilderState state = new BuilderState();
        state.setGhsEnabled(true);
        assertTrue(generator.generate(state).contains(".setHtmlSupport(HtmlSupport.allowAll())"));
    }

    @Test
    @DisplayName("默认全屏页面（A4_FIT）与未选 Fullscreen 插件时不输出 withFullscreenPage")
    void defaultFullscreenPageIsNotExported() {
        BuilderState state = new BuilderState();
        state.addPlugin(CKEditorPlugin.FULLSCREEN);
        assertFalse(generator.generate(state).contains("withFullscreenPage"));

        state.removePlugin(CKEditorPlugin.FULLSCREEN);
        state.setFullscreenPageMode(FullscreenPageMode.A4);
        assertFalse(generator.generate(state).contains("withFullscreenPage"), "没有 Fullscreen 插件时设置无效果");
    }

    @Test
    @DisplayName("BALLOON / INLINE 导出不含 fullscreen 工具栏项与全屏页面设置")
    void balloonExportDropsFullscreen() {
        for (CKEditorType type : List.of(CKEditorType.BALLOON, CKEditorType.INLINE)) {
            BuilderState state = new BuilderState();
            state.addPlugin(CKEditorPlugin.FULLSCREEN);
            assertTrue(state.getToolbarItems().contains("fullscreen"));
            state.setFullscreenPageMode(FullscreenPageMode.FULL_WIDTH);
            state.setEditorType(type);

            String code = generator.generate(state);
            assertFalse(code.contains("\"fullscreen\""), type + ": " + code);
            assertFalse(code.contains("withFullscreenPage"), type + ": " + code);
        }
    }

    @Test
    @DisplayName("代码高亮只在选了 CodeBlock 插件时导出")
    void codeHighlightingNeedsCodeBlock() {
        BuilderState state = new BuilderState();
        state.removePlugin(CKEditorPlugin.CODE_BLOCK);
        state.setCodeBlockHighlighting(true);
        assertFalse(generator.generate(state).contains("withCodeBlockHighlighting"));

        state.addPlugin(CKEditorPlugin.CODE_BLOCK);
        assertTrue(generator.generate(state).contains(".withCodeBlockHighlighting(CodeHighlightTheme.AUTO)"));
    }

    private static BuilderState fullyConfiguredState() {
        BuilderState state = new BuilderState();
        state.setPreset(CKEditorPreset.FULL);
        state.setMode(BuilderState.Mode.CUSTOM);
        state.setEditorType(CKEditorType.CLASSIC);
        state.addPlugin(CKEditorPlugin.FULLSCREEN);
        state.addPlugin(CKEditorPlugin.CODE_BLOCK);
        state.addPlugin(CKEditorPlugin.CKBOX);
        state.addPlugin(CKEditorPlugin.FULL_PAGE);
        state.removePlugin(CKEditorPlugin.BLOCK_QUOTE);
        state.setLicenseKey("GPL");
        state.setPlaceholder("Type \"here\"\n");
        state.setFontSizes(List.of("tiny", "default", "big"));
        state.setAllowAnyFontSize(true);
        state.setFontFamilies(List.of("Arial", "Georgia"));
        state.setAlignmentOptions(List.of("left", "right"));
        state.setLinkDefaultProtocol("http://");
        state.setAddTargetToExternalLinks(false);
        state.setSimpleUploadUrl("/api/upload");
        state.setSimpleUploadHeaders(Map.of("X-CSRF-TOKEN", "token"));
        state.setSimpleUploadWithCredentials(true);
        state.setAllowPrivateNetworks(true);
        state.setUploadAllowedMimeTypes(List.of("image/png"));
        state.setImageToolbar(List.of("imageTextAlternative", "toggleImageCaption"));
        state.setImageStyles(List.of("inline", "block"));
        state.setTableContentToolbar(List.of("tableColumn", "tableRow"));
        state.setCodeBlockLanguages(List.of(new CodeBlockLanguageConfig("java", "Java"),
            new CodeBlockLanguageConfig("javascript", "JavaScript")));
        state.setMediaEmbedPreviewsInData(true);
        state.setMentionFeeds(List.of(new MentionFeedConfig("@", List.of("@alice", "@bob"))));
        ToolbarStyleConfig style = new ToolbarStyleConfig();
        style.setBackground("#fff");
        style.setIconColor("#333");
        state.setToolbarStyle(style);
        state.setGhsEnabled(true);
        state.setHtmlSupportAllowedElements(List.of("div", "section"));
        state.setDependencyMode(BuilderState.DependencyMode.AUTO_RESOLVE_WITH_RECOMMENDED);
        state.addPremiumPlugin(withToolbar(CustomPluginConfig.fromPremium("Pagination"),
            "previousPage", "nextPage", "pageNavigation"));
        state.addPremiumPlugin(withToolbar(CustomPluginConfig.fromPremium("AIAssistant"), "aiAssistant"));
        state.addPremiumPlugin(CustomPluginConfig.fromPremium("SlashCommand"));
        CustomPluginConfig custom = new CustomPluginConfig("LineHeight", "custom-line-height", false);
        custom.setToolbarItems(List.of("lineHeight"));
        state.addCustomPlugin(custom);
        state.setFullscreenPageMode(FullscreenPageMode.CUSTOM);
        state.setFullscreenCustomWidth("min(1400px, 90%)");
        state.setWordCountVisible(true);
        state.setMaxWords(500);
        state.setMaxCharacters(3000);
        state.setCodeBlockHighlighting(true);
        state.setCodeHighlightTheme(CodeHighlightTheme.GITHUB_DARK);
        state.setAutosaveEnabled(true);
        state.setAutosaveInterval(3000);
        state.setReadOnly(true);
        state.setHideToolbar(true);
        state.setMinimapEnabled(true);
        state.setInitialValue("<p>Hello \"world\"</p>");
        state.setCustomCss(".ck-content { color: red; }");
        return state;
    }

    private static CustomPluginConfig withToolbar(CustomPluginConfig config, String... items) {
        config.setToolbarItems(List.of(items));
        return config;
    }

    /**
     * 把导出的代码片段包进一个 Vaadin 布局类（import 行放到文件头，其余作为方法体），
     * 用测试 classpath（含 ckeditor-vaadin 6.2）真实编译，失败时给出诊断与源码。
     */
    private static Path assertCompiles(String snippet) throws IOException {
        List<String> imports = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (String line : snippet.split("\n", -1)) {
            if (line.startsWith("import ")) {
                imports.add(line);
            } else {
                body.append("        ").append(line).append("\n");
            }
        }
        String source = String.join("\n", imports) + "\n\n"
            + "public class GeneratedSnippet extends com.vaadin.flow.component.orderedlayout.VerticalLayout {\n"
            + "    void build() {\n" + body + "    }\n}\n";

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK（javax.tools）");
        Path out = Files.createTempDirectory("generated-snippet");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///GeneratedSnippet.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<String> options = List.of("-classpath", System.getProperty("java.class.path"),
            "-d", out.toString(), "-proc:none");
        boolean ok = compiler.getTask(null, null, diagnostics, options, null, List.of(file)).call();
        String errors = diagnostics.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(null))
            .collect(Collectors.joining("\n"));
        assertTrue(ok, "导出的 Java 代码无法编译：\n" + errors + "\n---\n" + source);
        return out;
    }
}
