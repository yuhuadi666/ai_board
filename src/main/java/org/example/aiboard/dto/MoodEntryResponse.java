package org.example.aiboard.dto;

import java.time.Instant;
import org.example.aiboard.model.MoodEntry;

public record MoodEntryResponse(
        String id,
        String visitorId,
        String content,
        String warmReply,
        Instant createdAt,
        boolean completed
) {
    public static MoodEntryResponse from(MoodEntry e) {
        return new MoodEntryResponse(
                e.id(), e.visitorId(), e.content(), e.warmReply(), e.createdAt(), e.completed());
    }
}
