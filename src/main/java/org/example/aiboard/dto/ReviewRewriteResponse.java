package org.example.aiboard.dto;

import java.util.List;

public record ReviewRewriteResponse(
        CriticReportDto criticReport,
        String rewrittenResume,
        List<ResumeChangeDto> changes
) {}
