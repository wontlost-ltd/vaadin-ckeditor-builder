package com.wontlost.ckeditor.component;

import com.wontlost.ckeditor.CKEditorConfig;
import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.VaadinCKEditor;
import org.jsoup.safety.Safelist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ckeditor-vaadin 5.3.x 系列新功能与修复的契约测试。
 *
 * <p>验证目标（对应上游 PR）：
 * <ul>
 *   <li>#115 setMediaEmbedResizable —— Config 序列化契约（mediaEmbed.resizable）</li>
 *   <li>#114 setCaretToStart/setCaretToEnd/focusEditor —— 新增 caret/focus API 可调用</li>
 *   <li>#110 setOverrideCssUrl —— CSS URL 白名单（合法 URL 不抛异常）</li>
 *   <li>5.3.3 sanitizeOnInput —— 输入时 HTML 清洗开关（见 UpgradeBaseline）</li>
 * </ul>
 *
 * <p>说明：caret/focus 与 CSS 加载的浏览器级行为通过 executeJs 委托前端，
 * 单测仅验证 Java API 契约（可调用、序列化结构正确）；真实浏览器行为由 E2E 覆盖。
 *
 * <p>类名保留 531 是历史原因（最初随 5.3.1 引入），断言均按 5.3.x 系列编写，
 * 补丁升级不会误报。
 */
@DisplayName("ckeditor-vaadin 5.3.x 功能契约测试")
class CKEditor531FeatureTest {

    /**
     * #115：可拖拽缩放的嵌入媒体。
     * 验证 setMediaEmbedResizable 正确写入 mediaEmbed 配置对象，
     * 且与 previewsInData 共存（共享同一 mediaEmbed 节点，与调用顺序无关）。
     */
    @Nested
    @DisplayName("#115 嵌入媒体可缩放（setMediaEmbedResizable）")
    class MediaEmbedResizable {

        @Test
        @DisplayName("setMediaEmbedResizable(true) 写入 mediaEmbed.resizable=true")
        void resizableSerializedIntoMediaEmbed() {
            CKEditorConfig config = new CKEditorConfig();
            config.setMediaEmbed(true);
            config.setMediaEmbedResizable(true);

            ObjectNode json = config.toJson();
            JsonNode mediaEmbed = json.get("mediaEmbed");

            assertNotNull(mediaEmbed, "toJson 应包含 mediaEmbed 节点");
            assertTrue(mediaEmbed.get("resizable").asBoolean(),
                "mediaEmbed.resizable 应为 true");
            assertTrue(config.isMediaEmbedResizable(),
                "getter isMediaEmbedResizable 应返回 true");
        }

        @Test
        @DisplayName("resizable 与 previewsInData 共存于同一 mediaEmbed 节点")
        void coexistsWithPreviewsInData() {
            CKEditorConfig config = new CKEditorConfig();
            config.setMediaEmbed(true);
            config.setMediaEmbedResizable(true);

            ObjectNode json = config.toJson();
            JsonNode mediaEmbed = json.get("mediaEmbed");

            // 共享同一配置对象，两个开关都应存在
            assertTrue(mediaEmbed.has("resizable"), "应含 resizable");
            assertTrue(mediaEmbed.has("previewsInData"), "应含 previewsInData");
            assertTrue(mediaEmbed.get("resizable").asBoolean());
            assertTrue(config.isMediaEmbedPreviewsInData());
        }

        @Test
        @DisplayName("resizable 与 previewsInData 共存与调用顺序无关")
        void coexistenceIndependentOfCallOrder() {
            // 反向顺序：先 resizable 再 mediaEmbed(previewsInData)，结果应一致
            CKEditorConfig config = new CKEditorConfig();
            config.setMediaEmbedResizable(true);
            config.setMediaEmbed(true);

            JsonNode mediaEmbed = config.toJson().get("mediaEmbed");
            assertTrue(mediaEmbed.get("resizable").asBoolean(),
                "无论调用顺序，resizable 都应保留");
            assertTrue(mediaEmbed.has("previewsInData"),
                "无论调用顺序，previewsInData 都应存在");
        }

