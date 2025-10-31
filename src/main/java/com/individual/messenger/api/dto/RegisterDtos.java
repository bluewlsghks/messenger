package com.individual.messenger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RegisterDtos {
    public static class RegisterRequest {
        @NotBlank public String id;          // loginId
        @NotBlank public String userName;
        @NotBlank public String password;    // raw password
        @NotBlank @Pattern(regexp = "^[0-9\\-+]{9,20}$")
        public String phoneNumber;
    }
    public static class RegisterResponse {
        public String id;
        public String userName;
        public RegisterResponse(String id, String userName) { this.id = id; this.userName = userName; }
    }
}
