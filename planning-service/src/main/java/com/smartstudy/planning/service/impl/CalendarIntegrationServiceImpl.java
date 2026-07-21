package com.smartstudy.planning.service.impl;

import com.smartstudy.planning.dto.request.CalendarConnectRequest;
import com.smartstudy.planning.dto.response.CalendarConnectResponse;
import com.smartstudy.planning.dto.response.CalendarStatusResponse;
import com.smartstudy.planning.model.CalendarIntegration;
import com.smartstudy.planning.repository.CalendarIntegrationRepository;
import com.smartstudy.planning.service.CalendarIntegrationService;
import com.smartstudy.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CalendarIntegrationServiceImpl implements CalendarIntegrationService {

    private final CalendarIntegrationRepository repository;

    @Override
    @Transactional
    public CalendarConnectResponse connect(String userId, CalendarConnectRequest request) {
        if (!"google_calendar".equals(request.provider())) {
            throw new BadRequestException("INVALID_PROVIDER", "Unsupported calendar provider.");
        }

        CalendarIntegration integration = repository.findByUserId(userId).orElse(null);
        if (integration != null && integration.isConnected()) {
            throw new com.smartstudy.shared.exception.ConflictException("ALREADY_CONNECTED", "Calendar is already connected. Disconnect first.");
        }

        // Mocking validation. If this were a real integration, we would exchange the code for tokens here.
        if (request.authorizationCode().startsWith("error")) {
            throw new BadRequestException("INVALID_AUTH_CODE", "Authorization code is invalid or expired.");
        }

        if (integration == null) {
            integration = CalendarIntegration.builder()
                    .userId(userId)
                    .provider(request.provider())
                    .build();
        }

        integration.setConnected(true);
        integration.setConnectedAt(Instant.now());
        integration.setDisconnectedAt(null);
        
        integration = repository.save(integration);

        return new CalendarConnectResponse(true, integration.getProvider(), integration.getConnectedAt());
    }

    @Override
    public CalendarStatusResponse getStatus(String userId) {
        Optional<CalendarIntegration> integrationOpt = repository.findByUserId(userId);
        if (integrationOpt.isPresent()) {
            CalendarIntegration integration = integrationOpt.get();
            return new CalendarStatusResponse(integration.isConnected(), integration.getProvider(), integration.getConnectedAt());
        }
        return new CalendarStatusResponse(false, null, null);
    }

    @Override
    @Transactional
    public void disconnect(String userId) {
        Optional<CalendarIntegration> integrationOpt = repository.findByUserId(userId);
        if (integrationOpt.isPresent()) {
            CalendarIntegration integration = integrationOpt.get();
            if (integration.isConnected()) {
                integration.setConnected(false);
                integration.setDisconnectedAt(Instant.now());
                repository.save(integration);
            }
        }
    }
}
