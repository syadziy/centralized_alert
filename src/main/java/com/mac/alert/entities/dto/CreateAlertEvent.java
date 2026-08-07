package com.mac.alert.entities.dto;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAlertEvent(

        @NotBlank
        String eventId,

        @NotNull
        Instant occurredAt,

        @NotNull
        @Valid
        CreateAlertRequest data
) {
}