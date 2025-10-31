package com.individual.messenger.dto;

public class CreateDmRequest {
    private String peerId;

    public CreateDmRequest() {}
    public CreateDmRequest(String peerId) { this.peerId = peerId; }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }
}
