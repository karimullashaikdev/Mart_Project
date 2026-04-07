package com.karim.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpResponseDto {
    private String message;
    private String referenceId;
}