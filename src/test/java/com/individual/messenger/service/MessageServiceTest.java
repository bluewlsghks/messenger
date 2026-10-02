package com.individual.messenger.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {
    @Test void pageLimitMustBePositiveAndBounded() {
        for (int size : new int[]{-1, 0, 101, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> MessageService.historyQuery("room", null, null, size));
        }
    }
    @Test void cursorRequiresValidIdAndTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> MessageService.historyQuery("room", null, "0123456789abcdef01234567", 50));
        assertThrows(IllegalArgumentException.class, () -> MessageService.historyQuery("room", Instant.now(), "invalid", 50));
    }
    @Test void cursorHasTimestampTieBreakerAndStableSort() {
        var query = MessageService.historyQuery("room", Instant.parse("2026-01-01T00:00:00Z"), "0123456789abcdef01234567", 50);
        assertTrue(query.getQueryObject().containsKey("$and"));
        assertEquals(-1, query.getSortObject().get("createdAt"));
        assertEquals(-1, query.getSortObject().get("id"));
        assertEquals(50, query.getLimit());
    }
    @Test void contentPreservesLineBreaksAndValidatesBeforeTrimming() {
        assertEquals("한글\n메시지", MessageService.validateContent("  한글\n메시지  "));
        assertThrows(IllegalArgumentException.class, () -> MessageService.validateContent("x" + " ".repeat(4000)));
    }
}
