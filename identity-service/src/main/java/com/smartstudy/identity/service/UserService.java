package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.CreateUserRequest;
import com.smartstudy.identity.dto.request.SavePreferencesRequest;
import com.smartstudy.identity.dto.request.UpdateProfileRequest;
import com.smartstudy.identity.dto.response.PreferencesResponse;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.UpdateProfileResponse;
import com.smartstudy.identity.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    ProfileResponse getProfile(String firebaseUid);
    UpdateProfileResponse updateProfile(String firebaseUid, UpdateProfileRequest request);
    PreferencesResponse savePreferences(String firebaseUid, SavePreferencesRequest request);
    void deleteUser(String firebaseUid);
    UserResponse createUser(CreateUserRequest request);
    List<UserResponse> getAllUsers();
}
