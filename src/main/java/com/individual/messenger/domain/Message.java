package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("messages")
@CompoundIndex(name = "room_time_idx", def = "{ 'roomId': 1, 'createdAt': -1 }")
public class Message {
    @Id
    public String id;

    public String roomId;
    public String senderId;      // 로그인 ID (JWT의 subject)
    public String senderName;    // 표시용 이름
    public String content;

    public Instant createdAt = Instant.now();

    // 확장: 읽음 처리용
    public List<String> readBy = new ArrayList<>();

    public Message(String roomId, String sender, String content) {
        this.roomId = roomId;
        this.senderId = sender;     // 혹은 senderName 등 실제 필드 이름에 맞게
        this.senderName = sender;
        this.content = content;
    }

    public Message() {

    }
}
