package com.individual.messenger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.Message;
import com.individual.messenger.security.ChatAccessService;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.*;
import java.time.*;
import java.util.*;

/** Opt-in encrypted Web Push. The browser's push provider transports only a generic notice. */
@Service
public class WebPushService implements MessageProjection {
    private final MongoTemplate mongo;
    private final ChatAccessService access;
    private final SessionService sessions;
    private final CryptoService crypto;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String publicKey;
    private final Sender sender;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<VoiceCallService> calls;
    public WebPushService(MongoTemplate mongo, ChatAccessService access, SessionService sessions,
                          CryptoService crypto, ObjectMapper json,
                          @Value("${app.push.enabled:false}") boolean enabled,
                          @Value("${app.push.public-key:}") String publicKey,
                          @Value("${app.push.private-key:}") String privateKey,
                          @Value("${app.push.subject:}") String subject) throws GeneralSecurityException {
        this.mongo=mongo; this.access=access; this.sessions=sessions; this.crypto=crypto; this.json=json;
        this.enabled=enabled; this.publicKey=publicKey;
        if (enabled && (publicKey.isBlank() || privateKey.isBlank() ||
                !(subject.startsWith("mailto:") || subject.startsWith("https://"))))
            throw new IllegalArgumentException("Web Push needs VAPID public/private keys and a mailto:/https: subject.");
        if (enabled && Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        this.sender=enabled ? new Sender(publicKey,privateKey,subject) : null;
    }
    public Map<String,Object> configuration() { return Map.of("enabled",enabled,"publicKey",enabled?publicKey:""); }
    public void subscribe(String user, String sid, String endpoint, String key, String auth) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Web Push가 설정되지 않았습니다.");
        if (sid == null || !sessions.active(sid,user)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"다시 로그인한 후 알림을 설정해 주세요.");
        validateEndpoint(endpoint); validateKeys(key,auth);
        String id=hash(endpoint);
        Document previous=mongo.findById(id,Document.class,"push_subscriptions");
        if (previous!=null && !user.equals(previous.getString("owner")) &&
                sessions.active(previous.getString("sessionId"), previous.getString("owner")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"이 브라우저의 이전 계정 알림을 먼저 해제해 주세요.");
        if (previous==null && mongo.count(Query.query(Criteria.where("owner").is(user)),"push_subscriptions")>=10)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"계정당 알림 기기는 최대 10개입니다.");
        try {
            String encrypted=crypto.encryptString(json.writeValueAsString(Map.of("endpoint",endpoint,"key",key,"auth",auth)));
            Criteria owns=previous==null?Criteria.where("_id").is(id).and("owner").exists(false):
                    Criteria.where("_id").is(id).and("owner").is(previous.getString("owner")).and("sessionId").is(previous.getString("sessionId"));
            mongo.upsert(Query.query(owns),new Update().set("owner",user).set("sessionId",sid)
                    .set("encrypted",encrypted).set("expiresAt",Date.from(Instant.now().plus(Duration.ofDays(30)))),"push_subscriptions");
        } catch (org.springframework.dao.DuplicateKeyException race) { throw new ResponseStatusException(HttpStatus.CONFLICT,"알림 구독이 변경되었습니다. 다시 설정해 주세요."); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    public void unsubscribe(String user,String endpoint) {
        if(endpoint==null || endpoint.length()>2048) throw new IllegalArgumentException("유효한 구독 주소가 필요합니다.");
        String id=hash(endpoint);
        mongo.remove(Query.query(Criteria.where("_id").is(id).and("owner").is(user)),"push_subscriptions");
        mongo.remove(Query.query(Criteria.where("subscriptionId").is(id).and("owner").is(user)),"push_jobs");
    }
    public void preferences(String user, String sid, String endpoint, List<String> mutedRooms) {
        validateEndpoint(endpoint);
        List<String> rooms=mutedRooms==null?List.of():mutedRooms.stream().distinct().toList();
        if(rooms.size()>200 || rooms.stream().anyMatch(room->room==null || !room.matches("[A-Za-z0-9_-]{1,100}")))
            throw new IllegalArgumentException("대화 알림 설정이 유효하지 않습니다.");
        if(sid==null || !sessions.active(sid,user))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(hash(endpoint)).and("owner").is(user).and("sessionId").is(sid)),
                new Update().set("mutedRooms",rooms),"push_subscriptions");
    }
    static boolean muted(Document subscription,String room) {
        return subscription.getList("mutedRooms",String.class,List.of()).contains(room);
    }
    @Override public void publish(String type,Message message) {
        if(!enabled || !"MESSAGE_CREATED".equals(type) || message.deletedAt!=null) return;
        // Re-authorize each recipient at delivery too. Never queue chat text, phone numbers or names.
        var room=mongo.findById(message.roomId,com.individual.messenger.domain.Room.class);
        List<String> recipients;
        if(room!=null) recipients=room.members;
        else {
            var server=mongo.findOne(Query.query(Criteria.where("channels.id").is(message.roomId)),com.individual.messenger.domain.ChatServer.class);
            if(server==null || server.deleted) return;
            recipients=server.members;
        }
        for(Document subscription:mongo.find(Query.query(Criteria.where("owner").in(recipients).and("expiresAt").gt(new Date())),Document.class,"push_subscriptions")) {
            String user=subscription.getString("owner");
            if(user.equals(message.senderId) || message.readBy.contains(user) || muted(subscription,message.roomId)) continue;
            try { access.requireMember(message.roomId,()->user); access.requireWritable(message.roomId,()->message.senderId); }
            catch(RuntimeException denied) { continue; }
            String id=hash(subscription.getString("_id")+":"+message.id);
            try {
                mongo.upsert(Query.query(Criteria.where("_id").is(id)),new Update().setOnInsert("owner",user)
                        .setOnInsert("subscriptionId",subscription.getString("_id")).setOnInsert("roomId",message.roomId)
                        .setOnInsert("messageId",message.id).setOnInsert("pending",true).setOnInsert("attempts",0)
                        .setOnInsert("nextAt",new Date()).setOnInsert("expiresAt",Date.from(Instant.now().plusSeconds(86400))),"push_jobs");
            } catch(org.springframework.dao.DuplicateKeyException ignored) { /* Outbox redelivery. */ }
        }
    }
    public boolean reachable(String user) {
        if(!enabled)return false;
        return mongo.find(Query.query(Criteria.where("owner").is(user).and("expiresAt").gt(new Date())).limit(10),Document.class,"push_subscriptions")
                .stream().anyMatch(subscription->sessions.active(subscription.getString("sessionId"),user));
    }
    public void ring(String user,com.individual.messenger.dto.VoiceCallDtos.View call) {
        if(!enabled)return;
        for(Document sub:mongo.find(Query.query(Criteria.where("owner").is(user).and("expiresAt").gt(new Date())).limit(10),Document.class,"push_subscriptions")) {
            if(muted(sub,call.roomId()))continue;
            String id=hash(sub.getString("_id")+":call:"+call.id());
            try { mongo.upsert(Query.query(Criteria.where("_id").is(id)),new Update().setOnInsert("owner",user)
                    .setOnInsert("subscriptionId",sub.getString("_id")).setOnInsert("roomId",call.roomId())
                    .setOnInsert("callId",call.id().toString()).setOnInsert("pending",true).setOnInsert("attempts",0)
                    .setOnInsert("nextAt",new Date()).setOnInsert("expiresAt",new Date(call.expiresAt())),"push_jobs"); }
            catch(org.springframework.dao.DuplicateKeyException ignored) { }
        }
    }
    @Scheduled(fixedDelay=2000,initialDelay=5000)
    public void drain() {
        if(!enabled) return;
        for(int i=0;i<10;i++) {
            Instant now=Instant.now();String lease=UUID.randomUUID().toString();
            Criteria ready=new Criteria().andOperator(Criteria.where("pending").is(true).and("nextAt").lte(Date.from(now)).and("expiresAt").gt(Date.from(now)),
                    new Criteria().orOperator(Criteria.where("leaseUntil").is(null),Criteria.where("leaseUntil").lte(Date.from(now))));
            Document job=mongo.findAndModify(Query.query(ready).with(Sort.by("nextAt")),new Update().set("lease",lease)
                    .set("leaseUntil",Date.from(now.plusSeconds(20))).inc("attempts",1),FindAndModifyOptions.options().returnNew(true),Document.class,"push_jobs");
            if(job==null) return;
            Query owned=Query.query(Criteria.where("_id").is(job.getString("_id")).and("lease").is(lease));
            try {
                Document subscription=mongo.findById(job.getString("subscriptionId"),Document.class,"push_subscriptions");
                Message message=job.containsKey("messageId")?mongo.findById(job.getString("messageId"),Message.class):null;
                boolean incoming=job.containsKey("callId");
                String user=job.getString("owner"),room=job.getString("roomId");
                if(subscription==null || !user.equals(subscription.getString("owner")) || muted(subscription,room) ||
                        !sessions.active(subscription.getString("sessionId"),user) || (!incoming && (message==null ||
                        message.deletedAt!=null || message.readBy.contains(user)))) { complete(owned); continue; }
                try {
                    access.requireMember(room,()->user);
                    if(incoming) {
                        var current=calls.getObject().current(()->user);
                        if(current==null || !current.id().toString().equals(job.getString("callId")) || !"RINGING".equals(current.status())
                                || !user.equals(current.calleeId())) {complete(owned);continue;}
                        access.requireWritable(room,()->current.callerId());
                    } else access.requireWritable(room,()->message.senderId);
                }
                catch(RuntimeException denied) { complete(owned);continue; }
                Document keys=Document.parse(crypto.decryptString(subscription.getString("encrypted")));
                validateEndpoint(keys.getString("endpoint"));
                byte[] payload=json.writeValueAsBytes(incoming?Map.of("kind","call","roomId",room,"tag","call-"+job.getString("callId")):
                        Map.of("kind","message","roomId",room,"messageId",message.id,"tag","room-"+room));
                int status=sender.deliver(keys.getString("endpoint"),keys.getString("key"),keys.getString("auth"),payload,incoming?30:3600);
                if(status==404 || status==410) {
                    mongo.remove(Query.query(Criteria.where("_id").is(subscription.getString("_id")).and("encrypted").is(subscription.getString("encrypted"))),"push_subscriptions");
                    complete(owned);
                } else if(status>=200 && status<300) complete(owned);
                else throw new IllegalStateException("Push provider status "+status);
            } catch(Exception failure) {
                int attempts=job.getInteger("attempts",1);
                mongo.updateFirst(owned,new Update().set("pending",attempts<8).set("nextAt",Date.from(now.plusSeconds(Math.min(3600,30L<<Math.min(7,attempts)))))
                        .unset("lease").unset("leaseUntil"),"push_jobs");
                if(failure instanceof InterruptedException) { Thread.currentThread().interrupt();return; }
            }
        }
    }
    private void complete(Query owned) { mongo.updateFirst(owned,new Update().set("pending",false).unset("lease").unset("leaseUntil"),"push_jobs"); }
    static void validateEndpoint(String endpoint) {
        if(endpoint==null || endpoint.length()>2048) throw new IllegalArgumentException("구독 주소가 유효하지 않습니다.");
        URI uri;
        try { uri=URI.create(endpoint); } catch(RuntimeException error) { throw new IllegalArgumentException("구독 주소가 유효하지 않습니다."); }
        String host=uri.getHost();
        boolean allowed=host!=null && (host.equals("fcm.googleapis.com") || host.equals("updates.push.services.mozilla.com") ||
                host.endsWith(".push.services.mozilla.com") || host.equals("web.push.apple.com") || host.endsWith(".notify.windows.com"));
        if(!"https".equals(uri.getScheme()) || !allowed || uri.getRawUserInfo()!=null || uri.getFragment()!=null ||
                (uri.getPort()!=-1 && uri.getPort()!=443)) throw new IllegalArgumentException("지원되는 브라우저의 HTTPS Push 구독만 사용할 수 있습니다.");
    }
    static void validateKeys(String key,String auth) {
        try {
            byte[] k=Base64.getUrlDecoder().decode(key),a=Base64.getUrlDecoder().decode(auth);
            if(k.length!=65 || k[0]!=4 || a.length!=16) throw new IllegalArgumentException();
            // SEC1 uncompressed P-256 points must actually lie on the curve, not just have the right length.
            var x=new java.math.BigInteger(1,Arrays.copyOfRange(k,1,33));
            var y=new java.math.BigInteger(1,Arrays.copyOfRange(k,33,65));
            var prime=new java.math.BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",16);
            var b=new java.math.BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",16);
            if(x.compareTo(prime)>=0 || y.compareTo(prime)>=0 ||
                    !y.multiply(y).mod(prime).equals(x.pow(3).subtract(x.multiply(java.math.BigInteger.valueOf(3))).add(b).mod(prime)))
                throw new IllegalArgumentException();
        } catch(RuntimeException failure) { throw new IllegalArgumentException("Push 암호화 키가 유효하지 않습니다."); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static final class Sender extends AbstractPushService<Sender> {
        private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        Sender(String publicKey,String privateKey,String subject) throws GeneralSecurityException { super(publicKey,privateKey,subject); }
        int deliver(String endpoint,String key,String auth,byte[] payload,int ttl) throws Exception {
            var prepared=prepareRequest(new Notification(endpoint,key,auth,payload,ttl),Encoding.AES128GCM);
            validateEndpoint(prepared.getUrl());
            var request=HttpRequest.newBuilder(URI.create(prepared.getUrl())).timeout(Duration.ofSeconds(5));
            prepared.getHeaders().forEach((k,v)-> { if(!k.equalsIgnoreCase("Content-Length")) request.header(k,v); });
            return http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(prepared.getBody())).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
