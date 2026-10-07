package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.security.ChatAccessService;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Service
public class AttachmentService {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long USER_QUOTA = 100L * 1024 * 1024;
    private final GridFsTemplate files;
    private final MongoTemplate mongo;
    private final ChatAccessService access;
    public record View(String id, String name, long size, String type) {}
    public AttachmentService(GridFsTemplate files, MongoTemplate mongo, ChatAccessService access) {
        this.files = files; this.mongo = mongo; this.access = access;
    }
    public View upload(Principal principal, String roomId, MultipartFile input) throws IOException {
        String owner = access.requireWritable(roomId, principal).loginId;
        if (input.isEmpty() || input.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일은 1바이트~10MB까지 가능합니다.");
        byte[] bytes = input.getBytes(); String type = sniff(bytes);
        String extension = switch (type) { case "image/png" -> ".png"; case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp"; case "application/pdf" -> ".pdf"; default -> ".txt"; };
        String name = safeName(input.getOriginalFilename(), extension);
        Query quota = Query.query(Criteria.where("_id").is(owner));
        try { mongo.upsert(quota, new Update().setOnInsert("bytes", 0L), "upload_quotas"); }
        catch (org.springframework.dao.DuplicateKeyException ignored) { /* Concurrent first upload. */ }
        long reserved = mongo.updateFirst(Query.query(Criteria.where("_id").is(owner).and("bytes").lte(USER_QUOTA - bytes.length)),
                new Update().inc("bytes", bytes.length), "upload_quotas").getModifiedCount();
        if (reserved != 1) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "계정의 첨부파일 용량 한도(100MB)에 도달했습니다.");
        try {
            ObjectId id = files.store(new ByteArrayInputStream(bytes), name, type,
                    new Document("owner", owner).append("roomId", roomId).append("createdAt", Date.from(Instant.now())));
            return new View(id.toHexString(), name, bytes.length, type);
        } catch (RuntimeException failure) {
            mongo.updateFirst(quota, new Update().inc("bytes", -bytes.length), "upload_quotas"); throw failure;
        }
    }
    public GridFSFile require(Principal principal, String id) {
        if (!ObjectId.isValid(id)) throw new IllegalArgumentException("파일 ID가 유효하지 않습니다.");
        GridFSFile file = files.findOne(Query.query(Criteria.where("_id").is(new ObjectId(id))));
        if (file == null || file.getMetadata() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다.");
        String roomId = file.getMetadata().getString("roomId"), owner = file.getMetadata().getString("owner");
        String viewer = access.requireMember(roomId, principal).loginId;
        Query attached = Query.query(Criteria.where("roomId").is(roomId).and("attachmentIds").is(id));
        boolean linked = mongo.exists(Query.query(new Criteria().andOperator(Criteria.where("roomId").is(roomId).and("attachmentIds").is(id), Criteria.where("deletedAt").is(null))), Message.class);
        boolean everLinked = mongo.exists(attached, Message.class);
        Date created = file.getMetadata().getDate("createdAt");
        // Only uploader may preview a not-yet-sent file. Deleting the message revokes normal downloads.
        if (!linked && (everLinked || !viewer.equals(owner) || created == null || created.toInstant().isBefore(Instant.now().minusSeconds(3600))))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "파일을 내려받을 권한이 없습니다.");
        return file;
    }
    public View view(GridFSFile file) {
        return new View(file.getObjectId().toHexString(), file.getFilename(), file.getLength(), file.getMetadata().getString("_contentType"));
    }
    public InputStream stream(GridFSFile file) throws IOException { return files.getResource(file).getInputStream(); }
    public void deletePending(Principal principal, String id) {
        GridFSFile file = require(principal, id);
        if (!file.getMetadata().getString("owner").equals(principal.getName()) ||
                mongo.exists(Query.query(Criteria.where("attachmentIds").is(id)), Message.class))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "전송된 파일은 메시지에서 삭제하세요.");
        // Atomic metadata removal races safely with MessageExtras' claim; never delete a file a send has claimed.
        Document removed=mongo.findAndRemove(Query.query(Criteria.where("_id").is(file.getObjectId())
                .and("metadata.owner").is(principal.getName()).and("metadata.claimed").ne(true)),Document.class,"fs.files");
        if(removed==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"전송 처리 중인 파일은 삭제할 수 없습니다.");
        mongo.remove(Query.query(Criteria.where("files_id").is(file.getObjectId())),"fs.chunks");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(principal.getName())), new Update().inc("bytes", -file.getLength()), "upload_quotas");
    }
    static String safeName(String value, String extension) {
        String name = value == null ? "attachment" : value.replaceAll("[\\p{Cntrl}\\\\/]", "_").strip();
        if (name.length() > 100) name = name.substring(0, 100);
        if (name.isBlank() || name.equals(".") || name.equals("..")) name = "attachment";
        if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) name += extension;
        return name;
    }
    static String sniff(byte[] b) {
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return "image/jpeg";
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if (b.length >= 5 && new String(b, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
            if (text.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) return "text/plain";
        } catch (CharacterCodingException ignored) { }
        throw new IllegalArgumentException("PNG/JPEG/WebP/PDF/UTF-8 텍스트 파일만 지원합니다.");
    }
}
