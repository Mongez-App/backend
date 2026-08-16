package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.response.InternalUserSummaryResponse;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service lookups. Not routed through the api-gateway — only
 * reachable on the internal network, and still behind the token filter, which
 * accepts the calling service's {@code X-User-Id} header.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final Logger log = LoggerFactory.getLogger(InternalUserController.class);
    private static final int MAX_LOOKUP_IDS = 500;

    private final UserRepository userRepository;

    @GetMapping("/lookup")
    public List<InternalUserSummaryResponse> lookup(@RequestParam("ids") List<String> ids) {
        log.info("Internal request: GET /internal/users/lookup | {} id(s)", ids.size());
        List<String> capped = ids.size() > MAX_LOOKUP_IDS ? ids.subList(0, MAX_LOOKUP_IDS) : ids;
        return userRepository.findAllById(capped).stream()
                .map(user -> new InternalUserSummaryResponse(user.getId(), user.getName(), user.getEmail()))
                .toList();
    }
}
