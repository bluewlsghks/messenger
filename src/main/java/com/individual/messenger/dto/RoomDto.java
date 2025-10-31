package com.individual.messenger.dto;

import java.util.List;

public class RoomDto {
    private String id;
    private String type;
    private List<String> members;

    public RoomDto() {}

    public RoomDto(String id, String type, List<String> members) {
        this.id = id;
        this.type = type;
        this.members = members;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
}
