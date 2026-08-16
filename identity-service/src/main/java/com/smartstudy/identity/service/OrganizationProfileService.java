package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.OrgProfileUpdateRequest;
import com.smartstudy.identity.dto.response.OrgProfileResponse;
import com.smartstudy.identity.dto.response.OrgProfileUpdateResponse;

public interface OrganizationProfileService {
    OrgProfileResponse getProfile(String uid);
    OrgProfileUpdateResponse updateProfile(String uid, OrgProfileUpdateRequest request);
}
