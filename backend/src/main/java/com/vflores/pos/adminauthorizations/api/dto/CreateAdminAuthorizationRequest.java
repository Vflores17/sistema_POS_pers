package com.vflores.pos.adminauthorizations.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateAdminAuthorizationRequest(
        @NotBlank String adminUsername,
        @NotBlank String adminPassword,
        @NotBlank String operationKey,
        @NotBlank String resourceType,
        UUID resourceId
) {
}
