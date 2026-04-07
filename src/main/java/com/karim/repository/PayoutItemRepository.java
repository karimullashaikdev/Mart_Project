package com.karim.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.AgentPayoutItem;

@Repository
public interface PayoutItemRepository extends JpaRepository<AgentPayoutItem, UUID> {

	// ✅ Find all items for a specific payout
	List<AgentPayoutItem> findByPayoutId(UUID payoutId);

	// ✅ Soft delete an item by ID and mark actor
	@Modifying
	@Transactional
	@Query("UPDATE AgentPayoutItem pi SET pi.isDeleted = true, pi.deletedBy = :actorId WHERE pi.id = :id")
	void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);

	// ✅ Bulk create is handled by saveAll() from JpaRepository
}
