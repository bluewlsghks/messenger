package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

/** Bounded, coalescing state outbox. At-least-once submission, not exactly-once browser receipt. */
@Service
public class MessageOutbox {
    private final MongoTemplate mongo;
    private final ObjectProvider<ConversationEvents> events;
    private final ObjectProvider<SimpMessagingTemplate> broker;
    private final ObjectProvider<MessageProjection> projections;
    private volatile boolean available;
    public MessageOutbox(MongoTemplate mongo, ObjectProvider<ConversationEvents> events,
                         ObjectProvider<SimpMessagingTemplate> broker, ObjectProvider<MessageProjection> projections) {
        this.mongo = mongo; this.events = events; this.broker = broker; this.projections = projections;
    }
    @EventListener public void availability(BrokerAvailabilityEvent event) { available = event.isBrokerAvailable(); }
    @Scheduled(fixedDelayString = "${app.outbox.delay-ms:500}", initialDelay = 1000)
    public void drain() {
        if (!available) return;
        for (int i = 0; i < 25; i++) if (!deliverOne()) break;
    }
    public boolean deliverOne() {
        Instant now = Instant.now(); String lease = UUID.randomUUID().toString();
        Criteria ready = new Criteria().andOperator(Criteria.where("publicationPending").is(true),
                new Criteria().orOperator(Criteria.where("publishAfter").lte(now), Criteria.where("publishAfter").is(null)),
                new Criteria().orOperator(Criteria.where("publicationLeaseUntil").lte(now), Criteria.where("publicationLeaseUntil").is(null)));
        Message message = mongo.findAndModify(Query.query(ready).with(Sort.by("publishAfter", "id")),
                new Update().set("publicationLease", lease).set("publicationLeaseUntil", now.plusSeconds(30))
                        .inc("publicationAttempts", 1), FindAndModifyOptions.options().returnNew(true), Message.class);
        if (message == null) return false;
        Query owned = Query.query(Criteria.where("id").is(message.id).and("publicationLease").is(lease));
        try {
            String type = message.deletedAt != null ? "MESSAGE_DELETED" :
                    message.creationPublished ? "MESSAGE_UPDATED" : "MESSAGE_CREATED";
            events.getObject().publish(type, message);
            broker.getObject().convertAndSend("/topic/chat/" + message.roomId, message);
            for (MessageProjection projection : projections.orderedStream().toList()) projection.publish(type, message);
            // Never acknowledge a newer edit made while this snapshot was being delivered.
            Criteria version = message.version == 0 ? new Criteria().orOperator(Criteria.where("version").is(0),
                    Criteria.where("version").exists(false)) : Criteria.where("version").is(message.version);
            mongo.updateFirst(Query.query(new Criteria().andOperator(Criteria.where("id").is(message.id).and("publicationLease").is(lease), version)),
                    new Update().set("publicationPending", false).set("creationPublished", true)
                            .unset("publicationLease").unset("publicationLeaseUntil"), Message.class);
            mongo.updateFirst(owned, new Update().unset("publicationLease").unset("publicationLeaseUntil"), Message.class);
        } catch (RuntimeException failure) {
            long backoff = Math.min(300, 1L << Math.min(8, message.publicationAttempts));
            mongo.updateFirst(owned, new Update().set("publishAfter", now.plusSeconds(backoff))
                    .unset("publicationLease").unset("publicationLeaseUntil"), Message.class);
            // Do not log message content, tokens, SDP, or destination credentials.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox publication will retry: {}", failure.getClass().getSimpleName());
        }
        return true;
    }
}
