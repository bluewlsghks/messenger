package com.individual.messenger.service;

import com.individual.messenger.dto.ConferenceDtos.*;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.MediaRegistry.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;

/** Four-person WebRTC mesh. This is deliberately bounded, not an SFU or unlimited voice server. */
@Service
public class ConferenceService {
    private final MediaRegistry registry;
    private final ChatAccessService access;
    private final RoomRepository rooms;
    public ConferenceService(MediaRegistry registry,ChatAccessService access,RoomRepository rooms) {this.registry=registry;this.access=access;this.rooms=rooms;}
    public View join(Principal principal,String roomId,UUID client) {
        String user=access.requireWritable(roomId,principal).loginId;
        var room=rooms.findById(roomId).orElse(null);
        if(room!=null && room.type==RoomType.DIRECT)throw bad("DM은 1:1 통화 버튼을 사용하세요.");
        return registry.change(state->{
            expire(state);Conference conference=state.conferences.get(roomId);
            if(conference==null) {
                if(state.calls.size()+state.conferences.size()>=64)throw conflict("동시 통화 한도에 도달했습니다.");
                conference=new Conference();conference.room=roomId;conference.created=System.currentTimeMillis();state.conferences.put(roomId,conference);
            }
            Participant current=conference.participants.get(user);
            if(current!=null) {
                if(!client.equals(current.client))throw conflict("다른 탭에서 이미 이 통화에 참여했습니다.");
                current.seen=System.currentTimeMillis();return conference.view();
            }
            if(state.occupied.containsKey(user))throw conflict("다른 통화에 참여 중입니다.");
            if(conference.participants.size()>=4)throw conflict("다인 통화는 최대 4명입니다.");
            Participant participant=new Participant();participant.client=client;participant.joined=participant.seen=System.currentTimeMillis();
            conference.participants.put(user,participant);state.occupied.put(user,conference.epoch);
            roster(state,conference);return conference.view();
        });
    }
    public View view(Principal principal,String room) {
        access.requireMember(room,principal);
        return registry.change(state->{expire(state);var conference=state.conferences.get(room);return conference==null?new View(room,null,4,List.of()):conference.view();});
    }
    public void command(Principal principal,String room,Command command) {
        String user=access.actor(principal).loginId;
        if(command.action()!=Action.LEAVE)access.requireWritable(room,principal);
        registry.change(state->{
            expire(state);Conference conference=state.conferences.get(room);
            if(conference==null && command.action()==Action.LEAVE)return null;
            Participant participant=conference==null?null:conference.participants.get(user);
            if(participant==null)throw forbidden();
            if(!command.clientId().equals(participant.client))throw conflict("다른 탭에서 진행 중인 통화입니다.");
            if(command.action()==Action.LEAVE){leave(state,conference,user,"LEAVE");return null;}
            participant.seen=System.currentTimeMillis();
            if(command.action()==Action.PING)return null;
            String target=command.targetUserId();Participant peer=target==null?null:conference.participants.get(target);
            if(peer==null || user.equals(target))throw forbidden();
            if(++participant.signals>2048)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"통화 신호 제한을 초과했습니다.");
            if(command.action()==Action.RESTART) {
                if(user.compareTo(target)<0)throw forbidden();
                MediaRegistry.conference(state,target,conference.view(),"RESTART",user,peer.client,null,null);return null;
            }
            if(command.action()==Action.OFFER || command.action()==Action.ANSWER) {
                if(command.sdp()==null || command.sdp().isBlank() || command.sdp().length()>16000)throw bad("SDP가 유효하지 않습니다.");
                boolean initiator=user.compareTo(target)<0;
                if((command.action()==Action.OFFER)!=initiator)throw forbidden();
                String pair=initiator?user+":"+target:target+":"+user;
                if(command.action()==Action.OFFER) {
                    long last=conference.offerAt.getOrDefault(pair,0L);
                    if(System.currentTimeMillis()-last<1000)throw conflict("연결 제안 간격이 너무 짧습니다.");
                    conference.offerAt.put(pair,System.currentTimeMillis());
                } else if(!conference.offerAt.containsKey(pair))throw conflict("연결 제안을 먼저 받아야 합니다.");
            } else {
                var candidate=command.candidate();
                if(candidate==null || candidate.candidate()==null || candidate.candidate().length()>2048 ||
                        (!candidate.candidate().isEmpty() && candidate.sdpMid()==null && candidate.sdpMLineIndex()==null))throw bad("ICE 후보가 유효하지 않습니다.");
            }
            MediaRegistry.conference(state,target,conference.view(),command.action().name(),user,peer.client,
                    command.action()==Action.ICE?null:command.sdp(),command.action()==Action.ICE?command.candidate():null);
            return null;
        });
    }
    @Scheduled(fixedDelay=5000,initialDelay=5000) public void sweep(){registry.change(state->{expire(state);return null;});}
    private void expire(State state) {
        long now=System.currentTimeMillis();
        for(Conference conference:new ArrayList<>(state.conferences.values())) {
            for(var entry:new ArrayList<>(conference.participants.entrySet())) {
                boolean allowed=true;
                try{access.requireWritable(conference.room,()->entry.getKey());}catch(RuntimeException denied){allowed=false;}
                if(!allowed || now-entry.getValue().seen>70000 || now-conference.created>3600000)
                    leave(state,conference,entry.getKey(),allowed?"EXPIRED":"ACCESS_REVOKED");
            }
        }
    }
    private void leave(State state,Conference conference,String user,String reason) {
        Participant participant=conference.participants.remove(user);if(participant==null)return;
        state.occupied.remove(user,conference.epoch);
        conference.offerAt.keySet().removeIf(pair->pair.startsWith(user+":") || pair.endsWith(":"+user));
        Signal history=new Signal();history.id="conference-"+conference.epoch+"-"+user+"-"+participant.joined;history.action="HISTORY";
        history.expires=System.currentTimeMillis()+86400000;
        history.history=Map.of("callId",conference.epoch.toString(),"kind","CONFERENCE","roomId",conference.room,
                "participants",List.of(user),"startedAt",participant.joined,"endedAt",System.currentTimeMillis(),"reason",reason);
        state.signals.add(history);
        MediaRegistry.conference(state,user,conference.view(),"LEFT",user,participant.client,null,null);
        if(conference.participants.isEmpty())state.conferences.remove(conference.room);
        else roster(state,conference);
    }
    private static void roster(State state,Conference conference) {
        for(var entry:conference.participants.entrySet())MediaRegistry.conference(state,entry.getKey(),conference.view(),"MEMBERS",null,entry.getValue().client,null,null);
    }
    private static ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"이 통화의 참여자가 아닙니다.");}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
