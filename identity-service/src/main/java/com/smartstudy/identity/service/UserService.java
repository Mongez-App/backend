package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.SavePreferencesRequest;
import com.smartstudy.identity.dto.request.UpdateProfileRequest;
import com.smartstudy.identity.dto.response.PreferencesResponse;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.UpdateProfileResponse;

public interface UserService {
    ProfileResponse getProfile(String firebaseUid);
    UpdateProfileResponse updateProfile(String firebaseUid, UpdateProfileRequest request);
    PreferencesResponse savePreferences(String firebaseUid, SavePreferencesRequest request);
    void deleteUser(String firebaseUid);
}
