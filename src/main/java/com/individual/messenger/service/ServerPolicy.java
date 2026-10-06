package com.individual.messenger.service;
import com.individual.messenger.domain.ChatServer;
public final class ServerPolicy {
    private ServerPolicy() {}
    public static boolean member(ChatServer s, String id) { return !s.deleted && s.members != null && s.members.contains(id) && !s.banned.contains(id); }
    public static boolean read(ChatServer s, ChatServer.TextChannel c, String id) {
        return member(s, id) && (id.equals(s.ownerId) || c.readers == null || c.readers.contains(id));
    }
    public static boolean write(ChatServer s, ChatServer.TextChannel c, String id) {
        return read(s, c, id) && (id.equals(s.ownerId) || c.writers == null || c.writers.contains(id));
    }
}