        @Test
        @DisplayName("默认 isMediaEmbedResizable 为 false")
        void defaultsToFalse() {
            CKEditorConfig config = new CKEditorConfig();
            assertFalse(config.isMediaEmbedResizable(),
                "未调用 setter 时 resizable 默认应为 false");
        }
    }

    /**
     * #114：caret/focus 控制 API。
     * 这些方法通过 executeJs 委托前端，无浏览器时调用应安全（不抛异常）。
     */
    @Nested
    @DisplayName("#114 光标/焦点控制 API")
    class CaretFocusApi {

        private VaadinCKEditor newEditor() {
            return VaadinCKEditor.create()
                .withValue("<p>line one</p><p>line two</p>")
                .build();
        }

        @Test
        @DisplayName("setCaretToStart 可调用且不抛异常")
        void setCaretToStartCallable() {
            VaadinCKEditor editor = newEditor();
            assertDoesNotThrow(editor::setCaretToStart);
        }

        @Test
        @DisplayName("setCaretToEnd 可调用且不抛异常")
        void setCaretToEndCallable() {
            VaadinCKEditor editor = newEditor();
            assertDoesNotThrow(editor::setCaretToEnd);
        }

        @Test
        @DisplayName("focusEditor 可调用且不抛异常")
        void focusEditorCallable() {
            VaadinCKEditor editor = newEditor();
            assertDoesNotThrow(editor::focusEditor);
        }
    }

    /**
     * #110：overrideCssUrl 白名单校验（纵深防御）。
     * Java 侧入口 setOverrideCssUrl 对合法 URL 应正常接受；
     * 真正的方案过滤（javascript:/data: 拒绝）在前端 isAllowedCssUrl 完成，
     * 已由上游 css-url-validator.test.ts 覆盖，此处仅验证 Java 入口契约。
     */
    @Nested
    @DisplayName("#110 overrideCssUrl 入口契约")
    class OverrideCssUrl {

        @Test
        @DisplayName("合法 http/https/相对路径 URL 可设置")
        void legitimateUrlsAccepted() {
            VaadinCKEditor editor = VaadinCKEditor.create().build();
            assertDoesNotThrow(() -> editor.setOverrideCssUrl("https://cdn.example.com/theme.css"));
            assertDoesNotThrow(() -> editor.setOverrideCssUrl("/assets/custom.css"));
            assertDoesNotThrow(() -> editor.setOverrideCssUrl("./theme.css"));
        }
    }

    /**
     * 升级回归基线：确认 5.3.1 版本号 + 移除 LINE_HEIGHT 枚举生效。
     */
    @Nested
    @DisplayName("升级回归基线")
    class UpgradeBaseline {

        @Test
        @DisplayName("getVersion 报告 5.3.x 系列")
        void versionIs53x() {
            // 用前缀断言而非精确版本，避免补丁升级（5.3.2…）成为维护噪音
            assertTrue(VaadinCKEditor.getVersion().startsWith("5.3"),
                "VaadinCKEditor.getVersion 应为 5.3.x，实际：" + VaadinCKEditor.getVersion());
        }

        @Test
        @DisplayName("CKEditorPlugin 枚举不再包含 LINE_HEIGHT")
        void lineHeightEnumRemoved() {
            assertThrows(IllegalArgumentException.class,
                () -> CKEditorPlugin.valueOf("LINE_HEIGHT"),
                "5.3.1 应已移除 CKEditorPlugin.LINE_HEIGHT");
        }

        @Test
        @DisplayName("CKEditorPlugin 新增 media-embed 配套枚举")
        void mediaEmbedCompanionEnumsAdded() {
            assertDoesNotThrow(() -> CKEditorPlugin.valueOf("AUTO_MEDIA_EMBED"));
            assertDoesNotThrow(() -> CKEditorPlugin.valueOf("MEDIA_EMBED_STYLE"));
            assertDoesNotThrow(() -> CKEditorPlugin.valueOf("MEDIA_EMBED_TOOLBAR"));
            assertDoesNotThrow(() -> CKEditorPlugin.valueOf("CKFINDER"));
        }

