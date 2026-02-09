package com.wontlost.ckeditor.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 验证结果
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;

    private ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = errors;
        this.warnings = warnings;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }

    public static ValidationResult error(String message) {
        return new ValidationResult(false, List.of(message), Collections.emptyList());
    }

    public static ValidationResult errors(List<String> messages) {
        return new ValidationResult(false, new ArrayList<>(messages), Collections.emptyList());
    }

    public static ValidationResult warning(String message) {
        return new ValidationResult(true, Collections.emptyList(), List.of(message));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public boolean hasWarnings() { return !warnings.isEmpty(); }

    public String getFirstError() {
        return errors.isEmpty() ? "" : errors.get(0);
    }

    public static class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public Builder addError(String error) {
            errors.add(error);
            return this;
        }

        public Builder addWarning(String warning) {
            warnings.add(warning);
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(errors.isEmpty(), errors, warnings);
        }
    }
}
