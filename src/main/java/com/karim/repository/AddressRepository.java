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

    // ✅ Find all addresses by userId
    @Query("""
        SELECT a FROM Address a
        WHERE a.user.id = :userId
    """)
    List<Address> findByUserId(@Param("userId") UUID userId);

    // ✅ Find default address
    @Query("""
        SELECT a FROM Address a
        WHERE a.user.id = :userId AND a.isDefault = true
    """)
    Optional<Address> findDefaultByUser(@Param("userId") UUID userId);

    // ✅ Unset all default addresses
    @Modifying
    @Query("""
        UPDATE Address a
        SET a.isDefault = false
        WHERE a.user.id = :userId
    """)
    void unsetAllDefaults(@Param("userId") UUID userId);
}