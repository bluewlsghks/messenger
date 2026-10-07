package com.individual.messenger.dto.auth;

public class LoginResponse {
    public String token;
    public String id;
    public String userName;
    public LoginResponse(String token, String id, String userName) {
        this.token = token; this.id = id; this.userName = userName;
    }
}
