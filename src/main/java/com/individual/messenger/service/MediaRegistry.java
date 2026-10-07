package com.individual.messenger.service;

import com.individual.messenger.dto.VoiceCallDtos.*;
import com.individual.messenger.dto.ConferenceDtos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;

/** Control/SDP/ICE are encrypted, short-lived outbox data; audio/video bytes never reach this service. */
@Service
public class MediaRegistry {
    public static final String KEY="messenger-media-v1";
    private final EphemeralStateStore store;
    private final ObjectProvider<VoiceEvents> provider;
    private final VoiceEvents direct;
    @org.springframework.beans.factory.annotation.Autowired
    public MediaRegistry(EphemeralStateStore store,ObjectProvider<VoiceEvents> events) {this.store=store;this.provider=events;this.direct=null;}
    private MediaRegistry(VoiceEvents events) {this.store=EphemeralStateStore.memory();this.provider=null;this.direct=events;}
    static MediaRegistry memory(VoiceEvents events) {return new MediaRegistry(events);}
    public static class State {
        public Map<UUID,Call> calls=new LinkedHashMap<>();
        public Map<String,UUID> occupied=new HashMap<>();
        public Map<String,Long> lastStarts=new HashMap<>();
        public Map<String,Conference> conferences=new LinkedHashMap<>();
        public List<Signal> signals=new ArrayList<>();
        public String deliveryLease;
        public long deliveryUntil;
    }
    public static class Call {
        public UUID id,callerClient,calleeClient;
        public String room,caller,callee;
        public long created,accepted,callerSeen,calleeSeen,lastRestart;
        public boolean offer,answer,restarting;
        public int callerIce,calleeIce,restarts;
        public boolean ringing(){return calleeClient==null;}
        public View view(){return new View(id,room,caller,callee,callerClient,calleeClient,ringing()?"RINGING":"ACCEPTED",
                ringing()?created+45000:accepted+3600000);}
    }
    public static class Conference {
        public UUID epoch=UUID.randomUUID();
        public String room;
        public long created;
        public Map<String,Participant> participants=new LinkedHashMap<>();
        public Map<String,Long> offerAt=new HashMap<>();
        public ConferenceDtos.View view(){return new ConferenceDtos.View(room,epoch,4,participants.entrySet().stream()
                .map(entry->new ConferenceDtos.Member(entry.getKey(),entry.getValue().client,entry.getValue().joined)).toList());}
    }
    public static class Participant {public UUID client;public long joined,seen;public int signals;}
    public static class Signal {
        public String id=UUID.randomUUID().toString(),recipient,action,reason,room,from;
        public UUID target;
        public View call;
        public ConferenceDtos.View conference;
        public String sdp;
        public Candidate candidate;
        public Map<String,Object> history;
        public long expires=System.currentTimeMillis()+60000;
    }
    public <R> R change(Function<State,R> operation) {
        R result=store.change(KEY,State.class,State::new,operation);
        if(!store.persistent())drain();
        return result;
    }
    public static void call(State state,String recipient,View call,String action,UUID target,String sdp,Candidate candidate,String reason) {
        Signal signal=new Signal();signal.recipient=recipient;signal.room=call.roomId();signal.call=call;signal.action=action;
        signal.target=target;signal.sdp=sdp;signal.candidate=candidate;signal.reason=reason;enqueue(state,signal);
    }
    public static void conference(State state,String recipient,ConferenceDtos.View view,String action,String from,UUID target,String sdp,Candidate candidate) {
        Signal signal=new Signal();signal.recipient=recipient;signal.room=view.roomId();signal.conference=view;signal.action=action;
        signal.from=from;signal.target=target;signal.sdp=sdp;signal.candidate=candidate;enqueue(state,signal);
    }
    public static void history(State state,Call call,String reason,long ended) {
        Signal signal=new Signal();signal.id="history-"+call.id;signal.action="HISTORY";signal.expires=System.currentTimeMillis()+86400000;
        signal.history=Map.of("callId",call.id.toString(),"roomId",call.room,"participants",List.of(call.caller,call.callee),
                "callerId",call.caller,"calleeId",call.callee,"startedAt",call.created,"acceptedAt",call.accepted,"endedAt",ended,"reason",reason);
        enqueue(state,signal);
    }
    private static void enqueue(State state,Signal signal) {
        state.signals.removeIf(value->value.expires<System.currentTimeMillis());
        // Reserve control-message room by dropping obsolete ICE first; a failed media connection can restart.
        if(state.signals.size()>=512)state.signals.removeIf(value->"ICE".equals(value.action));
        if(state.signals.size()>=768)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"통화 신호 대기열이 가득 찼습니다.");
        state.signals.add(signal);
    }
    @Scheduled(fixedDelay=250,initialDelay=1500)
    public void drain() {
        for(int i=0;i<32;i++) {
            String lease=UUID.randomUUID().toString();long now=System.currentTimeMillis();
            Signal next=store.change(KEY,State.class,State::new,state->{
                state.signals.removeIf(value->value.expires<now);
                if(state.deliveryUntil>now || state.signals.isEmpty())return null;
                state.deliveryLease=lease;state.deliveryUntil=now+15000;
                return state.signals.getFirst();
            });
            if(next==null)return;
            try {
                VoiceEvents events=direct==null?provider.getObject():direct;
                if(next.history!=null)store.history(next.id,next.history);
                else if(next.conference!=null)events.conference(next.recipient,new ConferenceDtos.Event("CONFERENCE",next.action,next.room,
                        next.conference,next.from,next.target,next.sdp,next.candidate));
                else events.send(next.recipient,next.call,next.action,next.target,next.sdp,next.candidate,next.reason);
                store.change(KEY,State.class,State::new,state->{
                    if(lease.equals(state.deliveryLease)){state.signals.removeIf(value->value.id.equals(next.id));state.deliveryUntil=0;state.deliveryLease=null;}
                    return null;
                });
            } catch(RuntimeException failure) {
                store.change(KEY,State.class,State::new,state->{if(lease.equals(state.deliveryLease))state.deliveryUntil=System.currentTimeMillis()+2000;return null;});
                return;
            }
        }
    }
    public java.util.List<org.bson.Document> history(String user){return store.history(user);}
}
