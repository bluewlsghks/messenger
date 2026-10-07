package com.individual.messenger.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank public String id;
    @NotBlank public String password;
}
