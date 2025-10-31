package com.individual.messenger.dto;

import java.util.List;

public class CreateGroupRequest {
    private java.util.List<String> members;

    public CreateGroupRequest() {}
    public CreateGroupRequest(java.util.List<String> members) { this.members = members; }

    public java.util.List<String> getMembers() { return members; }
    public void setMembers(java.util.List<String> members) { this.members = members; }
}
