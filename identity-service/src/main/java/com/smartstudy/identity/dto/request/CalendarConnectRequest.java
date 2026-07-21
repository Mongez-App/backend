package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CalendarConnectRequest(
        @NotBlank(message = "Authorization code is required")
        @JsonProperty("authorization_code") String authorizationCode,

        @NotBlank(message = "Provider is required")
        @JsonProperty("provider") String provider,

        @NotBlank(message = "Redirect URI is required")
        @URL(message = "Redirect URI must be a valid URL")
        @JsonProperty("redirect_uri") String redirectUri
) {}
