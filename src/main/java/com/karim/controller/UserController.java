package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;

import com.karim.annotation.CurrentUser;
import com.karim.dto.AddressResponseDto;
import com.karim.dto.ApiResponse;
import com.karim.dto.CreateAddressDto;
import com.karim.dto.CreateUserProfileDto;
import com.karim.dto.UpdateAddressDto;
import com.karim.dto.UpdateProfileDto;
import com.karim.dto.UpdateUserDto;
import com.karim.dto.UserFilterDto;
import com.karim.dto.UserProfileDto;
import com.karim.entity.User;
import com.karim.entity.UserProfile;
import com.karim.service.AddressService;
import com.karim.service.UserService;
import com.karim.service.impl.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User", description = "User profile, account, and address management APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

	private final UserService userService;
	private final AddressService addressService;

	public UserController(UserService userService, AddressService addressService) {
		this.userService = userService;
		this.addressService = addressService;
	}

	// ─────────────────────────────────────────────────────────────────────────────
	// CURRENT USER (any authenticated user)
	// ─────────────────────────────────────────────────────────────────────────────

	@GetMapping("/me")
	@Operation(summary = "Get current user", description = "Returns the full account details of the currently authenticated user. "
			+ "Only non-deleted users are returned.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = User.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised – JWT missing or invalid"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found") })
	public ResponseEntity<ApiResponse<User>> getMe(@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		User user = userService.getUser(principal.getId());
		return ResponseEntity.ok(ApiResponse.success(user));
	}

	@PatchMapping("/me")
	@Operation(summary = "Update current user", description = "Partially updates the authenticated user's account fields "
			+ "(full_name, email, phone, is_active). Only non-null fields in the "
			+ "request body are applied. The actorId is resolved from the JWT.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated successfully"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found") })
	public ResponseEntity<ApiResponse<User>> updateMe(@Parameter(hidden = true) @CurrentUser UserPrincipal principal,
			@Valid @RequestBody UpdateUserDto dto) {

		User updated = userService.updateUser(principal.getId(), dto, principal.getId());
		return ResponseEntity.ok(ApiResponse.success(updated));
	}

	// ─────────────────────────────────────────────────────────────────────────────
	// PROFILE
	// ─────────────────────────────────────────────────────────────────────────────

	@GetMapping("/me/profile")
	public ResponseEntity<ApiResponse<UserProfileDto>> getMyProfile(
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		return userService.getProfile(principal.getId())
				.map(profile -> ResponseEntity.ok(ApiResponse.success(profile)))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(ApiResponse.failure("Profile not found")));
	}

	@PostMapping("/me/profile")
	public ResponseEntity<ApiResponse<UserProfileDto>> createOrUpdateProfile(
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal,
			@Valid @RequestBody CreateUserProfileDto dto) {

		UserProfile profile = userService.createOrUpdateProfile(
				principal.getId(), dto, principal.getId());

		return ResponseEntity.ok(ApiResponse.success(mapProfileToDto(profile)));
	}

	@PatchMapping("/me/profile")
	public ResponseEntity<ApiResponse<UserProfileDto>> updateProfile(
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal,
			@Valid @RequestBody UpdateProfileDto dto) {

		UserProfile profile = userService.updateProfile(
				principal.getId(), dto, principal.getId());

		return ResponseEntity.ok(ApiResponse.success(mapProfileToDto(profile)));
	}

	@DeleteMapping("/me/profile")
	public ResponseEntity<Void> softDeleteProfile(
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		userService.softDeleteProfile(principal.getId(), principal.getId());
		return ResponseEntity.noContent().build();
	}

	// ─────────────────────────────────────────────────────────────────────────────
	// ADDRESSES
	// ─────────────────────────────────────────────────────────────────────────────

	@GetMapping("/me/addresses")
	public ResponseEntity<ApiResponse<List<AddressResponseDto>>> getMyAddresses(
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		List<AddressResponseDto> addresses = addressService.getAddresses(principal.getId());
		return ResponseEntity.ok(ApiResponse.success(addresses));
	}

	@PostMapping("/me/addresses")
	public ResponseEntity<ApiResponse<AddressResponseDto>> addAddress(
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal,
			@Valid @RequestBody CreateAddressDto dto) {

		AddressResponseDto address = addressService.addAddress(principal.getId(), dto, principal.getId());

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(address));
	}

	@GetMapping("/me/addresses/{addressId}")
	public ResponseEntity<ApiResponse<AddressResponseDto>> getAddress(@PathVariable UUID addressId,
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		AddressResponseDto address = addressService.getAddress(addressId, principal.getId());

		return ResponseEntity.ok(ApiResponse.success(address));
	}

	@PatchMapping("/me/addresses/{addressId}")
	public ResponseEntity<ApiResponse<AddressResponseDto>> updateAddress(@PathVariable UUID addressId,
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal,
			@Valid @RequestBody UpdateAddressDto dto) {

		AddressResponseDto address = addressService.updateAddress(addressId, principal.getId(), dto, principal.getId());

		return ResponseEntity.ok(ApiResponse.success(address));
	}

	@PatchMapping("/me/addresses/{addressId}/default")
	public ResponseEntity<ApiResponse<String>> setDefaultAddress(@PathVariable UUID addressId,
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		addressService.setDefaultAddress(addressId, principal.getId(), principal.getId());
		return ResponseEntity.ok(ApiResponse.success("Default address updated successfully"));
	}

	@DeleteMapping("/me/addresses/{addressId}")
	public ResponseEntity<Void> softDeleteAddress(@PathVariable UUID addressId,
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		addressService.softDeleteAddress(addressId, principal.getId(), principal.getId());
		return ResponseEntity.noContent().build();
	}

	// ─────────────────────────────────────────────────────────────────────────────
	// ADMIN – user management
	// ─────────────────────────────────────────────────────────────────────────────

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "List all users (Admin)", description = "Returns a paginated list of users with optional filters. "
			+ "Supports filtering by role, is_active, is_deleted, and a free-text "
			+ "search on full_name / email / phone. "
			+ "By default only non-deleted users are returned unless is_deleted=true is passed.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of users returned"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden – admin role required") })
	public ResponseEntity<ApiResponse<Page<User>>> listUsers(@Valid UserFilterDto filters,
			@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

		Page<User> page = userService.listUsers(filters, pageable);
		return ResponseEntity.ok(ApiResponse.success(page));
	}

	@GetMapping("/{userId}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Get a specific user by ID (Admin)", description = "Fetches a single user record by its UUID. "
			+ "Intended for admin use; returns even soft-deleted users "
			+ "depending on the repository default scope configuration.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden – admin role required"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found") })
	public ResponseEntity<ApiResponse<User>> getUserById(
			@Parameter(description = "UUID of the target user", required = true) @PathVariable UUID userId) {

		User user = userService.getUser(userId);
		return ResponseEntity.ok(ApiResponse.success(user));
	}

	@PatchMapping("/{userId}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Update a specific user (Admin)", description = "Allows an admin to partially update any user's account fields "
			+ "(full_name, email, phone, is_active). "
			+ "The actorId recorded in the audit trail is the admin's own ID, "
			+ "resolved from the JWT.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden – admin role required"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found") })
	public ResponseEntity<ApiResponse<User>> updateUser(
			@Parameter(description = "UUID of the target user", required = true) @PathVariable UUID userId,
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal,
			@Valid @RequestBody UpdateUserDto dto) {

		User updated = userService.updateUser(userId, dto, principal.getId());
		return ResponseEntity.ok(ApiResponse.success(updated));
	}

	@PatchMapping("/{userId}/restore")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Restore a soft-deleted user (Admin)", description = "Reverses a soft-delete by setting is_deleted=false and clearing "
			+ "deleted_at / deleted_by on the user record. "
			+ "Returns 400 if the user is not currently soft-deleted.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User restored successfully"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User not found or already active"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden – admin role required") })
	public ResponseEntity<ApiResponse<String>> restoreUser(
			@Parameter(description = "UUID of the soft-deleted user to restore", required = true) @PathVariable UUID userId,
			@Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

		userService.restoreUser(userId, principal.getId());
		return ResponseEntity.ok(ApiResponse.success("User restored successfully"));
	}

	private UserProfileDto mapProfileToDto(UserProfile profile) {
		User user = profile.getUser();

		UserProfileDto dto = new UserProfileDto();
		dto.setUserId(user.getId());
		dto.setFullName(user.getFullName());
		dto.setEmail(user.getEmail());
		dto.setPhone(user.getPhone());
		dto.setRole(user.getRole());
		dto.setIsActive(user.getIsActive());
		dto.setAvatarUrl(profile.getAvatarUrl());
		dto.setDateOfBirth(profile.getDateOfBirth());
		dto.setGender(profile.getGender());

		return dto;
	}
}