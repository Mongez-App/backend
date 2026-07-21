package com.smartstudy.identity.repository;

import com.smartstudy.identity.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
}
