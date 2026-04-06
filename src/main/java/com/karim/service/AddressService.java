package com.karim.service;

import java.util.UUID;

import com.karim.dto.CreateAddressDto;
import com.karim.dto.UpdateAddressDto;
import com.karim.entity.Address;

public interface AddressService {

	Address addAddress(UUID userId, CreateAddressDto dto, UUID actorId);

	Address getAddress(UUID addressId, UUID userId);

	Address updateAddress(UUID addressId, UUID userId, UpdateAddressDto dto, UUID actorId);
	
	void setDefaultAddress(UUID addressId, UUID userId, UUID actorId);
	
	void softDeleteAddress(UUID addressId, UUID userId, UUID actorId);
}
