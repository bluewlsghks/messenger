package com.individual.messenger.rt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/sse")
public class SseController {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Client: GET /api/sse/rooms/{roomId}?me=USER */
    @GetMapping(value = "/rooms/{roomId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String roomId, @RequestParam String me) {
        String key = roomId + "#" + me;
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(key, emitter);
        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> emitters.remove(key));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("at", Instant.now().toString())));
        } catch (IOException ignored) {}
        return emitter;
    }

    public void push(String roomId, String username, Object payload) {
        String key = roomId + "#" + username;
        SseEmitter emitter = emitters.get(key);
        if (emitter != null) {
            try { emitter.send(SseEmitter.event().name("message").data(payload)); }
            catch (IOException e) { emitter.completeWithError(e); emitters.remove(key); }
        }
    }
}
