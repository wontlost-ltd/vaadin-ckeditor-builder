package com.wontlost.ckeditor.domain;

import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.VaadinCKEditorPremium.PremiumPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向导插件目录与 addon 6.0 / CKEditor 49.1 目录的一致性。
 * 目录项名字必须是 addon 真实导出的 PremiumPlugin，否则预览只会得到 "Premium plugin not found"。
 */
class PluginCatalogueTest {

    private static final List<String> LOCALES = List.of("en", "zh", "es", "fr", "ru", "ar");

    @Test
    @DisplayName("每个 premium 目录项都对应 addon 的 PremiumPlugin 常量，工具栏项与之一致")
    void premiumEntriesMatchAddonCatalogue() {
        Set<String> known = Arrays.stream(PremiumPlugin.values())
            .map(PremiumPlugin::getPluginName).collect(Collectors.toSet());
        for (PluginCatalogue.PremiumEntry entry : PluginCatalogue.PREMIUM) {
            assertTrue(known.contains(entry.name()), "addon 未导出 premium 插件：" + entry.name());
            if (!entry.toolbarItem().isEmpty()) {
                PremiumPlugin plugin = Arrays.stream(PremiumPlugin.values())
                    .filter(p -> p.getPluginName().equals(entry.name())).findFirst().orElseThrow();
                assertTrue(Arrays.asList(plugin.getToolbarItems()).contains(entry.toolbarItem()),
                    entry.name() + " 的工具栏项 " + entry.toolbarItem() + " 不在 addon 声明之内");
            }
        }
    }

    @Test
    @DisplayName("目录包含 CKEditor 49.1 新增的 premium 插件与 AI Chat")
    void catalogueIncludesCkeditor49Additions() {
        Set<String> names = PluginCatalogue.PREMIUM.stream()
            .map(PluginCatalogue.PremiumEntry::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(List.of(
            "AIChat", "AIChatBalloon", "AIEditorConfig", "ProposedChanges", "RealTimeContentFetcher")));
        assertFalse(names.contains("RealTimeCollaboration"), "包名不是插件类");
        assertFalse(names.contains("TableLayout"), "TableLayout 由免费 umbrella 导出，归 CKEditorPlugin");
    }

    @Test
    @DisplayName("需要 AI / 协作服务端配置或专用容器的 premium 插件在实时预览中跳过（精确集合）")
    void previewSkipsPluginsThatNeedServerSideConfig() {
        assertEquals(Set.of(
            "DocumentOutline", "TableOfContents", "Pagination",
            "AIAssistant", "AIChat", "AIChatBalloon", "AIEditorConfig", "ProposedChanges",
            "Comments", "TrackChanges", "RevisionHistory", "RealTimeContentFetcher"),
            PluginCatalogue.PREMIUM_PREVIEW_SKIP);
        // 目录里不在跳过集的项才会真正进入预览：逐项列出，避免目录调整时静默改变预览行为
        Set<String> previewed = PluginCatalogue.PREMIUM.stream().map(PluginCatalogue.PremiumEntry::name)
            .filter(n -> !PluginCatalogue.PREMIUM_PREVIEW_SKIP.contains(n)).collect(Collectors.toSet());
        assertEquals(Set.of("ExportPdf", "ExportWord", "ImportWord", "FormatPainter", "SlashCommand",
            "Template", "CaseChange", "MergeFields"), previewed);
    }

    @Test
    @DisplayName("license 门控以商业 key 为准：GPL 与空值都不算")
    void commercialLicenseSemantics() {
        BuilderState state = new BuilderState();
        state.setLicenseKey("GPL");
        assertFalse(state.hasCommercialLicenseKey(), "GPL 不是商业 license");
        state.setLicenseKey("  ");
        assertFalse(state.hasCommercialLicenseKey());
        state.setLicenseKey("eyJhbGciOiJFUzI1NiJ9.commercial");
        assertTrue(state.hasCommercialLicenseKey());
    }

    @Test
    @DisplayName("TableLayout 需商业 license：无 key 时不进实时预览，其余免费插件不受影响")
    void tableLayoutIsLicenseGatedInPreview() {
        assertFalse(PluginCatalogue.isPreviewable(CKEditorPlugin.TABLE_LAYOUT, false));
        assertTrue(PluginCatalogue.isPreviewable(CKEditorPlugin.TABLE_LAYOUT, true));
        assertTrue(PluginCatalogue.isPreviewable(CKEditorPlugin.TABLE, false));
        assertFalse(PluginCatalogue.isPreviewable(CKEditorPlugin.MINIMAP, true), "Minimap 预览时需 DOM 就绪，继续跳过");
    }

    @Test
    @DisplayName("目录用到的 i18n key 在全部 6 种语言中都存在")
    void i18nKeysExistInEveryLocale() throws IOException {
        List<String> keys = PluginCatalogue.PREMIUM.stream()
            .map(PluginCatalogue.PremiumEntry::descriptionKey).collect(Collectors.toList());
        keys.add("plugin.desc.tableLayout");
        for (String locale : LOCALES) {
            Properties props = load("i18n/messages_" + locale + ".properties");
            for (String key : keys) {
                assertTrue(props.containsKey(key), locale + " 缺少 " + key);
                assertFalse(props.getProperty(key).isBlank(), locale + " 的 " + key + " 为空");
            }
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties props = new Properties();
        try (InputStream in = PluginCatalogueTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "缺少资源 " + resource);
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }
}