        /**
         * 5.3.3 相对 5.3.1 的公共 API 增量（经 javap 逐类比对确认）：
         * setSanitizeOnInput / isSanitizeOnInput。纯新增、向后兼容，
         * 此处锁定其存在与默认值，防止后续升级静默移除。
         */
        @Test
        @DisplayName("5.3.3 新增 sanitizeOnInput 开关，默认关闭且可切换")
        void sanitizeOnInputApiAvailable() {
            VaadinCKEditor editor = VaadinCKEditor.create().build();

            assertFalse(editor.isSanitizeOnInput(),
                "sanitizeOnInput 默认应为 false，避免升级后静默改变既有输入行为");

            editor.setSanitizeOnInput(true);
            assertTrue(editor.isSanitizeOnInput(), "setSanitizeOnInput(true) 后应可读回 true");

            editor.setSanitizeOnInput(false);
            assertFalse(editor.isSanitizeOnInput(), "setSanitizeOnInput(false) 后应可读回 false");
        }

        /**
         * 基线：sanitizeHtml 走 jsoup 的 Jsoup.clean(html, Safelist)，
         * 验证内置 Safelist 的常规清洗行为（脚本被剥离、正文保留）。
         *
         * <p>注意：内置 Safelist <b>不受</b> CVE-2026-71497 影响，
         * 故本用例是通用清洗基线，不是该 CVE 的回归测试（见下一个用例）。
         */
        @Test
        @DisplayName("sanitizeHtml 剥离脚本标签并保留正文（内置 Safelist 基线）")
        void sanitizeHtmlStripsScript() {
            VaadinCKEditor editor = VaadinCKEditor.create().build();

            String cleaned = editor.sanitizeHtml(
                "<p>hello</p><script>alert(1)</script>", Safelist.basic());

            assertNotNull(cleaned, "清洗结果不应为 null");
            assertFalse(cleaned.toLowerCase().contains("<script"), "script 标签应被剥离：" + cleaned);
            assertTrue(cleaned.contains("hello"), "正文内容应保留：" + cleaned);
        }

        /**
         * CVE-2026-71497 回归测试（jsoup &gt;=1.14.3 &lt;1.23.1 受影响，1.23.1 修复）。
         *
         * <p>触发条件必须同时满足（缺一不可，这也是内置 Safelist 不受影响的原因）：
         * <ol>
         *   <li><b>自定义</b> Safelist 放行 raw-text 元素（style / title / iframe）；</li>
         *   <li>标签名以控制字符结尾（此处用 U+001E），旧版本会把
         *       {@code "style" + U+001E} 归一化成 {@code style}，从而套用 raw-text 解析行为。</li>
         * </ol>
         *
         * <p>修复后 jsoup 保留标签名中的控制字符，使其成为未知标签，
         * 在比对 safelist 时匹配不到，整个标签连同 payload 被剥离。
         *
         * <p>断言 payload 不以活动标记形式出现，即验证当前 jsoup（1.23.1）已修复。
         */
        @Test
        @DisplayName("CVE-2026-71497：自定义 Safelist 放行 raw-text 时畸形标签不得逃逸")
        void customSafelistRawTextTagIsNotSmuggled() {
            VaadinCKEditor editor = VaadinCKEditor.create().build();

            // 自定义 Safelist 放行 raw-text 元素 style —— 这是漏洞的必要前提
            Safelist rawTextAllowed = Safelist.basic().addTags("style");

            // 标签名以控制字符 U+001E 结尾。使用 \u001E 转义而非字面控制字符，
            // 避免编辑器/编码转换静默吃掉它，导致测试退化为无效断言。
            String rawText = "style\u001E";
            String malicious = "<" + rawText + "><img src=x onerror=alert(1)></" + rawText + ">";

            String cleaned = editor.sanitizeHtml(malicious, rawTextAllowed);
            assertNotNull(cleaned, "清洗结果不应为 null");

            String lower = cleaned.toLowerCase();
            assertFalse(lower.contains("onerror"),
                "onerror 事件处理器不得逃逸清洗（jsoup <1.23.1 的 CVE-2026-71497 行为）：" + cleaned);
            assertFalse(lower.contains("<img"),
                "img 标签不得以活动标记形式出现：" + cleaned);
        }
    }
}
