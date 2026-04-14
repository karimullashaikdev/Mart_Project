package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Order;
import com.karim.enums.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	// ✅ findById
	Optional<Order> findById(UUID id);

	@Query("SELECT o FROM Order o WHERE o.status IN :statuses AND (o.isDeleted = false OR o.isDeleted IS NULL)")
	List<Order> findByStatusIn(@Param("statuses") List<OrderStatus> statuses);

	// ✅ findByNumber
	Optional<Order> findByOrderNumber(String orderNumber);

	// ✅ findByUser with pagination
	@Query("""
			    SELECT o FROM Order o
			    WHERE o.user.id = :userId
			""")
	Page<Order> findByUser(@Param("userId") UUID userId, Pageable pageable);

	// ✅ listAdmin (no user filter, includes all except soft deleted handled by
	// entity)
	@Query("""
			    SELECT o FROM Order o
			""")
	Page<Order> listAdmin(Pageable pageable);

	// ✅ create(data) → save()

	// ✅ updateStatus
	@Modifying
	@Query("""
			    UPDATE Order o
			    SET o.status = :status,
			        o.updatedAt = :updatedAt
			    WHERE o.id = :id
			""")
	void updateStatus(@Param("id") UUID id, @Param("status") String status,
			@Param("updatedAt") LocalDateTime updatedAt);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.user.id = :userId
			    AND (:status IS NULL OR o.status = :status)
			    AND (:fromDate IS NULL OR o.placedAt >= :fromDate)
			    AND (:toDate IS NULL OR o.placedAt <= :toDate)
			    ORDER BY o.placedAt DESC
			""")
	Page<Order> findOrdersByUser(@Param("userId") UUID userId, @Param("status") OrderStatus status,
			@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:status IS NULL OR o.status = :status)
			    AND (:fromDate IS NULL OR o.placedAt >= :fromDate)
			    AND (:toDate IS NULL OR o.placedAt <= :toDate)
			    ORDER BY o.placedAt DESC
			""")
	Page<Order> listOrdersAdmin(@Param("status") OrderStatus status, @Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate, Pageable pageable);
	
	
	List<Order> findByStatus(OrderStatus status);

	@Query("""
		SELECT DISTINCT o
		FROM Order o
		LEFT JOIN FETCH o.user
		LEFT JOIN FETCH o.address
		LEFT JOIN FETCH o.orderItems oi
		LEFT JOIN FETCH oi.product
		WHERE o.status = :status
	""")
	List<Order> findByStatusWithDetails(@Param("status") OrderStatus status);
}
