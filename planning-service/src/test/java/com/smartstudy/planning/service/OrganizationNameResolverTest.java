package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.dto.response.OrganizationSummaryData;
import com.smartstudy.planning.exception.OrganizationLookupUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The organization name is copied onto a team once, at creation, and never
 * revisited — so the distinction this resolver draws between "identity-service
 * says there is no such organization" and "identity-service did not answer"
 * decides whether a permanent null gets written.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationNameResolverTest {

    @Mock private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private OrganizationNameResolver resolver;

    private static final String ORG_ID = "org-1";

    @Test
    void resolve_returnsTheName() {
        when(identityServiceClient.lookupOrganizations(ORG_ID, List.of(ORG_ID)))
                .thenReturn(List.of(new OrganizationSummaryData(ORG_ID, "Acme University")));

        assertEquals("Acme University", resolver.resolve(ORG_ID));
    }

    @Test
    void resolve_emptyResultIsAnAnswer_notAFailure() {
        // A 200 with nothing in it means the uid is not an organization account,
        // which is the normal case for a user-created team.
        when(identityServiceClient.lookupOrganizations(anyString(), anyList())).thenReturn(List.of());

        assertNull(resolver.resolve(ORG_ID));
    }

    @Test
    void resolve_treatsABlankNameAsUnset() {
        when(identityServiceClient.lookupOrganizations(anyString(), anyList()))
                .thenReturn(List.of(new OrganizationSummaryData(ORG_ID, "   ")));

        assertNull(resolver.resolve(ORG_ID));
    }

    @Test
    void resolve_propagatesTheUnavailableSignalFromTheFallback() {
        when(identityServiceClient.lookupOrganizations(anyString(), anyList()))
                .thenThrow(new OrganizationLookupUnavailableException("identity-service is unreachable.", null));

        assertThrows(OrganizationLookupUnavailableException.class, () -> resolver.resolve(ORG_ID));
    }
}
