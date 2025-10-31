package com.individual.messenger.dto;

public class AddFriendRequest {
    private String friendId;

    public AddFriendRequest() {}
    public AddFriendRequest(String friendId) { this.friendId = friendId; }

    public String getFriendId() { return friendId; }
    public void setFriendId(String friendId) { this.friendId = friendId; }
}
