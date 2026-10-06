package com.individual.messenger.service;

import com.individual.messenger.repository.ChatServerRepository;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.repository.UnreadMessageRepository;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class NotificationService {
    private final RoomRepository rooms;
    private final ChatServerRepository servers;
    private final ChatAccessService access;
    private final UnreadMessageRepository unread;
    public NotificationService(RoomRepository rooms, ChatServerRepository servers,
                               ChatAccessService access, UnreadMessageRepository unread) {
        this.rooms = rooms; this.servers = servers; this.access = access; this.unread = unread;
    }
    public Map<String, Long> unread(Principal principal) {
        String me = access.actor(principal).loginId;
        Set<String> ids = new LinkedHashSet<>();
        rooms.findByMembersContaining(me).forEach(room -> ids.add(room.id));
        servers.findByMembersContainingOrderByCreatedAtAsc(me)
                .forEach(server -> server.channels.forEach(channel -> ids.add(channel.id)));
        return unread.count(ids, me);
    }
}
