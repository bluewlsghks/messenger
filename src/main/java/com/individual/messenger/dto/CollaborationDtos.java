package com.individual.messenger.dto;

import com.individual.messenger.domain.Message;
import java.util.List;

public final class CollaborationDtos {
    private CollaborationDtos() {}
    public record Page(List<Message> items, String next, boolean hasMore) {}
    public record Changes(List<Message> items, String cursor, boolean reset, boolean hasMore) {}
    public record Capabilities(boolean canReact, boolean canPin) {}
    public record BookmarkState(List<String> ids) {}
}
