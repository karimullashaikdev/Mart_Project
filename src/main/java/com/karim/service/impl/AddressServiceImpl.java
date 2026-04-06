package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.CreateAddressDto;
import com.karim.dto.UpdateAddressDto;
import com.karim.entity.Address;
import com.karim.entity.User;
import com.karim.exception.AddressNotFoundException;
import com.karim.exception.ResourceNotFoundException;
import com.karim.repository.AddressRepository;
import com.karim.repository.UserRepository;
import com.karim.service.AddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

	private final AddressRepository addressRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public Address addAddress(UUID userId, CreateAddressDto dto, UUID actorId) {

		// 1. Validate user exists
		User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		// 2. If new address is default → unset existing defaults
		if (Boolean.TRUE.equals(dto.getIsDefault())) {

			List<Address> existingAddresses = addressRepository.findByUserId(userId);

			for (Address addr : existingAddresses) {
				if (Boolean.TRUE.equals(addr.getIsDefault())) {
					addr.setIsDefault(false);
					addr.setUpdatedBy(actorId);
				}
			}
		}

		// 3. Create new address
		Address address = new Address();
		address.setUser(user);
		address.setLabel(dto.getLabel());
		address.setLine1(dto.getLine1());
		address.setLine2(dto.getLine2());
		address.setCity(dto.getCity());
		address.setState(dto.getState());
		address.setPincode(dto.getPincode());
		address.setLatitude(dto.getLatitude());
		address.setLongitude(dto.getLongitude());
		address.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);

		// 4. Audit fields
		address.setCreatedBy(actorId);
		address.setUpdatedBy(actorId);

		// 5. Save new address
		return addressRepository.save(address);
	}

	@Override
	public Address getAddress(UUID addressId, UUID userId) {

		// 1. Fetch address
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		// 2. Verify ownership
		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this address");
		}

		return address;
	}

	@Override
	@Transactional
	public Address updateAddress(UUID addressId, UUID userId, UpdateAddressDto dto, UUID actorId) {

		// 1. Fetch address with ownership check
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this address");
		}

		// 2. Handle isDefault logic
		if (Boolean.TRUE.equals(dto.getIsDefault())) {

			// unset all existing defaults
			addressRepository.unsetAllDefaults(userId);

			address.setIsDefault(true);
		}

		// 3. Partial updates
		if (dto.getLabel() != null) {
			address.setLabel(dto.getLabel());
		}

		if (dto.getLine1() != null) {
			address.setLine1(dto.getLine1());
		}

		if (dto.getLine2() != null) {
			address.setLine2(dto.getLine2());
		}

		if (dto.getCity() != null) {
			address.setCity(dto.getCity());
		}

		if (dto.getState() != null) {
			address.setState(dto.getState());
		}

		if (dto.getPincode() != null) {
			address.setPincode(dto.getPincode());
		}

		if (dto.getLatitude() != null) {
			address.setLatitude(dto.getLatitude());
		}

		if (dto.getLongitude() != null) {
			address.setLongitude(dto.getLongitude());
		}

		if (dto.getIsDefault() != null && !dto.getIsDefault()) {
			address.setIsDefault(false);
		}

		// 4. Audit fields
		address.setUpdatedBy(actorId);

		// updatedAt handled by @PreUpdate

		// 5. Save
		return addressRepository.save(address);
	}

	@Override
	@Transactional
	public void setDefaultAddress(UUID addressId, UUID userId, UUID actorId) {

		// 1. Fetch address
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new RuntimeException("Address not found"));

		// 2. Ownership check
		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this address");
		}

		// 3. Unset all existing default addresses
		addressRepository.unsetAllDefaults(userId);

		// 4. Set selected address as default
		address.setIsDefault(true);

		// 5. Audit field
		address.setUpdatedBy(actorId);

		// 6. Save
		addressRepository.save(address);
	}

	@Override
	@Transactional
	public void softDeleteAddress(UUID addressId, UUID userId, UUID actorId) {

		// 1. Fetch address
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		// 2. Ownership check
		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this address");
		}

		// 3. Set audit fields
		address.setDeletedBy(actorId);
		address.setDeletedAt(LocalDateTime.now());

		// 4. Soft delete (triggers @SQLDelete)
		addressRepository.delete(address);
	}
}
