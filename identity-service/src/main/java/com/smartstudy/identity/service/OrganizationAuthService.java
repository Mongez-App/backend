package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.OrganizationUpdateRequest;
import com.smartstudy.identity.dto.response.OrganizationDataResponse;

public interface OrganizationAuthService {
    OrganizationDataResponse register(String uid, String email, String name);
    OrganizationDataResponse login(String uid);
    void logout(String uid);
    OrganizationDataResponse update(String uid, OrganizationUpdateRequest request);
}
