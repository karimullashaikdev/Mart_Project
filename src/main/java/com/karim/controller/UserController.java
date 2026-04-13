package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.karim.annotation.CurrentUser;
import com.karim.dto.*;
import com.karim.entity.User;
import com.karim.entity.UserProfile;
import com.karim.service.AddressService;
import com.karim.service.UserService;
import com.karim.service.impl.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile, account, and address management APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 1: USER SELF-SERVICE (ROLE_USER or ROLE_ADMIN)
    // All paths start with /me
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current user")
    public ResponseEntity<ApiResponse<User>> getMe(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUser(principal.getId())));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user")
    public ResponseEntity<ApiResponse<User>> updateMe(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal,
            @Valid @RequestBody UpdateUserDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUser(principal.getId(), dto, principal.getId())));
    }

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
        UserProfile profile = userService.createOrUpdateProfile(principal.getId(), dto, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(mapProfileToDto(profile)));
    }

    @DeleteMapping("/me/profile")
    public ResponseEntity<Void> softDeleteProfile(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {
        userService.softDeleteProfile(principal.getId(), principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADDRESS ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/me/addresses")
    @Operation(summary = "Get all addresses for current user")
    public ResponseEntity<ApiResponse<List<AddressResponseDto>>> getMyAddresses(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(addressService.getAddresses(principal.getId())));
    }

    @PostMapping("/me/addresses")
    @Operation(summary = "Add a new address for current user")
    public ResponseEntity<ApiResponse<AddressResponseDto>> addAddress(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateAddressDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(addressService.addAddress(principal.getId(), dto, principal.getId())));
    }

    /**
     * Update an existing address.
     * FIX: This endpoint was MISSING — frontend was getting
     *      "No static resource api/users/me/addresses/{id}" (500) on every edit/delete.
     */
    @PatchMapping("/me/addresses/{addressId}")
    @Operation(summary = "Update an existing address")
    public ResponseEntity<ApiResponse<AddressResponseDto>> updateAddress(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal,
            @PathVariable UUID addressId,
            @Valid @RequestBody CreateAddressDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                addressService.updateAddress(principal.getId(), addressId, dto, principal.getId())));
    }

    /**
     * Delete (soft-delete) an address.
     * FIX: This endpoint was MISSING — frontend was getting
     *      "No static resource api/users/me/addresses/{id}" (500) on every delete.
     */
    @DeleteMapping("/me/addresses/{addressId}")
    @Operation(summary = "Delete an address")
    public ResponseEntity<Void> deleteAddress(
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal,
            @PathVariable UUID addressId) {
        addressService.deleteAddress(principal.getId(), addressId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 2: ADMIN MANAGEMENT (ROLE_ADMIN ONLY)
    // All paths interact with specific {userId}
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (Admin)")
    public ResponseEntity<ApiResponse<Page<User>>> listUsers(
            @Valid UserFilterDto filters,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.listUsers(filters, pageable)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a specific user by ID (Admin)")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId)));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a specific user (Admin)")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @PathVariable UUID userId,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal,
            @Valid @RequestBody UpdateUserDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUser(userId, dto, principal.getId())));
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore a soft-deleted user (Admin)")
    public ResponseEntity<ApiResponse<String>> restoreUser(
            @PathVariable UUID userId,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {
        userService.restoreUser(userId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("User restored successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

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