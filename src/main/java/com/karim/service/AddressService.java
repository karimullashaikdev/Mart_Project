package com.karim.service;

import java.util.List;
import java.util.UUID;

import com.karim.dto.AddressResponseDto;
import com.karim.dto.CreateAddressDto;
import com.karim.dto.UpdateAddressDto;
import com.karim.entity.Address;

public interface AddressService {

    AddressResponseDto addAddress(UUID userId, CreateAddressDto dto, UUID actorId);

    AddressResponseDto getAddress(UUID addressId, UUID userId);

    List<AddressResponseDto> getAddresses(UUID userId);

    AddressResponseDto updateAddress(UUID addressId, UUID userId, UpdateAddressDto dto, UUID actorId);

    void setDefaultAddress(UUID addressId, UUID userId, UUID actorId);

    void softDeleteAddress(UUID addressId, UUID userId, UUID actorId);
}
