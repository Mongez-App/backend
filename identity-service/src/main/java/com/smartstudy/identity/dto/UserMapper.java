package com.smartstudy.identity.dto;

import com.smartstudy.identity.dto.response.HandshakeResponse;
import com.smartstudy.identity.dto.response.ProfileDetailsResponse;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import com.smartstudy.identity.dto.response.UserResponse;
import com.smartstudy.identity.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "isNewUser", source = "isNewUser")
    HandshakeResponse toHandshakeResponse(User user, boolean isNewUser);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "stats", source = "stats")
    ProfileResponse toProfileResponse(User user, ProfileStatsResponse stats);

    ProfileDetailsResponse toProfileDetailsResponse(User user);

    UserResponse toUserResponse(User user);
}
