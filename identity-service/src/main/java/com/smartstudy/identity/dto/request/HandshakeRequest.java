package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record HandshakeRequest(
        @NotBlank(message = "name is required.")
        @JsonProperty("name") String name,

        @NotBlank(message = "appearance is required.")
        @JsonProperty("appearance") String appearance,

        @NotBlank(message = "language is required.")
        @JsonProperty("language") String language
) {}
