package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.dto.CollaborationDtos.Changes;
import com.individual.messenger.repository.MessageCollaborationRepository;
import com.individual.messenger.repository.MessageJournalRepository;
import com.individual.messenger.repository.MessageJournalRepository.Snapshot;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

/** Durable outbox projection; duplicate journal entries are harmless current-state invalidations. */
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MessageSyncService implements MessageProjection {
    private final MessageJournalRepository journal;
    private final MessageCollaborationRepository repository;
    private final MessageService messages;
    private final ChatAccessService access;
    public MessageSyncService(MessageJournalRepository journal, MessageCollaborationRepository repository,
                              MessageService messages, ChatAccessService access) {
        this.journal = journal; this.repository = repository; this.messages = messages; this.access = access;
    }
    @Override public void publish(String type, Message message) { journal.append(message.roomId, message.id); }

    public Changes changes(Principal actor, String room, String cursor, int limit) {
        access.requireMember(room, actor);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("조회 개수는 1~100이어야 합니다.");
        if (cursor != null && (cursor.length() > 60 || !cursor.matches("[0-9a-f-]{36}:[0-9]{1,19}")))
            throw new IllegalArgumentException("동기화 커서가 유효하지 않습니다.");
        // Capture BEFORE the initial history query; a racing write must remain discoverable afterwards.
        Snapshot snapshot = journal.snapshot(room);
        long after = -1;
        if (cursor != null) {
            try { after = Long.parseLong(cursor.substring(37)); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("동기화 커서가 유효하지 않습니다."); }
        }
        long first = snapshot.sequence() - snapshot.ids().size();
        boolean reset = cursor == null || !cursor.startsWith(snapshot.epoch() + ":")
                || after < first || after > snapshot.sequence();
        Changes result;
        if (reset) {
            // Reset means replace the in-memory cache, not append an incomplete snapshot to stale messages.
            result = new Changes(messages.history(room, null, null, 100), token(snapshot, snapshot.sequence()), true, false);
        } else {
            int start = (int) (after - first);
            int end = Math.min(snapshot.ids().size(), start + limit);
            List<String> ids = snapshot.ids().subList(start, end).stream().distinct().toList();
            List<Message> items = ids.isEmpty() ? List.of() : repository.current(room, ids, true);
            long next = first + end;
            result = new Changes(items, token(snapshot, next), false, next < snapshot.sequence());
        }
        access.requireMember(room, actor);
        return result;
    }
    private static String token(Snapshot snapshot, long sequence) { return snapshot.epoch() + ":" + sequence; }
}
