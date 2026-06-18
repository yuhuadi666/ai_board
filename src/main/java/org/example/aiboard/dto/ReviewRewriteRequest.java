package org.example.aiboard.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ReviewRewriteRequest(
        @NotBlank String jd,
        @NotBlank String currentResume,
        List<ResumeChangeDto> confirmedChanges
) {}
