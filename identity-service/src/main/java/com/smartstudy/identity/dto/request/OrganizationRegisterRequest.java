package com.smartstudy.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrganizationRegisterRequest(
        @NotBlank(message = "Field 'name' is required for registration.")
        String name
) {
}
