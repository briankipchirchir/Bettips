package com.bettips.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {
    @NotBlank
    private String fullName;

    @NotBlank
    private String phone;

    // Optional — defaults to phone if not provided
    private String smsNumber;

    @NotBlank @Size(min = 4)
    private String password;
}
