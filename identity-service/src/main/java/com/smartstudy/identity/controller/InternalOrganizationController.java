package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.response.InternalOrganizationSummaryResponse;
import com.smartstudy.identity.repository.OrganizationRepository;
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
@RequestMapping("/internal/organizations")
@RequiredArgsConstructor
public class InternalOrganizationController {

    private static final Logger log = LoggerFactory.getLogger(InternalOrganizationController.class);
    private static final int MAX_LOOKUP_IDS = 500;

    private final OrganizationRepository organizationRepository;

    /**
     * Ids with no organization behind them are simply absent from the result.
     * That is what lets a caller tell "no such organization" (a 200 with an
     * empty list) apart from "identity-service is unreachable" (a transport
     * error) — a distinction planning-service depends on, since it writes the
     * name it gets back into a column it will never revisit.
     */
    @GetMapping("/lookup")
    public List<InternalOrganizationSummaryResponse> lookup(@RequestParam("ids") List<String> ids) {
        log.info("Internal request: GET /internal/organizations/lookup | {} id(s)", ids.size());
        List<String> capped = ids.size() > MAX_LOOKUP_IDS ? ids.subList(0, MAX_LOOKUP_IDS) : ids;
        return organizationRepository.findAllById(capped).stream()
                .map(organization -> new InternalOrganizationSummaryResponse(
                        organization.getId(), organization.getName()))
                .toList();
    }
}
