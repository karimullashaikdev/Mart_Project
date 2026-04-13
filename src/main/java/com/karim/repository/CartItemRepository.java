package com.karim.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.karim.entity.CartItem;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	/**
	 * Find a non-deleted item inside a cart by product. Used for "already in cart?"
	 * guard and quantity updates.
	 */
	@Query("""
			SELECT ci FROM CartItem ci
			 WHERE ci.cart.id      = :cartId
			   AND ci.product.id   = :productId
			   AND ci.isDeleted    = false
			""")
	Optional<CartItem> findActiveItemByCartAndProduct(@Param("cartId") UUID cartId, @Param("productId") UUID productId);

	/**
	 * Count non-deleted items in a cart — used to enforce a max-items guard.
	 */
	@Query("""
			SELECT COUNT(ci) FROM CartItem ci
			 WHERE ci.cart.id   = :cartId
			   AND ci.isDeleted = false
			""")
	long countActiveItemsByCart(@Param("cartId") UUID cartId);

	/**
	 * Soft-delete every item in the cart (bulk, no individual saves needed).
	 */
	@Modifying
	@Query("""
			UPDATE CartItem ci
			   SET ci.isDeleted = true,
			       ci.deletedAt = :now,
			       ci.deletedBy = :actorId
			 WHERE ci.cart.id   = :cartId
			   AND ci.isDeleted = false
			""")
	void softDeleteAllByCartId(@Param("cartId") UUID cartId, @Param("actorId") UUID actorId,
			@Param("now") LocalDateTime now);

	@Query("""
			    SELECT ci
			    FROM CartItem ci
			    WHERE ci.cart.id = :cartId
			      AND ci.product.id = :productId
			""")
	Optional<CartItem> findByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);
}
