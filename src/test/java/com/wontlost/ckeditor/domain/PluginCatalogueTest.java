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
 * 向导插件目录与 addon 6.2 / CKEditor 49.1 目录的一致性。
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
            PremiumPlugin plugin = Arrays.stream(PremiumPlugin.values())
                .filter(p -> p.getPluginName().equals(entry.name())).findFirst().orElseThrow();
            assertTrue(Arrays.asList(plugin.getToolbarItems()).containsAll(entry.toolbarItems()),
                entry.name() + " 的工具栏项 " + entry.toolbarItems() + " 不在 addon 声明之内");
        }
    }

    @Test
    @DisplayName("addon 6.2：Pagination 的工具栏项为 previousPage / nextPage / pageNavigation")
    void paginationToolbarItemsMatchAddon62() {
        PluginCatalogue.PremiumEntry pagination = PluginCatalogue.PREMIUM.stream()
            .filter(e -> e.name().equals("Pagination")).findFirst().orElseThrow();
        assertEquals(List.of("previousPage", "nextPage", "pageNavigation"), pagination.toolbarItems());
        assertEquals(Arrays.asList(PremiumPlugin.PAGINATION.getToolbarItems()), pagination.toolbarItems());
    }

    @Test
    @DisplayName("目录里不出现 addon 6.2 已移到 CKEditorPlugin 的 CKBox（fromPremium(\"CKBox\") 从未能加载）")
    void ckboxIsNotAPremiumPackagePlugin() {
        assertTrue(PluginCatalogue.PREMIUM.stream().noneMatch(e -> e.name().startsWith("CKBox")));
        assertTrue(CKEditorPlugin.CKBOX.isPremiumFeature());
    }

    @Test
    @DisplayName("premium feature 标记与 addon isPremiumFeature() 一致，CKBox / CKFinder 标为付费服务")
    void premiumBadgesFollowAddonCatalogue() {
        for (CKEditorPlugin plugin : CKEditorPlugin.values()) {
            String key = PluginCatalogue.premiumBadgeKey(plugin);
            assertEquals(plugin.isPremiumFeature(), key != null, plugin.name());
        }
        assertEquals("plugin.badge.paidService", PluginCatalogue.premiumBadgeKey(CKEditorPlugin.CKBOX));
        assertEquals("plugin.badge.paidService", PluginCatalogue.premiumBadgeKey(CKEditorPlugin.CKFINDER));
        assertEquals("plugin.badge.premiumFeature", PluginCatalogue.premiumBadgeKey(CKEditorPlugin.FIND_AND_REPLACE));
        assertNull(PluginCatalogue.premiumBadgeKey(CKEditorPlugin.BOLD));
    }

    @Test
    @DisplayName("CKBox 需要付费服务配置，不进实时预览")
    void ckboxIsNotPreviewed() {
        assertFalse(PluginCatalogue.isPreviewable(CKEditorPlugin.CKBOX, true));
        assertFalse(PluginCatalogue.isPreviewable(CKEditorPlugin.CKBOX_IMAGE_EDIT, true));
        assertTrue(PluginCatalogue.isPreviewable(CKEditorPlugin.FULL_PAGE, false));
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
        // addon 6.2 选项与标记
        keys.addAll(List.of(
            "plugin.badge.premiumFeature", "plugin.badge.premiumFeature.tooltip",
            "plugin.badge.paidService", "plugin.badge.paidService.tooltip",
            "plugin.desc.ckbox", "plugin.desc.ckboxImageEdit", "plugin.desc.fullscreen", "plugin.desc.wordCount",
            "plugin.desc.title", "plugin.desc.generalHtmlSupport", "plugin.desc.fullPage",
            "step6.codeHighlighting", "step6.codeHighlighting.helper", "step6.codeHighlighting.needsPlugin",
            "step6.codeHighlightTheme", "step6.wordCountVisible", "step6.maxWords", "step6.maxCharacters",
            "step6.limit.helper", "step6.ghs.helper", "step6.htmlSupportElements",
            "step6.htmlSupportElements.helper", "step6.htmlSupportElements.invalid",
            "step6.fullscreenPage", "step6.fullscreenPage.helper", "step6.fullscreenPage.needsPlugin",
            "step6.fullscreenPage.unsupported", "step6.fullscreenCustomWidth", "step6.fullscreenCustomWidth.invalid"));
        for (BuilderState.FullscreenPageMode mode : BuilderState.FullscreenPageMode.values()) {
            keys.add(switch (mode) {
                case A4_FIT -> "enum.fullscreenPage.a4Fit";
                case A4 -> "enum.fullscreenPage.a4";
                case FULL_WIDTH -> "enum.fullscreenPage.fullWidth";
                case CUSTOM -> "enum.fullscreenPage.custom";
            });
        }
        for (com.wontlost.ckeditor.CodeHighlightTheme theme : com.wontlost.ckeditor.CodeHighlightTheme.values()) {
            keys.add("enum.codeHighlightTheme." + theme.name());
        }
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
