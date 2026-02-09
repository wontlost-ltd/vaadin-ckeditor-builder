package com.wontlost.ckeditor.generator;

import com.wontlost.ckeditor.CKEditorPlugin;
import com.wontlost.ckeditor.domain.BuilderState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON Config Generator
 * Generates importable/exportable JSON configuration file
 */
public class JsonConfigGenerator implements CodeGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String generate(BuilderState state) {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");

        // Metadata
        generateMetadata(sb, state);

        // Configuration content
        generateConfig(sb, state);

        // Plugin list
        generatePlugins(sb, state);

        // Toolbar configuration
        generateToolbar(sb, state);

        // Custom CSS (if any)
        if (state.getCustomCss() != null && !state.getCustomCss().isEmpty()) {
            generateCustomCss(sb, state);
        }

        // Remove trailing comma and close
        sb.append("  \"_generator\": \"CKEditor Builder (Open Source)\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private void generateMetadata(StringBuilder sb, BuilderState state) {
        sb.append("  \"$schema\": \"https://ckeditor-builder.wontlost.com/schema/v1.json\",\n");
        sb.append("  \"name\": \"").append(escapeJson(state.getConfigName())).append("\",\n");
        sb.append("  \"version\": \"1.0.0\",\n");
        sb.append("  \"generatedAt\": \"").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\",\n");
    }

    private void generateConfig(StringBuilder sb, BuilderState state) {
        sb.append("  \"config\": {\n");
        sb.append("    \"preset\": \"").append(state.getPreset().name()).append("\",\n");
        sb.append("    \"editorType\": \"").append(state.getEditorType().name()).append("\",\n");
        sb.append("    \"theme\": \"").append(state.getTheme().name()).append("\",\n");
        sb.append("    \"language\": \"").append(escapeJson(state.getLanguage())).append("\",\n");
        sb.append("    \"mode\": \"").append(state.getMode().name()).append("\",\n");
        sb.append("    \"autoGenerateToolbar\": ").append(state.isAutoGenerateToolbar()).append("\n");
        sb.append("  },\n");
    }

    private void generatePlugins(StringBuilder sb, BuilderState state) {
        sb.append("  \"plugins\": [\n");

        List<String> plugins = state.getSelectedPlugins().stream()
            .map(p -> "    {\n" +
                      "      \"name\": \"" + escapeJson(p.name()) + "\",\n" +
                      "      \"jsName\": \"" + escapeJson(p.getJsName()) + "\",\n" +
                      "      \"category\": \"" + escapeJson(p.getCategory().name()) + "\"\n" +
                      "    }")
            .collect(Collectors.toList());
        sb.append(String.join(",\n", plugins));

        sb.append("\n  ],\n");
    }

    private void generateToolbar(StringBuilder sb, BuilderState state) {
        sb.append("  \"toolbar\": {\n");
        sb.append("    \"items\": [\n");

        List<String> toolbarItems;
        if (!state.getToolbarItems().isEmpty()) {
            toolbarItems = state.getToolbarItems().stream()
                .map(t -> "      \"" + escapeJson(t) + "\"")
                .collect(Collectors.toList());
        } else {
            // Generate default toolbar based on plugins
            toolbarItems = state.getSelectedPlugins().stream()
                .flatMap(p -> p.getToolbarItems().stream())
                .distinct()
                .map(t -> "      \"" + escapeJson(t) + "\"")
                .collect(Collectors.toList());
        }
        sb.append(String.join(",\n", toolbarItems));

        sb.append("\n    ],\n");
        sb.append("    \"shouldNotGroupWhenFull\": false\n");
        sb.append("  },\n");
    }

    private void generateCustomCss(StringBuilder sb, BuilderState state) {
        sb.append("  \"customCss\": \"").append(escapeJson(state.getCustomCss())).append("\",\n");
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\0", "\\0");
    }

    @Override
    public String getFileExtension() {
        return "json";
    }

    @Override
    public String getDisplayName() {
        return "JSON";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }
}
