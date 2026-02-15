package com.wontlost.ckeditor.domain;

import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.CKEditorPreset;
import com.wontlost.ckeditor.CKEditorTheme;
import com.wontlost.ckeditor.CKEditorType;
import com.wontlost.ckeditor.i18n.I18nUtil;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CKEditor Builder Configuration State
 * Stores all user selections during the wizard process
 */
public class BuilderState implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Build mode
     */
    public enum Mode {
        PRESET("enum.mode.preset", "enum.mode.preset.desc"),
        CUSTOM("enum.mode.custom", "enum.mode.custom.desc");

        private final String displayNameKey;
        private final String descriptionKey;

        Mode(String displayNameKey, String descriptionKey) {
            this.displayNameKey = displayNameKey;
            this.descriptionKey = descriptionKey;
        }

        public String getDisplayName() { return I18nUtil.get(displayNameKey); }
        public String getDescription() { return I18nUtil.get(descriptionKey); }
    }

    /**
     * Export language
     */
    public enum ExportLanguage {
        JAVA("Java", "java"),
        TYPESCRIPT("TypeScript", "typescript"),
        JSON("JSON", "json");

        private final String displayName;
        private final String extension;

        ExportLanguage(String displayName, String extension) {
            this.displayName = displayName;
            this.extension = extension;
        }

        public String getDisplayName() { return displayName; }
        public String getExtension() { return extension; }
    }

    /**
     * Dependency resolution mode
     */
    public enum DependencyMode {
        AUTO_RESOLVE("enum.dependencyMode.autoResolve", "enum.dependencyMode.autoResolve.desc"),
        AUTO_RESOLVE_WITH_RECOMMENDED("enum.dependencyMode.autoResolveWithRecommended", "enum.dependencyMode.autoResolveWithRecommended.desc"),
        STRICT("enum.dependencyMode.strict", "enum.dependencyMode.strict.desc"),
        MANUAL("enum.dependencyMode.manual", "enum.dependencyMode.manual.desc");

        private final String displayNameKey;
        private final String descriptionKey;

        DependencyMode(String displayNameKey, String descriptionKey) {
            this.displayNameKey = displayNameKey;
            this.descriptionKey = descriptionKey;
        }

        public String getDisplayName() { return I18nUtil.get(displayNameKey); }
        public String getDescription() { return I18nUtil.get(descriptionKey); }
    }

    /**
     * Fallback mode
     */
    public enum FallbackMode {
        TEXTAREA("enum.fallbackMode.textarea", "enum.fallbackMode.textarea.desc"),
        READ_ONLY("enum.fallbackMode.readOnly", "enum.fallbackMode.readOnly.desc"),
        ERROR_MESSAGE("enum.fallbackMode.errorMessage", "enum.fallbackMode.errorMessage.desc"),
        HIDDEN("enum.fallbackMode.hidden", "enum.fallbackMode.hidden.desc");

        private final String displayNameKey;
        private final String descriptionKey;

        FallbackMode(String displayNameKey, String descriptionKey) {
            this.displayNameKey = displayNameKey;
            this.descriptionKey = descriptionKey;
        }

        public String getDisplayName() { return I18nUtil.get(displayNameKey); }
        public String getDescription() { return I18nUtil.get(descriptionKey); }
    }

    /**
     * HTML sanitization policy
     */
    public enum SanitizationPolicy {
        NONE("enum.sanitizationPolicy.none", "enum.sanitizationPolicy.none.desc"),
        STRICT("enum.sanitizationPolicy.strict", "enum.sanitizationPolicy.strict.desc"),
        BASIC("enum.sanitizationPolicy.basic", "enum.sanitizationPolicy.basic.desc"),
        RELAXED("enum.sanitizationPolicy.relaxed", "enum.sanitizationPolicy.relaxed.desc");

        private final String displayNameKey;
        private final String descriptionKey;

        SanitizationPolicy(String displayNameKey, String descriptionKey) {
            this.displayNameKey = displayNameKey;
            this.descriptionKey = descriptionKey;
        }

        public String getDisplayName() { return I18nUtil.get(displayNameKey); }
        public String getDescription() { return I18nUtil.get(descriptionKey); }
    }

    /**
     * Custom plugin configuration
     */
    public static class CustomPluginConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String importPath;
        private List<String> toolbarItems = new ArrayList<>();
        private boolean premium;

        public CustomPluginConfig() {}

        public CustomPluginConfig(String name, String importPath, boolean premium) {
            this.name = name;
            this.importPath = importPath;
            this.premium = premium;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getImportPath() { return importPath; }
        public void setImportPath(String importPath) { this.importPath = importPath; }

        public List<String> getToolbarItems() { return toolbarItems; }
        public void setToolbarItems(List<String> toolbarItems) { this.toolbarItems = toolbarItems; }

        public boolean isPremium() { return premium; }
        public void setPremium(boolean premium) { this.premium = premium; }

        public static CustomPluginConfig fromPremium(String name, String... toolbarItems) {
            CustomPluginConfig config = new CustomPluginConfig(name, "ckeditor5-premium-features", true);
            if (toolbarItems != null && toolbarItems.length > 0) {
                config.setToolbarItems(Arrays.asList(toolbarItems));
            }
            return config;
        }
    }

    /**
     * Mention Feed configuration
     */
    public static class MentionFeedConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private String marker = "@";
        private List<String> feed = new ArrayList<>();
        private int minimumCharacters = 0;

        public MentionFeedConfig() {}

        public MentionFeedConfig(String marker, List<String> feed) {
            this.marker = marker;
            this.feed = feed;
        }

        public String getMarker() { return marker; }
        public void setMarker(String marker) { this.marker = marker; }

        public List<String> getFeed() { return feed; }
        public void setFeed(List<String> feed) { this.feed = feed; }

        public int getMinimumCharacters() { return minimumCharacters; }
        public void setMinimumCharacters(int minimumCharacters) { this.minimumCharacters = minimumCharacters; }
    }

    /**
     * Code block language configuration
     */
    public static class CodeBlockLanguageConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private String language;
        private String label;

        public CodeBlockLanguageConfig() {}

        public CodeBlockLanguageConfig(String language, String label) {
            this.language = language;
            this.label = label;
        }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    /**
     * Toolbar style configuration
     */
    public static class ToolbarStyleConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private String background;
        private String borderColor;
        private String borderRadius;
        private String buttonBackground;
        private String buttonHoverBackground;
        private String buttonActiveBackground;
        private String buttonOnBackground;
        private String buttonOnColor;
        private String iconColor;

        public String getBackground() { return background; }
        public void setBackground(String background) { this.background = background; }

        public String getBorderColor() { return borderColor; }
        public void setBorderColor(String borderColor) { this.borderColor = borderColor; }

        public String getBorderRadius() { return borderRadius; }
        public void setBorderRadius(String borderRadius) { this.borderRadius = borderRadius; }

        public String getButtonBackground() { return buttonBackground; }
        public void setButtonBackground(String buttonBackground) { this.buttonBackground = buttonBackground; }

        public String getButtonHoverBackground() { return buttonHoverBackground; }
        public void setButtonHoverBackground(String buttonHoverBackground) { this.buttonHoverBackground = buttonHoverBackground; }

        public String getButtonActiveBackground() { return buttonActiveBackground; }
        public void setButtonActiveBackground(String buttonActiveBackground) { this.buttonActiveBackground = buttonActiveBackground; }

        public String getButtonOnBackground() { return buttonOnBackground; }
        public void setButtonOnBackground(String buttonOnBackground) { this.buttonOnBackground = buttonOnBackground; }

        public String getButtonOnColor() { return buttonOnColor; }
        public void setButtonOnColor(String buttonOnColor) { this.buttonOnColor = buttonOnColor; }

        public String getIconColor() { return iconColor; }
        public void setIconColor(String iconColor) { this.iconColor = iconColor; }

        public boolean hasAnyStyle() {
            return background != null || borderColor != null || borderRadius != null ||
                   buttonBackground != null || buttonHoverBackground != null ||
                   buttonActiveBackground != null || buttonOnBackground != null ||
                   buttonOnColor != null || iconColor != null;
        }
    }

    // Step 1: Getting Started
    private Mode mode = Mode.PRESET;
    private CKEditorPreset preset = CKEditorPreset.STANDARD;
    private String presetCardId = "standard"; // 存储选择的卡片ID，用于识别 AI Document 等特殊预设

    // Step 2: Editor Type
    private CKEditorType editorType = CKEditorType.CLASSIC;

    // Step 3: Plugin Selection
    private final Set<CKEditorPlugin> selectedPlugins = new LinkedHashSet<>();
    private String pluginSearchTerm = "";
    private String pluginFilter = "all"; // all, selected, category
    private final List<CustomPluginConfig> customPlugins = new ArrayList<>();
    private final List<CustomPluginConfig> premiumPlugins = new ArrayList<>();

    // Step 4: Toolbar Configuration
    private final List<String> toolbarItems = new ArrayList<>();
    private boolean autoGenerateToolbar = true;
    private boolean shouldNotGroupWhenFull = false;
    private ToolbarStyleConfig toolbarStyle;

    // Step 5: Style and Language
    private CKEditorTheme theme = CKEditorTheme.AUTO;
    private String language = "en";
    private String customCss = "";
    private String overrideCssUrl = "";

    // Advanced Config - Premium (loaded from environment variable)
    private String licenseKey;
    {
        String env = System.getenv("CKEDITOR_LICENSE_KEY");
        licenseKey = env != null ? env : "";
    }

    // Advanced Config - CKEditorConfig
    private String placeholder = "";
    private List<String> fontSizes = new ArrayList<>();
    private boolean allowAnyFontSize = false;
    private List<String> fontFamilies = new ArrayList<>();
    private List<String> alignmentOptions = new ArrayList<>();
    private String linkDefaultProtocol = "https://";
    private boolean addTargetToExternalLinks = true;
    private List<String> imageToolbar = new ArrayList<>();
    private List<String> imageStyles = new ArrayList<>();
    private List<String> tableContentToolbar = new ArrayList<>();
    private String codeBlockIndent = "    ";
    private List<CodeBlockLanguageConfig> codeBlockLanguages = new ArrayList<>();
    private boolean mediaEmbedPreviewsInData = false;
    private List<MentionFeedConfig> mentionFeeds = new ArrayList<>();

    // Advanced Config - Upload
    private String simpleUploadUrl = "";
    private Map<String, String> simpleUploadHeaders = new HashMap<>();
    private boolean simpleUploadWithCredentials = false;
    private boolean allowPrivateNetworks = false;
    private long uploadMaxFileSize = 10_000_000; // 10MB
    private List<String> uploadAllowedMimeTypes = new ArrayList<>();

    // Advanced Config - Behavior
    private boolean autosaveEnabled = false;
    private int autosaveInterval = 5000;
    private boolean readOnly = false;
    private boolean hideToolbar = false;
    private boolean viewOnly = false;
    private FallbackMode fallbackMode = FallbackMode.TEXTAREA;
    private SanitizationPolicy sanitizationPolicy = SanitizationPolicy.RELAXED;
    private boolean ghsEnabled = false;
    private boolean documentOutlineEnabled = false;
    private boolean minimapEnabled = false;
    private DependencyMode dependencyMode = DependencyMode.AUTO_RESOLVE;

    // Advanced Config - Size
    private String editorWidth = "100%";
    private String editorHeight = "400px";
    private String initialValue = "";

    // Step 6: Export Settings
    private ExportLanguage exportLanguage = ExportLanguage.JAVA;
    private String configName = "my-editor-config";

    // State change listeners（CopyOnWriteArrayList 防止迭代时并发修改）
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    public BuilderState() {
        // Initialize default plugins
        initFromPreset(preset);
    }

    /**
     * Initialize state from preset
     */
    public void initFromPreset(CKEditorPreset preset) {
        this.preset = preset;
        this.selectedPlugins.clear();
        this.selectedPlugins.addAll(preset.getPlugins());
        this.toolbarItems.clear();
        this.toolbarItems.addAll(Arrays.asList(preset.getDefaultToolbar()));

        // 根据预设设置编辑器类型
        this.editorType = switch (preset) {
            case DOCUMENT, AI_DOCUMENT, COLLABORATIVE -> CKEditorType.DECOUPLED;
            case NOTION -> CKEditorType.BALLOON;
            default -> CKEditorType.CLASSIC;
        };

        notifyListeners();
    }

    /**
     * Reset to default state
     */
    public void reset() {
        this.mode = Mode.PRESET;
        this.preset = CKEditorPreset.STANDARD;
        this.editorType = CKEditorType.CLASSIC;
        this.theme = CKEditorTheme.AUTO;
        this.language = "en";
        this.customCss = "";
        this.overrideCssUrl = "";
        this.exportLanguage = ExportLanguage.JAVA;
        this.autoGenerateToolbar = true;
        this.shouldNotGroupWhenFull = false;
        this.toolbarStyle = null;

        // Premium config reset (preserve license key from environment variable)
        String env = System.getenv("CKEDITOR_LICENSE_KEY");
        this.licenseKey = env != null ? env : "";
        this.customPlugins.clear();
        this.premiumPlugins.clear();

        // Advanced config reset
        this.placeholder = "";
        this.fontSizes.clear();
        this.allowAnyFontSize = false;
        this.fontFamilies.clear();
        this.alignmentOptions.clear();
        this.linkDefaultProtocol = "https://";
        this.addTargetToExternalLinks = true;
        this.imageToolbar.clear();
        this.imageStyles.clear();
        this.tableContentToolbar.clear();
        this.codeBlockIndent = "    ";
        this.codeBlockLanguages.clear();
        this.mediaEmbedPreviewsInData = false;
        this.mentionFeeds.clear();

        // Upload config reset
        this.simpleUploadUrl = "";
        this.simpleUploadHeaders.clear();
        this.simpleUploadWithCredentials = false;
        this.allowPrivateNetworks = false;
        this.uploadMaxFileSize = 10_000_000;
        this.uploadAllowedMimeTypes.clear();

        // Behavior config reset
        this.autosaveEnabled = false;
        this.autosaveInterval = 5000;
        this.readOnly = false;
        this.hideToolbar = false;
        this.viewOnly = false;
        this.fallbackMode = FallbackMode.TEXTAREA;
        this.sanitizationPolicy = SanitizationPolicy.RELAXED;
        this.ghsEnabled = false;
        this.documentOutlineEnabled = false;
        this.minimapEnabled = false;
        this.dependencyMode = DependencyMode.AUTO_RESOLVE;

        // Size config reset
        this.editorWidth = "100%";
        this.editorHeight = "400px";
        this.initialValue = "";

        initFromPreset(preset);
    }

    // ========== Getters and Setters ==========

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) {
        Mode previousMode = this.mode;
        this.mode = mode;

        // When switching from custom mode back to preset mode, reset plugins and toolbar
        if (previousMode == Mode.CUSTOM && mode == Mode.PRESET && preset != null) {
            initFromPreset(preset);
        }

        notifyListeners();
    }

    public CKEditorPreset getPreset() { return preset; }
    public void setPreset(CKEditorPreset preset) {
        this.preset = preset;
        if (mode == Mode.PRESET) {
            initFromPreset(preset);
        }
        notifyListeners();
    }

    public String getPresetCardId() { return presetCardId; }
    public void setPresetCardId(String presetCardId) {
        this.presetCardId = presetCardId;
        notifyListeners();
    }

    /**
     * 检查是否应该显示文档选项（Document Options）
     * 对于 Document preset、AI Document 或 DECOUPLED 编辑器类型显示
     */
    public boolean shouldShowDocumentOptions() {
        // Document preset
        if (preset == CKEditorPreset.DOCUMENT) {
            return true;
        }
        // AI Document (cardId = "ai-document")
        if ("ai-document".equals(presetCardId)) {
            return true;
        }
        // DECOUPLED editor type
        return editorType == CKEditorType.DECOUPLED;
    }

    public CKEditorType getEditorType() { return editorType; }
    public void setEditorType(CKEditorType editorType) {
        this.editorType = editorType;
        notifyListeners();
    }

    public Set<CKEditorPlugin> getSelectedPlugins() {
        return Collections.unmodifiableSet(selectedPlugins);
    }

    public void addPlugin(CKEditorPlugin plugin) {
        selectedPlugins.add(plugin);
        if (autoGenerateToolbar) {
            regenerateToolbar();
        }
        notifyListeners();
    }

    public void removePlugin(CKEditorPlugin plugin) {
        selectedPlugins.remove(plugin);
        if (autoGenerateToolbar) {
            regenerateToolbar();
        }
        notifyListeners();
    }

    public void setPlugins(Collection<CKEditorPlugin> plugins) {
        selectedPlugins.clear();
        selectedPlugins.addAll(plugins);
        if (autoGenerateToolbar) {
            regenerateToolbar();
        }
        notifyListeners();
    }

    public boolean hasPlugin(CKEditorPlugin plugin) {
        return selectedPlugins.contains(plugin);
    }

    public String getPluginSearchTerm() { return pluginSearchTerm; }
    public void setPluginSearchTerm(String term) {
        this.pluginSearchTerm = term;
        notifyListeners();
    }

    public String getPluginFilter() { return pluginFilter; }
    public void setPluginFilter(String filter) {
        this.pluginFilter = filter;
        notifyListeners();
    }

    public List<String> getToolbarItems() {
        return Collections.unmodifiableList(toolbarItems);
    }

    public void setToolbarItems(List<String> items) {
        toolbarItems.clear();
        toolbarItems.addAll(items);
        notifyListeners();
    }

    public boolean isAutoGenerateToolbar() { return autoGenerateToolbar; }
    public void setAutoGenerateToolbar(boolean auto) {
        this.autoGenerateToolbar = auto;
        if (auto) {
            regenerateToolbar();
        }
        notifyListeners();
    }

    public CKEditorTheme getTheme() { return theme; }
    public void setTheme(CKEditorTheme theme) {
        this.theme = theme;
        notifyListeners();
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) {
        this.language = language;
        notifyListeners();
    }

    public String getCustomCss() { return customCss; }
    public void setCustomCss(String css) {
        this.customCss = css;
        notifyListeners();
    }

    public ExportLanguage getExportLanguage() { return exportLanguage; }
    public void setExportLanguage(ExportLanguage lang) {
        this.exportLanguage = lang;
        notifyListeners();
    }

    public String getConfigName() { return configName; }
    public void setConfigName(String name) {
        this.configName = name;
        notifyListeners();
    }

    // ========== Custom/Premium Plugins ==========

    public List<CustomPluginConfig> getCustomPlugins() {
        return Collections.unmodifiableList(customPlugins);
    }

    public void addCustomPlugin(CustomPluginConfig plugin) {
        customPlugins.add(plugin);
        notifyListeners();
    }

    public void removeCustomPlugin(CustomPluginConfig plugin) {
        customPlugins.remove(plugin);
        notifyListeners();
    }

    public void clearCustomPlugins() {
        customPlugins.clear();
        notifyListeners();
    }

    public List<CustomPluginConfig> getPremiumPlugins() {
        return Collections.unmodifiableList(premiumPlugins);
    }

    public void addPremiumPlugin(CustomPluginConfig plugin) {
        premiumPlugins.add(plugin);
        notifyListeners();
    }

    public void removePremiumPlugin(CustomPluginConfig plugin) {
        premiumPlugins.remove(plugin);
        notifyListeners();
    }

    public void clearPremiumPlugins() {
        premiumPlugins.clear();
        notifyListeners();
    }

    public boolean hasPremiumPlugins() {
        return !premiumPlugins.isEmpty();
    }

    // ========== Toolbar Advanced Config ==========

    public boolean isShouldNotGroupWhenFull() { return shouldNotGroupWhenFull; }
    public void setShouldNotGroupWhenFull(boolean shouldNotGroupWhenFull) {
        this.shouldNotGroupWhenFull = shouldNotGroupWhenFull;
        notifyListeners();
    }

    public ToolbarStyleConfig getToolbarStyle() { return toolbarStyle; }
    public void setToolbarStyle(ToolbarStyleConfig toolbarStyle) {
        this.toolbarStyle = toolbarStyle;
        notifyListeners();
    }

    // ========== Premium Config ==========

    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
        notifyListeners();
    }

    public boolean hasLicenseKey() {
        return licenseKey != null && !licenseKey.trim().isEmpty();
    }

    // ========== CKEditorConfig Advanced Config ==========

    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        notifyListeners();
    }

    public List<String> getFontSizes() { return fontSizes; }
    public void setFontSizes(List<String> fontSizes) {
        this.fontSizes = fontSizes != null ? fontSizes : new ArrayList<>();
        notifyListeners();
    }

    public boolean isAllowAnyFontSize() { return allowAnyFontSize; }
    public void setAllowAnyFontSize(boolean allowAnyFontSize) {
        this.allowAnyFontSize = allowAnyFontSize;
        notifyListeners();
    }

    public List<String> getFontFamilies() { return fontFamilies; }
    public void setFontFamilies(List<String> fontFamilies) {
        this.fontFamilies = fontFamilies != null ? fontFamilies : new ArrayList<>();
        notifyListeners();
    }

    public List<String> getAlignmentOptions() { return alignmentOptions; }
    public void setAlignmentOptions(List<String> alignmentOptions) {
        this.alignmentOptions = alignmentOptions != null ? alignmentOptions : new ArrayList<>();
        notifyListeners();
    }

    public String getLinkDefaultProtocol() { return linkDefaultProtocol; }
    public void setLinkDefaultProtocol(String linkDefaultProtocol) {
        this.linkDefaultProtocol = linkDefaultProtocol;
        notifyListeners();
    }

    public boolean isAddTargetToExternalLinks() { return addTargetToExternalLinks; }
    public void setAddTargetToExternalLinks(boolean addTargetToExternalLinks) {
        this.addTargetToExternalLinks = addTargetToExternalLinks;
        notifyListeners();
    }

    public List<String> getImageToolbar() { return imageToolbar; }
    public void setImageToolbar(List<String> imageToolbar) {
        this.imageToolbar = imageToolbar != null ? imageToolbar : new ArrayList<>();
        notifyListeners();
    }

    public List<String> getImageStyles() { return imageStyles; }
    public void setImageStyles(List<String> imageStyles) {
        this.imageStyles = imageStyles != null ? imageStyles : new ArrayList<>();
        notifyListeners();
    }

    public List<String> getTableContentToolbar() { return tableContentToolbar; }
    public void setTableContentToolbar(List<String> tableContentToolbar) {
        this.tableContentToolbar = tableContentToolbar != null ? tableContentToolbar : new ArrayList<>();
        notifyListeners();
    }

    public String getCodeBlockIndent() { return codeBlockIndent; }
    public void setCodeBlockIndent(String codeBlockIndent) {
        this.codeBlockIndent = codeBlockIndent;
        notifyListeners();
    }

    public List<CodeBlockLanguageConfig> getCodeBlockLanguages() { return codeBlockLanguages; }
    public void setCodeBlockLanguages(List<CodeBlockLanguageConfig> codeBlockLanguages) {
        this.codeBlockLanguages = codeBlockLanguages != null ? codeBlockLanguages : new ArrayList<>();
        notifyListeners();
    }

    public boolean isMediaEmbedPreviewsInData() { return mediaEmbedPreviewsInData; }
    public void setMediaEmbedPreviewsInData(boolean mediaEmbedPreviewsInData) {
        this.mediaEmbedPreviewsInData = mediaEmbedPreviewsInData;
        notifyListeners();
    }

    public List<MentionFeedConfig> getMentionFeeds() { return mentionFeeds; }
    public void setMentionFeeds(List<MentionFeedConfig> mentionFeeds) {
        this.mentionFeeds = mentionFeeds != null ? mentionFeeds : new ArrayList<>();
        notifyListeners();
    }

    public String getOverrideCssUrl() { return overrideCssUrl; }
    public void setOverrideCssUrl(String overrideCssUrl) {
        this.overrideCssUrl = overrideCssUrl;
        notifyListeners();
    }

    // ========== Upload Config ==========

    public String getSimpleUploadUrl() { return simpleUploadUrl; }
    public void setSimpleUploadUrl(String simpleUploadUrl) {
        this.simpleUploadUrl = simpleUploadUrl;
        notifyListeners();
    }

    public Map<String, String> getSimpleUploadHeaders() { return simpleUploadHeaders; }
    public void setSimpleUploadHeaders(Map<String, String> simpleUploadHeaders) {
        this.simpleUploadHeaders = simpleUploadHeaders != null ? simpleUploadHeaders : new HashMap<>();
        notifyListeners();
    }

    public boolean isSimpleUploadWithCredentials() { return simpleUploadWithCredentials; }
    public void setSimpleUploadWithCredentials(boolean simpleUploadWithCredentials) {
        this.simpleUploadWithCredentials = simpleUploadWithCredentials;
        notifyListeners();
    }

    public boolean isAllowPrivateNetworks() { return allowPrivateNetworks; }
    public void setAllowPrivateNetworks(boolean allowPrivateNetworks) {
        this.allowPrivateNetworks = allowPrivateNetworks;
        notifyListeners();
    }

    public long getUploadMaxFileSize() { return uploadMaxFileSize; }
    public void setUploadMaxFileSize(long uploadMaxFileSize) {
        this.uploadMaxFileSize = uploadMaxFileSize;
        notifyListeners();
    }

    public List<String> getUploadAllowedMimeTypes() { return uploadAllowedMimeTypes; }
    public void setUploadAllowedMimeTypes(List<String> uploadAllowedMimeTypes) {
        this.uploadAllowedMimeTypes = uploadAllowedMimeTypes != null ? uploadAllowedMimeTypes : new ArrayList<>();
        notifyListeners();
    }

    // ========== Behavior Config ==========

    public boolean isAutosaveEnabled() { return autosaveEnabled; }
    public void setAutosaveEnabled(boolean autosaveEnabled) {
        this.autosaveEnabled = autosaveEnabled;
        notifyListeners();
    }

    public int getAutosaveInterval() { return autosaveInterval; }
    public void setAutosaveInterval(int autosaveInterval) {
        this.autosaveInterval = autosaveInterval;
        notifyListeners();
    }

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        notifyListeners();
    }

    public boolean isHideToolbar() { return hideToolbar; }
    public void setHideToolbar(boolean hideToolbar) {
        this.hideToolbar = hideToolbar;
        notifyListeners();
    }

    public boolean isViewOnly() { return viewOnly; }
    public void setViewOnly(boolean viewOnly) {
        this.viewOnly = viewOnly;
        if (viewOnly) {
            this.readOnly = true;
            this.hideToolbar = true;
        }
        notifyListeners();
    }

    public FallbackMode getFallbackMode() { return fallbackMode; }
    public void setFallbackMode(FallbackMode fallbackMode) {
        this.fallbackMode = fallbackMode;
        notifyListeners();
    }

    public SanitizationPolicy getSanitizationPolicy() { return sanitizationPolicy; }
    public void setSanitizationPolicy(SanitizationPolicy sanitizationPolicy) {
        this.sanitizationPolicy = sanitizationPolicy;
        notifyListeners();
    }

    public boolean isGhsEnabled() { return ghsEnabled; }
    public void setGhsEnabled(boolean ghsEnabled) {
        this.ghsEnabled = ghsEnabled;
        notifyListeners();
    }

    public boolean isDocumentOutlineEnabled() { return documentOutlineEnabled; }
    public void setDocumentOutlineEnabled(boolean documentOutlineEnabled) {
        this.documentOutlineEnabled = documentOutlineEnabled;
        notifyListeners();
    }

    public boolean isMinimapEnabled() { return minimapEnabled; }
    public void setMinimapEnabled(boolean minimapEnabled) {
        this.minimapEnabled = minimapEnabled;
        notifyListeners();
    }

    public DependencyMode getDependencyMode() { return dependencyMode; }
    public void setDependencyMode(DependencyMode dependencyMode) {
        this.dependencyMode = dependencyMode;
        notifyListeners();
    }

    // ========== Size Config ==========

    public String getEditorWidth() { return editorWidth; }
    public void setEditorWidth(String editorWidth) {
        this.editorWidth = editorWidth;
        notifyListeners();
    }

    public String getEditorHeight() { return editorHeight; }
    public void setEditorHeight(String editorHeight) {
        this.editorHeight = editorHeight;
        notifyListeners();
    }

    public String getInitialValue() { return initialValue; }
    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
        notifyListeners();
    }

    // ========== Utility Methods ==========

    /**
     * Regenerate toolbar based on selected plugins
     */
    private void regenerateToolbar() {
        toolbarItems.clear();
        Set<String> items = new LinkedHashSet<>();
        for (CKEditorPlugin plugin : selectedPlugins) {
            items.addAll(plugin.getToolbarItems());
        }
        toolbarItems.addAll(items);
    }

    /**
     * Get configuration summary
     */
    public String getSummary() {
        return String.format(
            "Type: %s | Preset: %s | Plugins: %d | Theme: %s | Language: %s",
            editorType.name(),
            preset.getDisplayName(),
            selectedPlugins.size(),
            theme.name(),
            language
        );
    }

    // ========== State Change Listeners ==========

    public interface StateChangeListener {
        void onStateChanged(BuilderState state);
    }

    public void addListener(StateChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (StateChangeListener listener : listeners) {
            listener.onStateChanged(this);
        }
    }
}
