package com.wontlost.ckeditor.generator;

import com.wontlost.ckeditor.domain.BuilderState;

import java.util.Map;

/**
 * Code Generator Factory
 * Get corresponding code generator by export language type
 */
public class CodeGeneratorFactory {

    private static final Map<BuilderState.ExportLanguage, CodeGenerator> generators = Map.of(
        BuilderState.ExportLanguage.JAVA, new JavaCodeGenerator(),
        BuilderState.ExportLanguage.TYPESCRIPT, new TypeScriptCodeGenerator(),
        BuilderState.ExportLanguage.JSON, new JsonConfigGenerator()
    );

    private CodeGeneratorFactory() {
        // Prevent instantiation
    }

    /**
     * Get code generator
     * @param language export language type
     * @return corresponding code generator
     */
    public static CodeGenerator getGenerator(BuilderState.ExportLanguage language) {
        CodeGenerator generator = generators.get(language);
        if (generator == null) {
            // Default to Java generator
            return generators.get(BuilderState.ExportLanguage.JAVA);
        }
        return generator;
    }

    /**
     * Generate code
     * @param state build state
     * @return generated code
     */
    public static String generateCode(BuilderState state) {
        return getGenerator(state.getExportLanguage()).generate(state);
    }

    /**
     * Get file extension
     * @param language export language type
     * @return file extension
     */
    public static String getFileExtension(BuilderState.ExportLanguage language) {
        return getGenerator(language).getFileExtension();
    }

    /**
     * Get MIME type
     * @param language export language type
     * @return MIME type
     */
    public static String getMimeType(BuilderState.ExportLanguage language) {
        return getGenerator(language).getMimeType();
    }
}
