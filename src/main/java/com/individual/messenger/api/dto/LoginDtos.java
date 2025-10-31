package com.individual.messenger.api.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginDtos {
    public static class LoginRequest {
        @NotBlank public String id;
        @NotBlank public String password;
    }
    public static class LoginResponse {
        public String token;
        public String id;
        public String userName;
        public LoginResponse(String token, String id, String userName) {
            this.token = token; this.id = id; this.userName = userName;
        }
    }
}
