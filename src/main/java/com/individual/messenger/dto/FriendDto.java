package com.individual.messenger.dto;

public class FriendDto {
    private String userId;
    private String userName;

    public FriendDto() {}
    public FriendDto(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public String getUserId() { return userId; }
    public String getUserName() { return userName; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
}
