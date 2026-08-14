package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record OrgCreateEventRequest(
        @NotBlank String teamId,
        @NotNull UUID courseId,
        @NotBlank String eventType,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate eventDate
) {
}
