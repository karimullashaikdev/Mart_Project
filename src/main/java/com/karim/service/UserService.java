package com.karim.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.karim.dto.CreateUserProfileDto;
import com.karim.dto.UpdateProfileDto;
import com.karim.dto.UpdateUserDto;
import com.karim.dto.UserFilterDto;
import com.karim.dto.UserProfileDto;
import com.karim.entity.User;
import com.karim.entity.UserProfile;

public interface UserService {

    User getUser(UUID userId);

    User updateUser(UUID userId, UpdateUserDto dto, UUID actorId);

    Optional<UserProfileDto> getProfile(UUID userId);

    UserProfile createOrUpdateProfile(UUID userId, CreateUserProfileDto dto, UUID actorId);

    UserProfile updateProfile(UUID userId, UpdateProfileDto dto, UUID actorId);

    void softDeleteProfile(UUID userId, UUID actorId);

    Page<User> listUsers(UserFilterDto filters, Pageable pageable);

    void restoreUser(UUID userId, UUID actorId);
}
