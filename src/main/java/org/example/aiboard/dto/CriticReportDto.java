package org.example.aiboard.dto;

import java.util.List;

public record CriticReportDto(
        int jdMatchScore,
        int completenessScore,
        List<String> strengths,
        List<String> risks,
        List<String> missingItems
) {}
