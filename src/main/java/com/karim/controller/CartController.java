package com.karim.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karim.annotation.CurrentUser;
import com.karim.dto.AddToCartRequest;
import com.karim.dto.ApiResponse;
import com.karim.dto.ApplyCouponRequest;
import com.karim.dto.CartResponse;
import com.karim.dto.UpdateCartItemRequest;
import com.karim.service.CartService;
import com.karim.service.impl.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

	private final CartService cartService;

	@GetMapping
	@PreAuthorize("hasRole('CLIENT')")
	public ResponseEntity<ApiResponse<CartResponse>> getOrCreateCart(@CurrentUser UserPrincipal principal) {

		CartResponse cart = cartService.getOrCreateCart(principal.getId(), principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Cart fetched successfully", cart));
	}

	@PostMapping("/items")
	@PreAuthorize("hasRole('CLIENT')")
	public ResponseEntity<ApiResponse<CartResponse>> addItem(@CurrentUser UserPrincipal principal,
			@Valid @RequestBody AddToCartRequest request) {

		CartResponse cart = cartService.addItem(principal.getId(), request, principal.getId());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Item added to cart", cart));
	}

	@PatchMapping("/items/{itemId}")
	@PreAuthorize("hasRole('CLIENT')")
	public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(@CurrentUser UserPrincipal principal,
			@PathVariable UUID itemId, @Valid @RequestBody UpdateCartItemRequest request) {

		CartResponse cart = cartService.updateItemQuantity(principal.getId(), itemId, request, principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Cart updated", cart));
	}

	@DeleteMapping("/items/{itemId}")
	@PreAuthorize("hasRole('CLIENT')")
	public ResponseEntity<ApiResponse<CartResponse>> removeItem(@CurrentUser UserPrincipal principal,
			@PathVariable UUID itemId) {

		CartResponse cart = cartService.removeItem(principal.getId(), itemId, principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
	}

	@PostMapping("/coupon")
	@PreAuthorize("hasRole('CLIENT')")
	public ResponseEntity<ApiResponse<CartResponse>> applyCoupon(@CurrentUser UserPrincipal principal,
			@Valid @RequestBody ApplyCouponRequest request) {

		CartResponse cart = cartService.applyCoupon(principal.getId(), request.getCouponCode(), principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Coupon applied", cart));
	}

	@DeleteMapping("/coupon")
	@PreAuthorize("hasRole('CLIENT')")
	public ResponseEntity<ApiResponse<CartResponse>> removeCoupon(@CurrentUser UserPrincipal principal) {

		CartResponse cart = cartService.removeCoupon(principal.getId(), principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Coupon removed", cart));
	}

	@DeleteMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<Void>> deleteCart(@RequestParam UUID userId,
			@CurrentUser UserPrincipal principal) {

		cartService.deleteCart(userId, principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Cart deleted", null));
	}
}