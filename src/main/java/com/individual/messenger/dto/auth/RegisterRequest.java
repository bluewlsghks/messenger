package com.individual.messenger.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties("phoneNumber")
public class RegisterRequest {
    @NotBlank public String id;          // loginId
    @NotBlank public String userName;
    @NotBlank public String password;    // raw password
}
