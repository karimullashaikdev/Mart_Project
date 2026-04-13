package com.karim.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.AddToCartRequest;
import com.karim.dto.CartItemResponse;
import com.karim.dto.CartResponse;
import com.karim.dto.CartSummaryResponse;
import com.karim.dto.UpdateCartItemRequest;
import com.karim.entity.Cart;
import com.karim.entity.CartItem;
import com.karim.entity.Product;
import com.karim.entity.Stock;
import com.karim.entity.User;
import com.karim.exception.CartEmptyException;
import com.karim.exception.CartItemLimitExceededException;
import com.karim.exception.CartItemNotFoundException;
import com.karim.exception.CartNotFoundException;
import com.karim.exception.InvalidCouponException;
import com.karim.exception.ProductOutOfStockException;
import com.karim.repository.CartItemRepository;
import com.karim.repository.CartRepository;
import com.karim.repository.ProductRepository;
import com.karim.repository.StockRepository;
import com.karim.repository.UserRepository;
import com.karim.service.CartService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

	private static final int MAX_CART_ITEMS = 50;

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final StockRepository stockRepository;
	private final UserRepository userRepository;

	@Override
	public CartResponse getOrCreateCart(UUID userId, UUID actorId) {
		return cartRepository.findActiveCartByUserId(userId)
				.map(this::toCartResponse)
				.orElseGet(() -> {
					User user = userRepository.findById(userId)
							.orElseThrow(() -> new RuntimeException("User not found: " + userId));

					Cart cart = Cart.builder()
							.user(user)
							.createdBy(actorId)
							.updatedBy(actorId)
							.build();

					Cart saved = cartRepository.save(cart);
					return toCartResponse(saved);
				});
	}

	@Override
	@Transactional(readOnly = true)
	public CartResponse getCart(UUID userId) {
		Cart cart = findActiveCartOrThrow(userId);
		return toCartResponse(cart);
	}

	@Override
	public CartResponse addItem(UUID userId, AddToCartRequest request, UUID actorId) {
		Cart cart = findOrCreateRawCart(userId, actorId);

		Product product = productRepository.findById(request.getProductId())
				.filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()) && Boolean.TRUE.equals(p.getIsActive()))
				.orElseThrow(() -> new RuntimeException("Product not found or inactive: " + request.getProductId()));

		Stock stock = findActiveStockOrThrow(product.getId());
		int availableQty = getAvailableQty(stock);

		if (availableQty <= 0) {
			throw new ProductOutOfStockException(product.getId());
		}

		Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(
				cart.getId(),
				product.getId()
		);

		if (existingItem.isPresent()) {
			CartItem item = existingItem.get();

			// revive soft-deleted row instead of inserting duplicate row
			if (Boolean.TRUE.equals(item.getIsDeleted())) {

				if (request.getQuantity() > availableQty) {
					throw new ProductOutOfStockException(product.getId(), request.getQuantity(), availableQty);
				}

				item.setIsDeleted(false);
				item.setDeletedAt(null);
				item.setDeletedBy(null);
				item.setQuantity(request.getQuantity());
				item.setUnitPrice(BigDecimal.valueOf(product.getSellingPrice()));
				item.setUpdatedBy(actorId);

				cartItemRepository.save(item);

			} else {
				int newQty = item.getQuantity() + request.getQuantity();

				if (newQty > availableQty) {
					throw new ProductOutOfStockException(product.getId(), newQty, availableQty);
				}

				item.setQuantity(newQty);
				item.setUnitPrice(BigDecimal.valueOf(product.getSellingPrice()));
				item.setUpdatedBy(actorId);

				cartItemRepository.save(item);
			}

		} else {
			long currentCount = cartItemRepository.countActiveItemsByCart(cart.getId());
			if (currentCount >= MAX_CART_ITEMS) {
				throw new CartItemLimitExceededException();
			}

			if (request.getQuantity() > availableQty) {
				throw new ProductOutOfStockException(product.getId(), request.getQuantity(), availableQty);
			}

			CartItem newItem = CartItem.builder()
					.cart(cart)
					.product(product)
					.quantity(request.getQuantity())
					.unitPrice(BigDecimal.valueOf(product.getSellingPrice()))
					.createdBy(actorId)
					.updatedBy(actorId)
					.build();

			cartItemRepository.save(newItem);
		}

		Cart updated = findActiveCartOrThrow(userId);
		return toCartResponse(updated);
	}

	@Override
	public CartResponse updateItemQuantity(UUID userId, UUID itemId, UpdateCartItemRequest request, UUID actorId) {
		Cart cart = findActiveCartOrThrow(userId);

		CartItem item = cartItemRepository.findById(itemId)
				.filter(i -> i.getCart().getId().equals(cart.getId()) && !Boolean.TRUE.equals(i.getIsDeleted()))
				.orElseThrow(() -> new CartItemNotFoundException(itemId));

		Stock stock = findActiveStockOrThrow(item.getProduct().getId());
		int availableQty = getAvailableQty(stock);

		if (request.getQuantity() > availableQty) {
			throw new ProductOutOfStockException(item.getProduct().getId(), request.getQuantity(), availableQty);
		}

		item.setQuantity(request.getQuantity());
		item.setUpdatedBy(actorId);
		cartItemRepository.save(item);

		Cart updated = findActiveCartOrThrow(userId);
		return toCartResponse(updated);
	}

	@Override
	public CartResponse removeItem(UUID userId, UUID itemId, UUID actorId) {
		Cart cart = findActiveCartOrThrow(userId);

		CartItem item = cartItemRepository.findById(itemId)
				.filter(i -> i.getCart().getId().equals(cart.getId()) && !Boolean.TRUE.equals(i.getIsDeleted()))
				.orElseThrow(() -> new CartItemNotFoundException(itemId));

		item.softDelete(actorId);
		item.setUpdatedBy(actorId);
		cartItemRepository.save(item);

		Cart updated = findActiveCartOrThrow(userId);
		return toCartResponse(updated);
	}

	@Override
	public CartResponse applyCoupon(UUID userId, String couponCode, UUID actorId) {
		Cart cart = findActiveCartOrThrow(userId);

		if (couponCode == null || couponCode.isBlank()) {
			throw new InvalidCouponException(couponCode);
		}

		cart.setCouponCode(couponCode.trim().toUpperCase());
		cart.setUpdatedBy(actorId);
		cartRepository.save(cart);

		return toCartResponse(cart);
	}

	@Override
	public CartResponse removeCoupon(UUID userId, UUID actorId) {
		Cart cart = findActiveCartOrThrow(userId);
		cart.setCouponCode(null);
		cart.setUpdatedBy(actorId);
		cartRepository.save(cart);
		return toCartResponse(cart);
	}

	@Override
	public void clearCart(UUID userId, UUID actorId) {
		cartRepository.findActiveCartByUserId(userId).ifPresent(cart -> {
			cartItemRepository.softDeleteAllByCartId(cart.getId(), actorId, LocalDateTime.now());
			cart.setCouponCode(null);
			cart.setUpdatedBy(actorId);
			cartRepository.save(cart);
		});
	}

	@Override
	@Transactional(readOnly = true)
	public CartSummaryResponse getCartSummary(UUID userId) {
		Cart cart = findActiveCartOrThrow(userId);

		List<CartItem> activeItems = cart.getItems().stream()
				.filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
				.collect(Collectors.toList());

		if (activeItems.isEmpty()) {
			throw new CartEmptyException();
		}

		CartSummaryResponse summary = new CartSummaryResponse();
		summary.setCartId(cart.getId());
		summary.setCouponCode(cart.getCouponCode());
		summary.setSubtotal(cart.getSubtotal());
		summary.setItems(activeItems.stream().map(this::toCartItemResponse).collect(Collectors.toList()));
		return summary;
	}

	@Override
	public void deleteCart(UUID userId, UUID actorId) {
		Cart cart = findActiveCartOrThrow(userId);
		cartItemRepository.softDeleteAllByCartId(cart.getId(), actorId, LocalDateTime.now());
		cart.softDelete(actorId);
		cart.setUpdatedBy(actorId);
		cartRepository.save(cart);
	}

	private Cart findActiveCartOrThrow(UUID userId) {
		return cartRepository.findActiveCartByUserId(userId)
				.orElseThrow(() -> new CartNotFoundException(userId));
	}

	private Cart findOrCreateRawCart(UUID userId, UUID actorId) {
		return cartRepository.findActiveCartByUserId(userId).orElseGet(() -> {
			User user = userRepository.findById(userId)
					.orElseThrow(() -> new RuntimeException("User not found: " + userId));

			Cart cart = Cart.builder()
					.user(user)
					.createdBy(actorId)
					.updatedBy(actorId)
					.build();

			return cartRepository.save(cart);
		});
	}

	private Stock findActiveStockOrThrow(UUID productId) {
		return stockRepository.findActiveByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Active stock not found for product: " + productId));
	}

	private int getAvailableQty(Stock stock) {
		return stock.getQuantityAvailable() != null ? stock.getQuantityAvailable() : 0;
	}

	private int getReservedQty(Stock stock) {
		return stock.getQuantityReserved() != null ? stock.getQuantityReserved() : 0;
	}

	private CartResponse toCartResponse(Cart cart) {
		List<CartItem> activeItems = cart.getItems().stream()
				.filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
				.collect(Collectors.toList());

		CartResponse response = new CartResponse();
		response.setId(cart.getId());
		response.setUserId(cart.getUser().getId());
		response.setCouponCode(cart.getCouponCode());
		response.setSubtotal(cart.getSubtotal());
		response.setItemCount(
				activeItems.stream()
						.mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0)
						.sum()
		);
		response.setCreatedAt(cart.getCreatedAt());
		response.setUpdatedAt(cart.getUpdatedAt());
		response.setItems(activeItems.stream().map(this::toCartItemResponse).collect(Collectors.toList()));

		return response;
	}

	private CartItemResponse toCartItemResponse(CartItem item) {
		Product product = item.getProduct();
		Stock stock = findActiveStockOrThrow(product.getId());

		int availableQty = getAvailableQty(stock);
		int reservedQty = getReservedQty(stock);

		CartItemResponse response = new CartItemResponse();
		response.setId(item.getId());
		response.setProductId(product.getId());
		response.setProductName(product.getName());
		response.setProductImageUrl(
				product.getImages() != null && !product.getImages().isEmpty()
						? product.getImages().get(0)
						: null
		);
		response.setUnit(product.getUnit() != null ? product.getUnit().name() : null);
		response.setUnitValue(product.getUnitValue());
		response.setQuantity(item.getQuantity());
		response.setUnitPrice(item.getUnitPrice());
		response.setLineTotal(item.getLineTotal());
		response.setAddedAt(item.getCreatedAt());

		response.setAvailableQuantity(availableQty);
		response.setQuantityReserved(reservedQty);
		response.setInStock(availableQty > 0);

		return response;
	}
}