package com.wontlost.ckeditor.generator;

import com.wontlost.ckeditor.domain.BuilderState;

/**
 * Code Generator Interface
 * Generates configuration code in corresponding language based on BuilderState
 */
public interface CodeGenerator {

    /**
     * Generate code
     * @param state build state
     * @return generated code string
     */
    String generate(BuilderState state);

    /**
     * Get generated file extension
     * @return file extension (without dot)
     */
    String getFileExtension();

    /**
     * Get generator display name
     * @return display name
     */
    String getDisplayName();

    /**
     * Get code MIME type
     * @return MIME type
     */
    default String getMimeType() {
        return "text/plain";
    }
}
