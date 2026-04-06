package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> , JpaSpecificationExecutor<User>{

    // ✅ Find by email (only non-deleted due to @SQLRestriction)
    Optional<User> findByEmail(String email);

    // ✅ Default list (soft delete applied automatically)
    Page<User> findAll(Pageable pageable);

    // ✅ List users with optional filter (soft delete + pagination)
    @Query("""
        SELECT u FROM User u
        WHERE (:email IS NULL OR u.email = :email)
    """)
    Page<User> listUsers(@Param("email") String email, Pageable pageable);

    // ✅ Admin: include deleted users explicitly
    @Query("""
        SELECT u FROM User u
        WHERE (:email IS NULL OR u.email = :email)
        AND u.isDeleted = true
    """)
    Page<User> listDeletedUsers(@Param("email") String email, Pageable pageable);
    
    
    @Modifying
    @Query("""
    UPDATE User u
    SET u.isDeleted = false
    WHERE u.id = :userId
    """)
    int restoreUser(@Param("userId") UUID userId);

	Optional<User> findByPhone(String phone);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);
}