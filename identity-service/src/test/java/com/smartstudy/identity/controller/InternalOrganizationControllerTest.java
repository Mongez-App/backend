package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.response.InternalOrganizationSummaryResponse;
import com.smartstudy.identity.model.Organization;
import com.smartstudy.identity.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * planning-service copies the name it gets back onto a team permanently, so the
 * two behaviours that matter here are that a hit carries the name and that a
 * miss is an empty result rather than an error.
 */
@ExtendWith(MockitoExtension.class)
class InternalOrganizationControllerTest {

    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private InternalOrganizationController controller;

    private static Organization organization(String id, String name) {
        return Organization.builder().id(id).name(name).build();
    }

    @Test
    void lookup_returnsIdAndName() {
        when(organizationRepository.findAllById(List.of("org-1")))
                .thenReturn(List.of(organization("org-1", "Acme University")));

        List<InternalOrganizationSummaryResponse> found = controller.lookup(List.of("org-1"));

        assertEquals(1, found.size());
        assertEquals("org-1", found.getFirst().id());
        assertEquals("Acme University", found.getFirst().name());
    }

    @Test
    void lookup_unknownIdIsAbsentRatherThanAnError() {
        when(organizationRepository.findAllById(List.of("missing"))).thenReturn(List.of());

        assertTrue(controller.lookup(List.of("missing")).isEmpty());
    }

    @Test
    void lookup_capsTheNumberOfIdsItWillQuery() {
        when(organizationRepository.findAllById(anyIterable())).thenReturn(List.of());

        controller.lookup(IntStream.range(0, 600).mapToObj(i -> "org-" + i).toList());

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        verify(organizationRepository).findAllById(captor.capture());
        assertEquals(500, captor.getValue().size());
    }
}
