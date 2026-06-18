package org.example.aiboard.dto;

import java.util.List;

public record InitialRewriteResponse(
        String extractedResume,
        String rewrittenResume,
        List<ResumeChangeDto> changes
) {}
