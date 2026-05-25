/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data Access Object for the AuditLog entity. - Serves as the persistence layer for
 * tracking security events, admin actions, and L8-BCSM telemetry.
 *
 * <p>Scope: - Read/Write operations for audit logs.
 *
 * <p>Critical Dependencies: - Backend: `AuditLog`, `MerkleAuditLogService`.
 *
 * <p>Security Constraints: - Must never expose bulk-delete operations to prevent track-covering by
 * insiders.
 *
 * <p>Non-Negotiables: - Adheres to Spring Data JPA standard conventions.
 *
 * <p>Change Intent: - Support the cryptographic Merkle tree generation by providing a bounded,
 * ordered fetch method.
 *
 * <p>Future AI Guidance: - If performance degrades during Merkle generation, implement keyset
 * pagination (cursor-based) rather than removing this method.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial Repository interface. - EDITED
 * (Batch 8 - Fix Forward): • Added `findTop1000ByOrderByTimestampDesc()` method signature. • Why:
 * Resolves compilation failure `cannot find symbol` in `MerkleAuditLogService`. Fixes the
 * architecture forward.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Required for AEGIS Part-K (K2) Merkle Tree Generation
    List<AuditLog> findTop1000ByOrderByTimestampDesc();
}
