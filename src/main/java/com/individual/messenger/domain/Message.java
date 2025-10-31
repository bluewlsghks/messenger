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
    public String senderId;      // 濡쒓렇??ID (JWT??subject)
    public String senderName;    // ?쒖떆???대쫫
    public String content;

    public Instant createdAt = Instant.now();

    // ?뺤옣: ?쎌쓬 泥섎━??
    public List<String> readBy = new ArrayList<>();

    public Message(String roomId, String sender, String content) {
        this.roomId = roomId;
        this.senderId = sender;     // ?뱀? senderName ???ㅼ젣 ?꾨뱶 ?대쫫??留욊쾶
        this.senderName = sender;
        this.content = content;
    }

    public Message() {

    }
}


