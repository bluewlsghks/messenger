package com.individual.messenger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class ConferenceDtos {
    private ConferenceDtos() {}
    public record Join(@NotNull UUID clientId) {}
    public enum Action { PING, LEAVE, OFFER, ANSWER, ICE, RESTART }
    public record Command(@NotNull UUID clientId,@NotNull Action action,
                          @Size(max=64) String targetUserId,@Size(max=16000) String sdp,@Valid VoiceCallDtos.Candidate candidate) {}
    public record Member(String userId,UUID clientId,long joinedAt) {}
    public record View(String roomId,UUID epoch,int capacity,List<Member> members) {}
    public record Event(String type,String action,String roomId,View conference,String fromUserId,UUID targetClientId,
                        String sdp,VoiceCallDtos.Candidate candidate) {}
}
