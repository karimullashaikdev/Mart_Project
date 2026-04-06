package com.karim.repository;

import java.util.UUID;

import org.hibernate.query.assignment.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

//    // ✅ findById
//    Optional<Assignment> findById(UUID id);
//
//    // ✅ findByOrder
//    Optional<Assignment> findByOrderId(UUID orderId);
//
//    // ✅ findByAgent with pagination (filters can be added later via Specification/Criteria)
//    @Query("SELECT a FROM Assignment a WHERE a.agentId = :agentId")
//    org.springframework.data.domain.Page<Assignment> findByAgentId(
//            @Param("agentId") UUID agentId,
//            org.springframework.data.domain.Pageable pageable
//    );
//
//    // ✅ create(data) → handled by save()
//
//    // ✅ update(id, data) → handled by save()
//
//    // ❌ softDelete(id, actorId) → typically handled in service layer
}
