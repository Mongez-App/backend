package com.smartstudy.planning.client;

import com.smartstudy.planning.dto.response.UserPreferencesData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Reads the caller's saved study preferences from identity-service.
 * identity-service's token filter accepts {@code X-User-Id} as an authenticated
 * principal, so "/users/me/preferences" resolves to whichever uid we pass.
 */
@FeignClient(name = "identity-service", fallbackFactory = IdentityServiceClientFallbackFactory.class)
public interface IdentityServiceClient {

    @GetMapping("/users/me/preferences")
    UserPreferencesData getPreferences(@RequestHeader("X-User-Id") String userId);
}
