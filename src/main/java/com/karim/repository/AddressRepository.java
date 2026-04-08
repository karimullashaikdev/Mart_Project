package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Address;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    // ✅ Get all active addresses of user
    List<Address> findByUserIdAndIsDeletedFalse(UUID userId);

    // ✅ Find default address
    Optional<Address> findByUserIdAndIsDefaultTrueAndIsDeletedFalse(UUID userId);

    // ✅ Unset all default addresses
    @Modifying
    @Query("""
        UPDATE Address a
        SET a.isDefault = false
        WHERE a.user.id = :userId
    """)
    void unsetAllDefaults(@Param("userId") UUID userId);
}