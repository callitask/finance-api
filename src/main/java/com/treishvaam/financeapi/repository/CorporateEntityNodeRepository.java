package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.CorporateEntityNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CorporateEntityNodeRepository extends JpaRepository<CorporateEntityNode, Long> {

    List<CorporateEntityNode> findByActiveTrueOrderByDisplayOrderAsc();

    List<CorporateEntityNode> findByTenantIdInAndActiveTrueOrderByDisplayOrderAsc(
            List<String> tenantIds);

    Optional<CorporateEntityNode> findByEntityKey(String entityKey);
}
