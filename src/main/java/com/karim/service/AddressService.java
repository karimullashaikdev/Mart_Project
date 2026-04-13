package com.karim.service;

import java.util.List;
import java.util.UUID;

import com.karim.dto.AddressResponseDto;
import com.karim.dto.CreateAddressDto;
import com.karim.dto.UpdateAddressDto;

public interface AddressService {

    /**
     * Creates a new address for a specific user.
     */
    AddressResponseDto addAddress(UUID userId, CreateAddressDto dto, UUID actorId);

    /**
     * Fetches a specific address, ensuring it belongs to the requesting user.
     */
    AddressResponseDto getAddress(UUID addressId, UUID userId);

    /**
     * Lists all active (non-deleted) addresses for a user.
     */
    List<AddressResponseDto> getAddresses(UUID userId);

    /**
     * Updates an address using UpdateAddressDto.
     */
    AddressResponseDto updateAddress(UUID addressId, UUID userId, UpdateAddressDto dto, UUID actorId);

    /**
     * Overloaded update method to handle CreateAddressDto 
     * (Matches the call used in your Controller's PatchMapping).
     */
    AddressResponseDto updateAddress(UUID userId, UUID addressId, CreateAddressDto dto, UUID actorId);

    /**
     * Sets a specific address as the default for the user.
     */
    void setDefaultAddress(UUID addressId, UUID userId, UUID actorId);

    /**
     * Standard soft-delete method name used in your earlier logic.
     */
    void softDeleteAddress(UUID addressId, UUID userId, UUID actorId);

    /**
     * Alias for softDeleteAddress to match the "deleteAddress" call 
     * currently in your UserController.
     */
    default void deleteAddress(UUID userId, UUID addressId, UUID actorId) {
        softDeleteAddress(addressId, userId, actorId);
    }
}