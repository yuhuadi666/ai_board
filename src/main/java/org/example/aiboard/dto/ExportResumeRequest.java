package org.example.aiboard.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ExportResumeRequest(
        @NotBlank String resumeText,
        List<ResumeChangeDto> acceptedChanges
) {}
