package org.example.aiboard.dto;

public record ResumeChangeDto(
        String id,
        String section,
        String beforeText,
        String afterText,
        String reason
) {}
