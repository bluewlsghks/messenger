package com.individual.messenger.service;
import com.individual.messenger.domain.Message;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only counts; never returns IDs, passwords, phone values, keys, or message content. */
@Service
public class OperationsService {
    private final MongoTemplate mongo;
    public OperationsService(MongoTemplate mongo){this.mongo=mongo;}
    public Map<String,Object> audit() {
        var users=mongo.getCollection("users");
        long duplicates=0;
        try(var cursor=users.aggregate(List.of(new Document("$match",new Document("loginId",new Document("$type","string"))),
                new Document("$group",new Document("_id","$loginId").append("n",new Document("$sum",1))),
                new Document("$match",new Document("n",new Document("$gt",1))),new Document("$count","groups"))).iterator()) {
            if(cursor.hasNext())duplicates=((Number)cursor.next().get("groups")).longValue();
        }
        boolean unique=false;
        for(Document index:users.listIndexes()) {
            Document keys=index.get("key",Document.class);
            if(Boolean.TRUE.equals(index.getBoolean("unique")) && keys!=null && keys.size()==1 && keys.containsKey("loginId")) unique=true;
        }
        return Map.of("duplicateLoginIdGroups",duplicates,"loginIdUniqueIndex",unique,
                "legacyPlainPasswordDocuments",users.countDocuments(new Document("legacyPassword",new Document("$type","string"))),
                "legacyPlainPhoneDocuments",users.countDocuments(new Document("legacyPhoneNumber",new Document("$type","string"))),
                "invalidLoginIdDocuments",users.countDocuments(new Document("$nor",List.of(new Document("loginId",new Document("$type","string").append("$regex","^[A-Za-z0-9_-]{3,64}$"))))),
                "outboxPending",mongo.getCollection("messages").countDocuments(new Document("publicationPending",true)),
                "attachmentBytes",totalAttachmentBytes(),
                "notes","Read-only audit. Existing documents, keys and indexes were not deleted or normalized.");
    }
    private long totalAttachmentBytes() {
        Document sum=mongo.getCollection("fs.files").aggregate(List.of(new Document("$group",new Document("_id",null)
                .append("bytes",new Document("$sum","$length"))))).first();
        return sum==null?0:((Number)sum.get("bytes")).longValue();
    }
}
