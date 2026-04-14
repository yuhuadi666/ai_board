package org.example.aiboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardRequest(
        @NotBlank @Size(max = 64) String visitorId,
        @Size(max = 500) String content
) {}
