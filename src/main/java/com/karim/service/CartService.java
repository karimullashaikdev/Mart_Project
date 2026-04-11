package com.karim.service;


import java.util.UUID;

import com.karim.dto.AddToCartRequest;
import com.karim.dto.CartResponse;
import com.karim.dto.CartSummaryResponse;
import com.karim.dto.UpdateCartItemRequest;

/**
 * CartService – mandatory prerequisite to OrderService.
 *
 * Lifecycle: 1. getOrCreateCart → returns (or lazily creates) the user's active
 * cart 2. addItem / updateItemQuantity / removeItem → mutate line-items 3.
 * applyCoupon / removeCoupon → coupon management 4. getCart / getCartSummary →
 * read operations 5. clearCart → removes all items (called by OrderService
 * after checkout) 6. deleteCart → full soft-delete (e.g. admin operation)
 *
 * All write methods accept an {@code actorId} for audit / soft-delete trails.
 */
public interface CartService {

	/**
	 * Fetch the active cart for a user, creating one if it does not exist yet. This
	 * is the entry-point called before any item mutation.
	 *
	 * @param userId  owner
	 * @param actorId who is performing the action (equals userId for self-service)
	 */
	CartResponse getOrCreateCart(UUID userId, UUID actorId);

	/**
	 * Return the active cart without auto-creating.
	 *
	 * @throws com.supermarket.cart.exception.CartNotFoundException if no active
	 *                                                              cart exists
	 */
	CartResponse getCart(UUID userId);

	/**
	 * Add a product to the cart.
	 * <ul>
	 * <li>If the product is already in the cart, increments quantity instead.</li>
	 * <li>Validates that the product is active and in stock.</li>
	 * <li>Snapshots the current selling_price into CartItem.unitPrice.</li>
	 * </ul>
	 *
	 * @throws com.supermarket.cart.exception.ProductOutOfStockException     if
	 *                                                                       stock
	 *                                                                       is 0
	 * @throws com.supermarket.cart.exception.CartItemLimitExceededException if the
	 *                                                                       cart
	 *                                                                       already
	 *                                                                       has 50
	 *                                                                       distinct
	 *                                                                       products
	 */
	CartResponse addItem(UUID userId, AddToCartRequest request, UUID actorId);

	/**
	 * Update the quantity of an existing cart item. Passing quantity = 0 is
	 * equivalent to removeItem.
	 *
	 * @param itemId CartItem PK
	 * @throws com.supermarket.cart.exception.CartItemNotFoundException  if item is
	 *                                                                   not found
	 *                                                                   or does not
	 *                                                                   belong to
	 *                                                                   the user's
	 *                                                                   cart
	 * @throws com.supermarket.cart.exception.ProductOutOfStockException if
	 *                                                                   requested
	 *                                                                   qty exceeds
	 *                                                                   available
	 *                                                                   stock
	 */
	CartResponse updateItemQuantity(UUID userId, UUID itemId, UpdateCartItemRequest request, UUID actorId);

	/**
	 * Soft-delete a single item from the cart.
	 *
	 * @param itemId CartItem PK
	 * @throws com.supermarket.cart.exception.CartItemNotFoundException if not found
	 *                                                                  in the
	 *                                                                  user's
	 *                                                                  active cart
	 */
	CartResponse removeItem(UUID userId, UUID itemId, UUID actorId);

	/**
	 * Apply a coupon code to the cart. Coupon validation logic (expiry, minimum
	 * order value, usage limits) lives in a CouponService; CartService calls it and
	 * stores the code on success.
	 *
	 * @throws com.supermarket.cart.exception.InvalidCouponException if the coupon
	 *                                                               is invalid or
	 *                                                               expired
	 */
	CartResponse applyCoupon(UUID userId, String couponCode, UUID actorId);

	/**
	 * Remove an applied coupon from the cart.
	 */
	CartResponse removeCoupon(UUID userId, UUID actorId);

	/**
	 * Soft-delete ALL items inside the cart without deleting the cart itself.
	 * Called by OrderService immediately after a successful order placement.
	 *
	 * @param userId  owner
	 * @param actorId who triggered the clear (system / user)
	 */
	void clearCart(UUID userId, UUID actorId);

	/**
	 * Lightweight summary used by OrderService to build order lines without loading
	 * full product entities again.
	 *
	 * @throws com.supermarket.cart.exception.CartNotFoundException if no active
	 *                                                              cart exists
	 * @throws com.supermarket.cart.exception.CartEmptyException    if the cart has
	 *                                                              no items
	 */
	CartSummaryResponse getCartSummary(UUID userId);

	/**
	 * Full soft-delete of the cart and all its items (admin operation).
	 *
	 * @param userId  owner of the cart to delete
	 * @param actorId admin performing the deletion
	 */
	void deleteCart(UUID userId, UUID actorId);
}