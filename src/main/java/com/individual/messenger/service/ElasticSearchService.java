package com.individual.messenger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.individual.messenger.domain.Message;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Optional projection, never the source of authorization or deletion truth. */
@Service
public class ElasticSearchService implements MessageProjection {
    private final MongoTemplate mongo;
    private final ObjectMapper json;
    private final boolean enabled;
    private final URI base;
    private final String index,apiKey;
    private final HttpClient http;
    private volatile boolean initialized;
    public ElasticSearchService(MongoTemplate mongo,ObjectMapper json,
                                @Value("${app.search.enabled:false}") boolean enabled,
                                @Value("${app.search.url:http://127.0.0.1:9200}") String url,
                                @Value("${app.search.index:messenger-messages-v1}") String index,
                                @Value("${app.search.api-key:}") String apiKey) {
        this.mongo=mongo;this.json=json;this.enabled=enabled;this.index=index;this.apiKey=apiKey;
        this.base=URI.create(url.replaceAll("/+$",""));
        if(!index.matches("[a-z][a-z0-9-]{0,99}") || base.getHost()==null || base.getUserInfo()!=null ||
                base.getQuery()!=null || base.getFragment()!=null ||
                !(base.getScheme().equals("http") || base.getScheme().equals("https")) ||
                !(base.getPath().isEmpty() || base.getPath().equals("/"))) throw new IllegalArgumentException("Invalid Elasticsearch origin or index name");
        if(enabled && base.getScheme().equals("http") && !Set.of("localhost","127.0.0.1","elasticsearch").contains(base.getHost()))
            throw new IllegalArgumentException("Use HTTPS for remote Elasticsearch connections");
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public boolean enabled() { return enabled; }
    @Override public void publish(String type,Message message) {
        if(!enabled) return;
        ensureIndex();
        Map<String,Object> document=new LinkedHashMap<>();document.put("roomId",message.roomId);
        document.put("senderId",message.senderId);document.put("content",message.deletedAt==null?message.content:"");
        document.put("deleted",message.deletedAt!=null);document.put("createdAt",message.createdAt.toString());
        long version=Math.addExact(message.version,1);
        int status=send("PUT","/"+index+"/_doc/"+message.id+"?version="+version+"&version_type=external_gte",encode(document)).statusCode();
        // A newer projection wins. Keep tombstones in the index so old retries cannot resurrect deleted text.
        if(status!=409 && (status<200 || status>=300)) throw new IllegalStateException("Search projection could not be updated");
    }
    public Optional<List<String>> candidates(String room,String query) {
        if(!enabled || query.codePointCount(0,query.length())<2) return Optional.empty();
        try {
            ensureIndex();
            var body=Map.of("size",200,"_source",false,"sort",List.of(Map.of("createdAt","desc")),"query",
                    Map.of("bool",Map.of("filter",List.of(Map.of("term",Map.of("roomId",room)),Map.of("term",Map.of("deleted",false))),
                            "must",List.of(Map.of("match",Map.of("content",Map.of("query",query,"operator","and")))))));
            var response=send("POST","/"+index+"/_search",encode(body));
            if(response.statusCode()!=200) return Optional.empty();
            List<String> ids=new ArrayList<>();
            for(JsonNode hit:json.readTree(response.body()).path("hits").path("hits")) {
                String id=hit.path("_id").asText();if(ObjectId.isValid(id)) ids.add(id);
            }
            return Optional.of(ids);
        } catch(Exception unavailable) { return Optional.empty(); }
    }
    private synchronized void ensureIndex() {
        if(initialized) return;
        try(var input=new ClassPathResource("search/messages-index.json").getInputStream()) {
            var result=send("PUT","/"+index,new String(input.readAllBytes(),StandardCharsets.UTF_8));
            if(result.statusCode()!=200 && !(result.statusCode()==400 && result.body().contains("resource_already_exists_exception")))
                throw new IllegalStateException("Elasticsearch index initialization failed");
            initialized=true;
        } catch(java.io.IOException failure) { throw new IllegalStateException("Elasticsearch mapping is missing",failure); }
    }
    public void requestReindex() {
        if(!enabled) throw new IllegalArgumentException("Elasticsearch가 비활성화되어 있습니다.");
        String generation=UUID.randomUUID().toString();
        mongo.upsert(Query.query(Criteria.where("_id").is(index)),new Update().set("pending",true).set("cursor","")
                .set("generation",generation).set("indexed",0L).unset("lease").unset("leaseUntil"),"search_reindex");
    }
    public Map<String,Object> status() {
        Document job=mongo.findById(index,Document.class,"search_reindex");
        return Map.of("enabled",enabled,"index",index,"reindex",job==null?Map.of():job);
    }
    @Scheduled(fixedDelay=3000,initialDelay=10000)
    public void reindexPage() {
        if(!enabled) return;
        String lease=UUID.randomUUID().toString();Instant now=Instant.now();
        Criteria ready=new Criteria().andOperator(Criteria.where("_id").is(index).and("pending").is(true),
                new Criteria().orOperator(Criteria.where("leaseUntil").is(null),Criteria.where("leaseUntil").lt(Date.from(now))));
        Document job=mongo.findAndModify(Query.query(ready),new Update().set("lease",lease).set("leaseUntil",Date.from(now.plusSeconds(60))),
                FindAndModifyOptions.options().returnNew(true),Document.class,"search_reindex");
        if(job==null)return;
        Query owned=Query.query(Criteria.where("_id").is(index).and("lease").is(lease).and("generation").is(job.getString("generation")));
        try {
            String cursor=job.getString("cursor");Query query=new Query().with(Sort.by("id")).limit(20);
            if(cursor!=null && !cursor.isEmpty())query.addCriteria(Criteria.where("id").gt(cursor));
            List<Message> page=mongo.find(query,Message.class);
            for(Message message:page) {
                publish("REINDEX",message);
                mongo.updateFirst(owned,new Update().set("cursor",message.id).inc("indexed",1).set("leaseUntil",Date.from(Instant.now().plusSeconds(60))),"search_reindex");
            }
            mongo.updateFirst(owned,new Update().set("pending",page.size()==20).unset("lease").unset("leaseUntil"),"search_reindex");
        } catch(RuntimeException error) {
            mongo.updateFirst(owned,new Update().unset("lease").set("leaseUntil",Date.from(Instant.now().plusSeconds(30))),"search_reindex");
        }
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(java.io.IOException failure){throw new IllegalArgumentException(failure);} }
    private HttpResponse<String> send(String method,String path,String body) {
        try {
            var builder=HttpRequest.newBuilder(URI.create(base.toString()+path)).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json");
            if(!apiKey.isBlank())builder.header("Authorization","ApiKey "+apiKey);
            return http.send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        } catch(InterruptedException failure) { Thread.currentThread().interrupt();throw new IllegalStateException("Search interrupted",failure); }
        catch(java.io.IOException failure) { throw new IllegalStateException("Search unavailable",failure); }
    }
}
