package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.dto.VoiceCallDtos;
import com.individual.messenger.dto.VoiceCallDtos.*;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.individual.messenger.service.MediaRegistry.Call;
import com.individual.messenger.service.MediaRegistry.State;

/** Shared, bounded media state. Signaling is encrypted transiently; media itself is peer-to-peer. */
@Service
public class VoiceCallService {
    static final long RING_MS = 45_000, LEASE_MS = 70_000, MAX_CALL_MS = 3_600_000;
    private final ChatAccessService access;
    private final RoomRepository rooms;
    private final VoiceEvents events;
    private final Clock clock;
    private final MediaRegistry registry;

    @Autowired
    public VoiceCallService(ChatAccessService access, RoomRepository rooms, VoiceEvents events, MediaRegistry registry) {
        this.access=access; this.rooms=rooms; this.events=events; this.clock=Clock.systemUTC(); this.registry=registry;
    }
    VoiceCallService(ChatAccessService access, RoomRepository rooms, VoiceEvents events, Clock clock) {
        this.access = access; this.rooms = rooms; this.events = events; this.clock = clock; this.registry=MediaRegistry.memory(events);
    }



    public View start(Principal principal, UUID id, UUID clientId, String roomId) {
        return registry.change(state -> start(state,principal,id,clientId,roomId));
    }
    private View start(State state,Principal principal, UUID id, UUID clientId, String roomId) {
        String user = access.requireWritable(roomId, principal).loginId;
        Room room = rooms.findById(roomId).orElseThrow(() -> bad("DM에서만 음성통화를 시작할 수 있습니다."));
        if (room.type != RoomType.DIRECT || room.members == null || room.members.size() != 2
                || room.members.stream().distinct().count() != 2 || !room.members.contains(user))
            throw bad("서로 다른 두 명의 DM에서만 음성통화를 사용할 수 있습니다.");
        String peer = room.members.stream().filter(member -> !member.equals(user)).findFirst().orElseThrow();
        access.actor(() -> peer);
        expire(state);
        if (state.calls.size()+state.conferences.size()>=64) throw conflict("동시 통화 한도에 도달했습니다.");
        if (state.calls.containsKey(id)) throw conflict("이미 사용 중인 통화 ID입니다.");
        if (state.occupied.containsKey(user) || state.occupied.containsKey(peer)) throw conflict("본인 또는 상대방이 이미 통화 중입니다.");
        if (!events.online(peer)) throw conflict("상대방의 메신저가 연결되어 있지 않습니다.");
        long now = clock.millis();
        Long previous = state.lastStarts.get(user);
        if (previous != null && now - previous < 3_000)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 통화를 시도해 주세요.");
        state.lastStarts.put(user, now);
        Call call = new Call();
        call.id = id; call.room = roomId; call.caller = user; call.callee = peer;
        call.callerClient = clientId; call.created = now; call.callerSeen = now; call.calleeSeen = now;
        state.calls.put(id, call); state.occupied.put(user, id); state.occupied.put(peer, id);
        MediaRegistry.call(state,peer,call.view(),"RING",null,null,null,null);
        return call.view();
    }

