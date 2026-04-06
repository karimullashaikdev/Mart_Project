package com.karim.dto;

import com.karim.enums.Role;

import lombok.Data;

@Data
public class UserFilterDto {

    private Role role;

    private Boolean isActive;

    private String search; // optional (name/email search)
}
