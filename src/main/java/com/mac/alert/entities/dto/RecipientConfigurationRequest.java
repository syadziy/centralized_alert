package com.mac.alert.entities.dto;

import com.mac.alert.entities.constant.RecipientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecipientConfigurationRequest(
        @Size(max = 100) String sourceSystem,
        @NotNull RecipientType type,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 150) String displayName,
        Boolean enabled) {}