    public void command(Principal principal, UUID id, VoiceCallDtos.Command command) {
        registry.change(state -> { command(state,principal,id,command); return null; });
    }
    private void command(State state,Principal principal, UUID id, VoiceCallDtos.Command command) {
        String user = access.actor(principal).loginId;
        expire(state);
        Call call = state.calls.get(id);
        // A repeated hangup is harmless, including a keepalive request during page teardown.
        if (call == null && command.action() == Action.END) return;
        if (call == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미 종료된 통화입니다.");
        if (!user.equals(call.caller) && !user.equals(call.callee)) throw forbidden();
        if(command.action()!=Action.END && command.action()!=Action.DECLINE) access.requireWritable(call.room, principal);
        boolean caller = user.equals(call.caller);
        UUID expected = caller ? call.callerClient : call.calleeClient;
        if (expected != null && !expected.equals(command.clientId())) throw conflict("다른 탭에서 진행 중인 통화입니다.");
        if (!caller && call.ringing() && command.action() != Action.ACCEPT && command.action() != Action.DECLINE && command.action() != Action.END)
            throw conflict("먼저 통화를 수락해 주세요.");
        switch (command.action()) {
            case ACCEPT -> {
                if (caller || !call.ringing()) throw conflict("수락할 수 없는 통화입니다.");
                call.calleeClient = command.clientId(); call.accepted = clock.millis();
                call.callerSeen = call.accepted; call.calleeSeen = call.accepted;
                broadcast(state,call, "ACCEPTED", null);
            }
            case DECLINE -> {
                if (caller || !call.ringing()) throw conflict("거절할 수 없는 통화입니다.");
                finish(state,call, "DECLINED");
            }
            case END -> finish(state,call, !caller && call.ringing() ? "DECLINED" : "HANGUP");
            case PING -> {
                if (caller) call.callerSeen = clock.millis(); else call.calleeSeen = clock.millis();
            }
            case RESTART_REQUEST -> {
                if(call.ringing() || caller)throw conflict("상대방만 재연결을 요청할 수 있습니다.");
                MediaRegistry.call(state,call.caller,call.view(),"RESTART_REQUEST",call.callerClient,null,null,null);
            }
            case RESTART_OFFER, RESTART_ANSWER -> {
                if(call.ringing())throw conflict("수락 후 재연결할 수 있습니다.");
                requireSdp(command.sdp());
                if(command.action()==Action.RESTART_OFFER) {
                    if(!caller || !call.answer || call.restarting || call.restarts>=3 || clock.millis()-call.lastRestart<3000)
                        throw conflict("재연결 제안을 보낼 수 없습니다.");
                    call.restarting=true;call.answer=false;call.restarts++;call.lastRestart=clock.millis();
                    call.callerIce=0;call.calleeIce=0;
                } else {
                    if(caller || !call.restarting)throw conflict("재연결 응답을 보낼 수 없습니다.");
                    call.restarting=false;call.answer=true;
                }
                MediaRegistry.call(state,caller?call.callee:call.caller,call.view(),command.action().name(),
                        caller?call.calleeClient:call.callerClient,command.sdp(),null,null);
            }
            case OFFER, ANSWER, ICE -> {
                if (call.ringing()) throw conflict("아직 수락되지 않은 통화입니다.");
                if (command.action() == Action.OFFER) {
                    if (!caller || call.offer) throw conflict("유효하지 않은 연결 제안입니다.");
                    requireSdp(command.sdp()); call.offer = true;
                } else if (command.action() == Action.ANSWER) {
                    if (caller || !call.offer || call.answer || call.restarting) throw conflict("유효하지 않은 연결 응답입니다.");
                    requireSdp(command.sdp()); call.answer = true;
                } else {
                    var candidate = command.candidate();
                    if (candidate == null || candidate.candidate() == null
                            || candidate.candidate().length() > 2048
                            || (!candidate.candidate().isEmpty() && (candidate.candidate().isBlank()
                            || (candidate.sdpMid() == null && candidate.sdpMLineIndex() == null))))
                        throw bad("유효한 ICE candidate가 필요합니다.");
                    if ((caller ? call.callerIce : call.calleeIce) >= 256)
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "연결 후보 수 제한을 초과했습니다.");
                    if (caller) call.callerIce++; else call.calleeIce++;
                }
                MediaRegistry.call(state,caller ? call.callee : call.caller, call.view(), command.action().name(),
                        caller ? call.calleeClient : call.callerClient,
                        command.action() == Action.ICE ? null : command.sdp(),
                        command.action() == Action.ICE ? command.candidate() : null, null);
            }
        }
    }

    @Scheduled(fixedDelay = 5_000)
    public void sweep() { registry.change(state -> { expire(state); return null; }); }
    private void expire(State state) {
        long now = clock.millis();
        for (Call call : state.calls.values().toArray(Call[]::new)) {
            if (call.ringing() && now >= call.created + RING_MS) finish(state,call, "NO_ANSWER");
            else if (!call.ringing() && now >= call.accepted + MAX_CALL_MS) finish(state,call, "TIME_LIMIT");
            else if (!call.ringing() && (now - call.callerSeen >= LEASE_MS || now - call.calleeSeen >= LEASE_MS))
                finish(state,call, "CONNECTION_LOST");
        }
        state.lastStarts.entrySet().removeIf(entry -> now - entry.getValue() > 60_000);
    }
    private void broadcast(State state,Call call, String action, String reason) {
        MediaRegistry.call(state,call.caller, call.view(), action, null, null, null, reason);
        MediaRegistry.call(state,call.callee, call.view(), action, null, null, null, reason);
    }
    private void finish(State state,Call call, String reason) { remove(state,call); MediaRegistry.history(state,call,reason,clock.millis()); broadcast(state,call, "ENDED", reason); }
    private void remove(State state,Call call) {
        state.calls.remove(call.id); state.occupied.remove(call.caller, call.id); state.occupied.remove(call.callee, call.id);
    }
    public View current(Principal principal) {
        String user=access.actor(principal).loginId;
        return registry.change(state->{
            expire(state);UUID id=state.occupied.get(user);Call call=id==null?null:state.calls.get(id);
            if(call==null)return null;
            access.requireMember(call.room,principal);return call.view();
        });
    }
    public java.util.List<org.bson.Document> history(Principal principal) { return registry.history(access.actor(principal).loginId); }
    private static void requireSdp(String sdp) {
        if (sdp == null || sdp.isBlank() || sdp.length() > 16_000) throw bad("유효한 SDP가 필요합니다.");
    }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
    private static ResponseStatusException forbidden() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "통화 참여자가 아닙니다."); }
}
