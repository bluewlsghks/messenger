package com.individual.messenger.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties("phoneNumber")
public class RegisterRequest {
    @NotBlank @Size(max = 100) public String id;          // loginId
    @NotBlank @Size(max = 100) public String userName;
    @NotBlank @Size(min = 8, max = 72) public String password;    // raw password
}
