package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MessageExtras {
    public record Prepared(String threadId, List<String> attachmentIds, List<String> mentions) {}
    private final MongoTemplate mongo;
    private final ChatAccessService access;
    public MessageExtras(MongoTemplate mongo, ChatAccessService access) { this.mongo = mongo; this.access = access; }
    public Prepared prepare(String room, String sender, String text, String reply, List<String> fileIds) {
        String thread = null;
        if (reply != null) {
            Message parent = mongo.findOne(Query.query(Criteria.where("id").is(reply).and("roomId").is(room)), Message.class);
            if (parent == null || parent.deletedAt != null) throw new IllegalArgumentException("답장할 메시지를 찾을 수 없습니다.");
            thread = parent.threadId == null ? parent.id : parent.threadId;
        }
        List<String> files = fileIds == null ? List.of() : fileIds.stream().distinct().toList();
        if (files.size() > 5) throw new IllegalArgumentException("첨부파일은 최대 5개입니다.");
        for (String file : files) {
            if (!org.bson.types.ObjectId.isValid(file)) throw new IllegalArgumentException("첨부파일 ID가 유효하지 않습니다.");
            var stored = mongo.findOne(Query.query(Criteria.where("_id").is(new org.bson.types.ObjectId(file))
                    .and("metadata.owner").is(sender).and("metadata.roomId").is(room)), org.bson.Document.class, "fs.files");
            if (stored == null || mongo.updateFirst(Query.query(Criteria.where("_id").is(new org.bson.types.ObjectId(file))
                    .and("metadata.owner").is(sender).and("metadata.roomId").is(room)),
                    new Update().set("metadata.claimed",true),"fs.files").getMatchedCount()!=1)
                throw new IllegalArgumentException("이 대화에 업로드한 본인 파일만 첨부할 수 있습니다.");
        }
        Set<String> mentions = new LinkedHashSet<>();
        var match = Pattern.compile("(?<![\\p{L}\\p{N}_])@([\\p{L}\\p{N}_-]{1,64})").matcher(text);
        while (match.find() && mentions.size() < 20) {
            String id = match.group(1);
            try { access.requireMember(room, () -> id); mentions.add(id); }
            catch (org.springframework.web.server.ResponseStatusException ignored) { /* Not a current member. */ }
        }
        return new Prepared(thread, files, List.copyOf(mentions));
    }
}
