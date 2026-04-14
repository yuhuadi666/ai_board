package org.example.aiboard.model;

import java.time.Instant;
import java.util.UUID;

public record MoodEntry(
        String id,
        String visitorId,
        String content,
        String warmReply,
        Instant createdAt,
        boolean completed
) {
    public static MoodEntry create(String visitorId, String content, String warmReply) {
        return new MoodEntry(
                UUID.randomUUID().toString(),
                visitorId,
                content,
                warmReply,
                Instant.now(),
                false
        );
    }

    public MoodEntry withCompleted(boolean value) {
        return new MoodEntry(id, visitorId, content, warmReply, createdAt, value);
    }
}
