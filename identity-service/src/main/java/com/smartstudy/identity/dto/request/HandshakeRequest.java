package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record HandshakeRequest(
        @NotNull(message = "is_guest field is required.")
        @JsonProperty("is_guest") Boolean isGuest
) {}
