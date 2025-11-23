package com.chat4all.connector.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Credential validation result
 * Returned by MessageConnector.validateCredentials()
 */
@Data
@Builder
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;

    private String message;

    private List<String> errors;

    private String platformInfo;
    
    public ValidationResult() {
        this.errors = new ArrayList<>();
    }

    public static ValidationResult success(String platformInfo) {
        return ValidationResult.builder()
            .valid(true)
            .message("Credentials validated successfully")
            .platformInfo(platformInfo)
            .errors(new ArrayList<>())
            .build();
    }

    public static ValidationResult failure(String message, List<String> errors) {
        return ValidationResult.builder()
            .valid(false)
            .message(message)
            .errors(errors != null ? errors : new ArrayList<>())
            .build();
    }
}
