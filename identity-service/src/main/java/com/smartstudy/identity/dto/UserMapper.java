package com.smartstudy.identity.dto;

import com.smartstudy.identity.dto.response.HandshakeResponse;
import com.smartstudy.identity.dto.response.ProfileDetailsResponse;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import com.smartstudy.identity.dto.response.UserResponse;
import com.smartstudy.identity.model.User;
import com.smartstudy.identity.util.FieldMappingUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "appearance", source = "appearance", qualifiedByName = "appearanceToContract")
    @Mapping(target = "language", source = "language", qualifiedByName = "languageToContract")
    @Mapping(target = "calendarSyncConnected", source = "calendarConnected")
    @Mapping(target = "stats", source = "user", qualifiedByName = "toStats")
    HandshakeResponse toHandshakeResponse(User user);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "stats", source = "stats")
    ProfileResponse toProfileResponse(User user, ProfileStatsResponse stats);

    @Mapping(target = "appearance", source = "appearance", qualifiedByName = "appearanceToContract")
    @Mapping(target = "language", source = "language", qualifiedByName = "languageToContract")
    ProfileDetailsResponse toProfileDetailsResponse(User user);

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "isGuest", source = "guest")
    UserResponse toUserResponse(User user);

    @Named("appearanceToContract")
    default String appearanceToContract(String internalValue) {
        return FieldMappingUtil.appearanceToContract(internalValue);
    }

    @Named("languageToContract")
    default String languageToContract(String internalValue) {
        return FieldMappingUtil.languageToContract(internalValue);
    }

    @Named("toStats")
    default ProfileStatsResponse toStats(User user) {
        return new ProfileStatsResponse(
                user.getTotalStudyHours(),
                user.getCompletedTasksCount(),
                user.getCurrentStreakDays()
        );
    }
}
