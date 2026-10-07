package com.individual.messenger.domain;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;
@Document("contact_relations")
public class ContactRelation {
    @Id public String id;
    @Version public Long version;
    public List<String> members = new ArrayList<>();
    public String requester;
    public String state = "NONE";
    public Set<String> blockedBy = new HashSet<>();
    public Instant updatedAt = Instant.now();
}
