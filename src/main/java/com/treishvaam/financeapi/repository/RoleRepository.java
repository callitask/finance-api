package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.ERole;
import com.treishvaam.financeapi.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(ERole name);
}
