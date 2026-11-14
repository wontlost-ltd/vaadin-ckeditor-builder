package com.wontlost.ckeditor.domain;

import com.wontlost.ckeditor.CKEditorPlugin;

import java.util.List;
import java.util.Set;

/**
 * 向导可选插件目录中「不能从 addon 枚举直接推导」的那部分知识，集中于此供各步骤共用：
 * <ul>
 *   <li>premium 目录项（名字、描述 i18n key、工具栏项）——名字必须是 addon
 *       {@code PremiumPlugin} 真实导出的插件类，由 PluginCatalogueTest 守护；</li>
 *   <li>实时预览需要跳过的插件：premium 中依赖 AI / 协作服务端配置者，以及需商业 license 的免费插件
 *       （未配置 key 时加载会让 CKEditor 49 把编辑器锁为只读）。</li>
 * </ul>
 */
public final class PluginCatalogue {

    private PluginCatalogue() {}

    /** premium 目录项：插件类名、描述的 i18n key、工具栏项（无则空串）。 */
    public record PremiumEntry(String name, String descriptionKey, String toolbarItem) {}

    public static final List<PremiumEntry> PREMIUM = List.of(
        new PremiumEntry("ExportPdf", "premium.desc.exportPdf", "exportPdf"),
        new PremiumEntry("ExportWord", "premium.desc.exportWord", "exportWord"),
        new PremiumEntry("ImportWord", "premium.desc.importWord", "importWord"),
        new PremiumEntry("FormatPainter", "premium.desc.formatPainter", "formatPainter"),
        new PremiumEntry("SlashCommand", "premium.desc.slashCommand", ""),
        new PremiumEntry("TableOfContents", "premium.desc.tableOfContents", "tableOfContents"),
        new PremiumEntry("DocumentOutline", "premium.desc.documentOutline", ""),
        new PremiumEntry("Template", "premium.desc.template", "insertTemplate"),
        new PremiumEntry("CaseChange", "premium.desc.caseChange", "caseChange"),
        new PremiumEntry("MergeFields", "premium.desc.mergeFields", "insertMergeField"),
        new PremiumEntry("Pagination", "premium.desc.pagination", "pagination"),
        new PremiumEntry("AIAssistant", "premium.desc.aiAssistant", "aiAssistant"),
        new PremiumEntry("AIChat", "premium.desc.aiChat", "toggleAi"),
        // CKEditor 49.1 新增
        new PremiumEntry("AIChatBalloon", "premium.desc.aiChatBalloon", ""),
        new PremiumEntry("AIEditorConfig", "premium.desc.aiEditorConfig", ""),
        new PremiumEntry("ProposedChanges", "premium.desc.proposedChanges", ""),
        new PremiumEntry("Comments", "premium.desc.comments", "comment"),
        new PremiumEntry("TrackChanges", "premium.desc.trackChanges", "trackChanges"),
        new PremiumEntry("RevisionHistory", "premium.desc.revisionHistory", "revisionHistory"),
        new PremiumEntry("RealTimeContentFetcher", "premium.desc.realTimeContentFetcher", "")
    );

    /** 实时预览中跳过的 premium 插件：需要 AI adapter、协作服务或专用容器配置，向导不提供这些配置。 */
    public static final Set<String> PREMIUM_PREVIEW_SKIP = Set.of(
        "DocumentOutline", "TableOfContents",
        "AIAssistant", "AIChat", "AIChatBalloon", "AIEditorConfig", "ProposedChanges",
        "Comments", "TrackChanges", "RevisionHistory", "RealTimeContentFetcher",
        "Pagination"
    );

    /** 由免费 umbrella 导出但运行时需商业 license 的插件。 */
    private static final Set<CKEditorPlugin> LICENSE_GATED = Set.of(CKEditorPlugin.TABLE_LAYOUT);

    /** 预览时需要编辑器 DOM 完全就绪，暂不进预览。 */
    private static final Set<CKEditorPlugin> PREVIEW_UNSAFE = Set.of(CKEditorPlugin.MINIMAP);

    /**
     * 某个免费插件能否进入实时预览：Minimap 一律跳过；license 门控插件只在已配置商业 license key
     *（非 GPL）时加入，
     * 否则 CKEditor 49 会以 license 错误把整个预览编辑器锁为只读。导出代码不受此限制。
     */
    public static boolean isPreviewable(CKEditorPlugin plugin, boolean hasCommercialLicenseKey) {
        if (PREVIEW_UNSAFE.contains(plugin)) {
            return false;
        }
        return hasCommercialLicenseKey || !LICENSE_GATED.contains(plugin);
    }
}
