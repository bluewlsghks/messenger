package com.individual.messenger.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank private String loginId;
    @NotBlank private String userName;
    @NotBlank private String password;
    @NotBlank private String phoneNumber;

    // getter/setter
}


