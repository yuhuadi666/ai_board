package org.example.aiboard.dto;

import java.util.List;
import org.example.aiboard.model.MoodEntry;

public record BoardResponse(List<MoodEntryResponse> entries) {
    public static BoardResponse of(List<MoodEntry> entries) {
        return new BoardResponse(entries.stream().map(MoodEntryResponse::from).toList());
    }
}
