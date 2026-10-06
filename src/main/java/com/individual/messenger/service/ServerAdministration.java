package com.individual.messenger.service;

import com.individual.messenger.domain.ChatServer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ServerAdministration {
    private final ChatServerService servers; private final MongoTemplate mongo;
    public ServerAdministration(ChatServerService servers, MongoTemplate mongo) { this.servers = servers; this.mongo = mongo; }
    private void mutate(String serverId, String actor, Consumer<ChatServer> change) {
        for (int attempt = 0; attempt < 5; attempt++) {
            ChatServer s = servers.requireMember(serverId, actor); long version = s.revision;
            change.accept(s);
            Criteria revision = version == 0 ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").exists(false))
                    : Criteria.where("revision").is(version);
            Query query = Query.query(new Criteria().andOperator(Criteria.where("id").is(serverId).and("deleted").ne(true), revision));
            Update update = new Update().set("ownerId", s.ownerId).set("members", s.members).set("moderators", s.moderators)
                    .set("banned", s.banned).set("channels", s.channels).set("deleted", s.deleted)
                    .set("inviteVersion", s.inviteVersion).inc("revision", 1);
            if (mongo.updateFirst(query, update, ChatServer.class).getModifiedCount() == 1) return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "서버가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
    }
    public void command(String serverId, String actor, String action, String target) {
        mutate(serverId, actor, s -> {
            boolean owner = actor.equals(s.ownerId), moderator = s.moderators.contains(actor);
            if (action.equals("LEAVE")) {
                if (owner) throw bad("소유권을 이전한 뒤 탈퇴하거나 서버를 삭제하세요.");
                s.members.remove(actor); s.moderators.remove(actor); return;
            }
            if (action.equals("KICK") || action.equals("BAN")) {
                if ((!owner && !moderator) || Objects.equals(target, s.ownerId) || actor.equals(target)
                        || (!owner && s.moderators.contains(target))) throw forbidden();
                if (target == null || !s.members.contains(target)) throw bad("현재 멤버를 선택하세요.");
                s.members.remove(target); s.moderators.remove(target);
                if (action.equals("BAN") && !s.banned.contains(target)) s.banned.add(target);
                return;
            }
            if (!owner) throw forbidden();
            switch (action) {
                case "DELETE" -> { s.deleted = true; s.inviteVersion++; }
                case "TRANSFER" -> {
                    if (target == null || !s.members.contains(target) || s.banned.contains(target)) throw bad("현재 멤버에게만 이전할 수 있습니다.");
                    s.ownerId = target; s.moderators.remove(target); s.inviteVersion++;
                }
                case "PROMOTE", "DEMOTE" -> {
                    if (target == null || !s.members.contains(target) || target.equals(s.ownerId)) throw bad("일반 멤버를 선택하세요.");
                    s.moderators.remove(target); if (action.equals("PROMOTE")) s.moderators.add(target);
                }
                case "REVOKE_INVITES" -> s.inviteVersion++;
                case "UNBAN" -> s.banned.remove(target);
                default -> throw bad("지원하지 않는 관리 명령입니다.");
            }
        });
    }
    public void permissions(String serverId, String actor, String channelId, List<String> readers, List<String> writers) {
        mutate(serverId, actor, s -> {
            if (!actor.equals(s.ownerId)) throw forbidden();
            if ((readers != null && (readers.size() > 500 || !s.members.containsAll(readers)))
                    || (writers != null && (writers.size() > 500 || !s.members.containsAll(writers)))) throw bad("현재 멤버만 권한을 받을 수 있습니다.");
            var c = s.channels.stream().filter(v -> v.id.equals(channelId)).findFirst().orElseThrow(() -> bad("채널을 찾을 수 없습니다."));
            c.readers = readers == null ? null : readers.stream().distinct().toList();
            c.writers = writers == null ? null : writers.stream().distinct().toList();
        });
    }
    private ResponseStatusException forbidden() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "관리 권한이 없습니다."); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
