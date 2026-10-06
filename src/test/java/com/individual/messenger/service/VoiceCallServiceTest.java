package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.domain.User;
import com.individual.messenger.dto.VoiceCallDtos;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.security.ChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceCallServiceTest {
    ChatAccessService access; RoomRepository rooms; VoiceEvents events; VoiceCallService service;
    Room room; MutableClock clock; UUID callId, aliceClient, bobClient;
    Principal alice = () -> "alice", bob = () -> "bob", outsider = () -> "outsider";
    static class MutableClock extends Clock {
        long now = 1_000_000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    @BeforeEach void setup() {
        access = mock(ChatAccessService.class); rooms = mock(RoomRepository.class); events = mock(VoiceEvents.class);
        clock = new MutableClock(); service = new VoiceCallService(access, rooms, events, clock);
        callId = UUID.randomUUID(); aliceClient = UUID.randomUUID(); bobClient = UUID.randomUUID();
        room = Room.directOf("alice", "bob"); room.id = "dm";
        when(rooms.findById("dm")).thenReturn(Optional.of(room));
        when(access.actor(any())).thenAnswer(inv -> user(((Principal) inv.getArgument(0)).getName()));
        when(access.requireMember(eq("dm"), any())).thenAnswer(inv -> {
            String id = ((Principal) inv.getArgument(1)).getName();
            if (!room.members.contains(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return user(id);
        });
        when(events.online(anyString())).thenReturn(true);
    }
    static User user(String id) { User user = new User(); user.loginId = id; return user; }
    void start() { service.start(alice, callId, aliceClient, "dm"); }
    void action(Principal user, UUID client, VoiceCallDtos.Action action, String sdp) {
        service.command(user, callId, new VoiceCallDtos.Command(client, action, sdp, null));
    }
    void accept() { action(bob, bobClient, VoiceCallDtos.Action.ACCEPT, null); }
    void status(HttpStatus expected, Runnable work) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, work::run).getStatusCode());
    }
    @Test void directsRingOnlyToOtherDmMember() {
        start();
        verify(events).send(eq("bob"), argThat(v -> v.callerId().equals("alice") && v.status().equals("RINGING")), eq("RING"), isNull(), isNull(), isNull(), isNull());
        verify(events, never()).send(eq("outsider"), any(), any(), any(), any(), any(), any());
    }
    @Test void forbidsNonmembersAndGroupCalls() {
        status(HttpStatus.FORBIDDEN, () -> service.start(outsider, callId, aliceClient, "dm"));
        room.type = RoomType.GROUP;
        status(HttpStatus.BAD_REQUEST, this::start);
        verify(events, never()).send(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void rejectsOfflineAndBusyUsers() {
        when(events.online("bob")).thenReturn(false);
        status(HttpStatus.CONFLICT, this::start);
        when(events.online("bob")).thenReturn(true); start();
        status(HttpStatus.CONFLICT, () -> service.start(bob, UUID.randomUUID(), bobClient, "dm"));
    }
    @Test void rejectsSpoofedParticipantAndWrongTab() {
        start(); accept();
        status(HttpStatus.FORBIDDEN, () -> action(outsider, bobClient, VoiceCallDtos.Action.END, null));
        status(HttpStatus.CONFLICT, () -> action(alice, UUID.randomUUID(), VoiceCallDtos.Action.END, null));
        action(alice, aliceClient, VoiceCallDtos.Action.END, null);
    }
    @Test void onlyOneTabCanAcceptAndCallerCannotAccept() {
        start();
        status(HttpStatus.CONFLICT, () -> action(alice, aliceClient, VoiceCallDtos.Action.ACCEPT, null));
        accept();
        status(HttpStatus.CONFLICT, () -> action(bob, UUID.randomUUID(), VoiceCallDtos.Action.ACCEPT, null));
        status(HttpStatus.CONFLICT, this::accept);
    }
    @Test void enforcesConsentRolesAndSingleSdpExchange() {
        start();
        status(HttpStatus.CONFLICT, () -> action(alice, aliceClient, VoiceCallDtos.Action.OFFER, "v=0"));
        accept();
        status(HttpStatus.CONFLICT, () -> action(bob, bobClient, VoiceCallDtos.Action.ANSWER, "v=0"));
        status(HttpStatus.BAD_REQUEST, () -> action(alice, aliceClient, VoiceCallDtos.Action.OFFER, " "));
        action(alice, aliceClient, VoiceCallDtos.Action.OFFER, "v=0");
        status(HttpStatus.CONFLICT, () -> action(alice, aliceClient, VoiceCallDtos.Action.OFFER, "v=0"));
        action(bob, bobClient, VoiceCallDtos.Action.ANSWER, "v=0");
        verify(events).send(eq("bob"), any(), eq("OFFER"), eq(bobClient), eq("v=0"), isNull(), isNull());
        verify(events).send(eq("alice"), any(), eq("ANSWER"), eq(aliceClient), eq("v=0"), isNull(), isNull());
    }
    @Test void boundsCandidateCountAndValidatesCandidate() {
        start(); accept();
        status(HttpStatus.BAD_REQUEST, () -> action(alice, aliceClient, VoiceCallDtos.Action.ICE, null));
        var candidate = new VoiceCallDtos.Candidate("candidate:1 1 UDP 1 127.0.0.1 1234 typ host", "0", 0, null);
        var command = new VoiceCallDtos.Command(aliceClient, VoiceCallDtos.Action.ICE, null, candidate);
        for (int i = 0; i < 256; i++) service.command(alice, callId, command);
        status(HttpStatus.TOO_MANY_REQUESTS, () -> service.command(alice, callId, command));
    }
    @Test void forwardsEndOfCandidatesMarkerButRejectsWhitespace() {
        start(); accept();
        var marker = new VoiceCallDtos.Candidate("", null, null, null);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(marker).isEmpty());
        }
        assertDoesNotThrow(() -> service.command(alice, callId,
                new VoiceCallDtos.Command(aliceClient, VoiceCallDtos.Action.ICE, null, marker)));
        verify(events).send(eq("bob"), any(), eq("ICE"), eq(bobClient), isNull(), eq(marker), isNull());
        var invalid = new VoiceCallDtos.Candidate(" ", "0", 0, null);
        status(HttpStatus.BAD_REQUEST, () -> service.command(alice, callId,
                new VoiceCallDtos.Command(aliceClient, VoiceCallDtos.Action.ICE, null, invalid)));
    }
    @Test void expiresUnansweredCallsAndReleasesBothUsers() {
        start(); clock.now += VoiceCallService.RING_MS; service.sweep();
        verify(events).send(eq("alice"), any(), eq("ENDED"), isNull(), isNull(), isNull(), eq("NO_ANSWER"));
        service.start(bob, UUID.randomUUID(), bobClient, "dm");
    }
    @Test void bothParticipantsMustRenewLease() {
        start(); accept(); clock.now += VoiceCallService.LEASE_MS - 1;
        action(alice, aliceClient, VoiceCallDtos.Action.PING, null);
        clock.now += 1; service.sweep();
        verify(events).send(eq("alice"), any(), eq("ENDED"), isNull(), isNull(), isNull(), eq("CONNECTION_LOST"));
    }
    @Test void hangupIsIdempotentAndReceiverCanCancelWhilePreparing() {
        start(); action(bob, bobClient, VoiceCallDtos.Action.END, null);
        action(bob, bobClient, VoiceCallDtos.Action.END, null);
        verify(events, times(1)).send(eq("alice"), any(), eq("ENDED"), isNull(), isNull(), isNull(), eq("DECLINED"));
        clock.now += 3_000; start(); accept(); action(bob, bobClient, VoiceCallDtos.Action.END, null);
    }
    @Test void limitsRapidRedialAndLongCalls() {
        start(); action(alice, aliceClient, VoiceCallDtos.Action.END, null);
        status(HttpStatus.TOO_MANY_REQUESTS, this::start);
        clock.now += 3_000; start(); accept(); clock.now += VoiceCallService.MAX_CALL_MS; service.sweep();
        verify(events).send(eq("alice"), any(), eq("ENDED"), isNull(), isNull(), isNull(), eq("TIME_LIMIT"));
    }
    @Test void failedRingPublicationDoesNotLeaveUsersBusy() {
        doThrow(new IllegalStateException("broker unavailable")).doNothing().when(events)
                .send(eq("bob"), any(), eq("RING"), isNull(), isNull(), isNull(), isNull());
        assertThrows(IllegalStateException.class, this::start);
        clock.now += 3_000; assertDoesNotThrow(this::start);
    }
}
