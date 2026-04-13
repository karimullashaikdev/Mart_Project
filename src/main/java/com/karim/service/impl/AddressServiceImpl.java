package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.AddressResponseDto;
import com.karim.dto.CreateAddressDto;
import com.karim.dto.UpdateAddressDto;
import com.karim.entity.Address;
import com.karim.entity.User;
import com.karim.enums.AddressLabel;
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

	private AddressResponseDto mapToDto(Address address) {
		return AddressResponseDto.builder().id(address.getId()).label(address.getLabel()).line1(address.getLine1())
				.line2(address.getLine2()).city(address.getCity()).state(address.getState())
				.pincode(address.getPincode()).phone(address.getPhone()).landmark(address.getLandmark())
				.latitude(address.getLatitude()).longitude(address.getLongitude()).isDefault(address.getIsDefault())
				.build();
	}

	@Override
	@Transactional
	public AddressResponseDto addAddress(UUID userId, CreateAddressDto dto, UUID actorId) {
		User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		if (Boolean.TRUE.equals(dto.getIsDefault())) {
			addressRepository.unsetAllDefaults(userId);
		}

		Address address = new Address();
		address.setUser(user);
		updateAddressFields(address, dto); // Reuse logic
		address.setCreatedBy(actorId);
		address.setUpdatedBy(actorId);
		address.setIsDeleted(false);

		return mapToDto(addressRepository.save(address));
	}

	@Override
	public AddressResponseDto getAddress(UUID addressId, UUID userId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access");
		}
		return mapToDto(address);
	}

	@Override
	public List<AddressResponseDto> getAddresses(UUID userId) {
		return addressRepository.findByUserIdAndIsDeletedFalse(userId).stream().map(this::mapToDto).toList();
	}

	/**
	 * Implementation for PatchMapping in Controller that uses CreateAddressDto
	 */
	@Override
	@Transactional
	public AddressResponseDto updateAddress(UUID userId, UUID addressId, CreateAddressDto dto, UUID actorId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access");
		}

		if (Boolean.TRUE.equals(dto.getIsDefault())) {
			addressRepository.unsetAllDefaults(userId);
		}

		updateAddressFields(address, dto);
		address.setUpdatedBy(actorId);

		return mapToDto(addressRepository.save(address));
	}

	@Override
	@Transactional
	public AddressResponseDto updateAddress(UUID addressId, UUID userId, UpdateAddressDto dto, UUID actorId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access");
		}

		if (Boolean.TRUE.equals(dto.getIsDefault())) {
			addressRepository.unsetAllDefaults(userId);
			address.setIsDefault(true);
		}

		if (dto.getLabel() != null)
			address.setLabel(dto.getLabel());
		if (dto.getLine1() != null)
			address.setLine1(dto.getLine1());
		if (dto.getLine2() != null)
			address.setLine2(dto.getLine2());
		if (dto.getCity() != null)
			address.setCity(dto.getCity());
		if (dto.getState() != null)
			address.setState(dto.getState());
		if (dto.getPincode() != null)
			address.setPincode(dto.getPincode());
		if (dto.getLatitude() != null)
			address.setLatitude(dto.getLatitude());
		if (dto.getLongitude() != null)
			address.setLongitude(dto.getLongitude());

		if (dto.getIsDefault() != null && !dto.getIsDefault()) {
			address.setIsDefault(false);
		}

		address.setUpdatedBy(actorId);
		return mapToDto(addressRepository.save(address));
	}

	@Override
	@Transactional
	public void setDefaultAddress(UUID addressId, UUID userId, UUID actorId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access");
		}

		addressRepository.unsetAllDefaults(userId);
		address.setIsDefault(true);
		address.setUpdatedBy(actorId);
		addressRepository.save(address);
	}

	@Override
	@Transactional
	public void softDeleteAddress(UUID addressId, UUID userId, UUID actorId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new AddressNotFoundException("Address not found"));

		if (!address.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access");
		}

		address.setIsDeleted(true);
		address.setDeletedAt(LocalDateTime.now());
		address.setDeletedBy(actorId);
		addressRepository.save(address);
	}

	// Helper to map CreateAddressDto to Entity
	private void updateAddressFields(Address address, CreateAddressDto dto) {
		address.setLabel(resolveAddressLabel(dto.getLabel()));
		address.setLine1(dto.getLine1());
		address.setLine2(dto.getLine2());
		address.setCity(dto.getCity());
		address.setState(dto.getState());
		address.setPincode(dto.getPincode());
		address.setPhone(dto.getPhone());
		address.setLandmark(dto.getLandmark());
		address.setLatitude(dto.getLatitude());
		address.setLongitude(dto.getLongitude());
		address.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
	}

	private AddressLabel resolveAddressLabel(String rawLabel) {
		if (rawLabel == null || rawLabel.isBlank())
			return AddressLabel.OTHER;
		String normalized = rawLabel.trim().toUpperCase();
		if (normalized.contains("HOME"))
			return AddressLabel.HOME;
		if (normalized.contains("WORK") || normalized.contains("OFFICE"))
			return AddressLabel.WORK;
		return AddressLabel.OTHER;
	}
}