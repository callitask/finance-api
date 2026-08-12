package com.treishvaam.financeapi.userpreferences.repository;

import com.treishvaam.financeapi.userpreferences.model.UserPreferences;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    Optional<UserPreferences> findByUserSubAndTenantId(String userSub, String tenantId);
}
