package com.individual.messenger.service;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.individual.messenger.crypto.CryptoService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.function.*;

/** One bounded CAS document coordinates media occupancy across application instances. */
@Service
public class EphemeralStateStore {
    private final MongoTemplate mongo;
    private final ObjectMapper json;
    private final CryptoService crypto;
    private final Map<String,String> memory=new HashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public EphemeralStateStore(MongoTemplate mongo,ObjectMapper json,CryptoService crypto) {
        this.mongo=mongo;this.crypto=crypto;
        this.json=json.copy().setVisibility(PropertyAccessor.FIELD,JsonAutoDetect.Visibility.ANY);
    }
    private EphemeralStateStore() { this(null,new ObjectMapper().findAndRegisterModules(),null); }
    static EphemeralStateStore memory() { return new EphemeralStateStore(); }
    boolean persistent() { return mongo!=null; }
    public synchronized <T,R> R change(String key,Class<T> type,Supplier<T> initial,Function<T,R> operation) {
        for(int attempt=0;attempt<24;attempt++) {
            Document stored=mongo==null?null:mongo.findById(key,Document.class,"media_state");
            try {
                String value=mongo==null?memory.get(key):stored==null?null:crypto.decryptString(stored.getString("encrypted"));
                T state=value==null?initial.get():json.readValue(value,type);
                R result=operation.apply(state);
                String encoded=json.writeValueAsString(state);
                if(encoded.equals(value))return result; // Read-only polling need not rewrite the shared CAS document.
                if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4*1024*1024)
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"통화 신호가 밀려 있습니다. 잠시 후 다시 시도해 주세요.");
                if(mongo==null) { memory.put(key,encoded);return result; }
                long revision=stored==null?0:((Number)stored.get("revision")).longValue();
                String encrypted=crypto.encryptString(encoded); Date expiry=Date.from(Instant.now().plusSeconds(86400));
                if(stored==null) {
                    try { mongo.insert(new Document("_id",key).append("revision",1L).append("encrypted",encrypted).append("expiresAt",expiry),"media_state"); return result; }
                    catch(org.springframework.dao.DuplicateKeyException race) { continue; }
                }
                if(mongo.updateFirst(Query.query(Criteria.where("_id").is(key).and("revision").is(revision)),
                        new Update().set("encrypted",encrypted).inc("revision",1).set("expiresAt",expiry),"media_state").getModifiedCount()==1) return result;
            } catch(java.io.IOException invalidState) { throw new IllegalStateException("Media state could not be decoded",invalidState); }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,"통화 상태가 동시에 변경되었습니다. 다시 시도해 주세요.");
    }
    public void history(String id,Map<String,Object> fields) {
        if(mongo==null)return;
        Update insert=new Update();fields.forEach(insert::setOnInsert);
        insert.setOnInsert("expiresAt",Date.from(Instant.now().plusSeconds(30L*86400)));
        mongo.upsert(Query.query(Criteria.where("_id").is(id)),insert,"voice_history");
    }
    public List<Document> history(String user) {
        if(mongo==null)return List.of();
        return mongo.find(Query.query(Criteria.where("participants").is(user)).with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC,"endedAt")).limit(100),Document.class,"voice_history");
    }
}
