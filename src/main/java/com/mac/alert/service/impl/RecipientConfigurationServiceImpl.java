package com.mac.alert.service.impl;

import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.dto.RecipientConfigurationResponse;
import com.mac.alert.entities.constant.AlertLogFields;
import com.mac.alert.entities.model.CreateAlert;
import com.mac.alert.entities.model.RecipientConfiguration;
import com.mac.alert.repository.RecipientConfigurationRepository;
import com.mac.alert.service.RecipientConfigurationService;
import com.mac.sdk_util.exception.ResourceNotFoundException;
import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RecipientConfigurationServiceImpl implements RecipientConfigurationService {

    private static final String GLOBAL_SOURCE = "*";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecipientConfigurationServiceImpl.class);
    private final RecipientConfigurationRepository repository;
    private final Clock clock;

    public RecipientConfigurationServiceImpl(
            RecipientConfigurationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RecipientConfigurationResponse create(RecipientConfigurationRequest request) {
        String sourceSystem = normalizeSourceSystem(request.sourceSystem());
        String email = normalizeEmail(request.email());
        rejectDuplicate(sourceSystem, request, email, null);
        Instant now = clock.instant();
        RecipientConfiguration created = repository.insert(new RecipientConfiguration(
                UUID.randomUUID(), sourceSystem, request.type(), email,
                trimToNull(request.displayName()), enabled(request), now, now));
        logChange("createRecipientConfiguration", created);
        return toResponse(created);
    }

    @Override
    @Transactional
    public RecipientConfigurationResponse update(UUID id, RecipientConfigurationRequest request) {
        RecipientConfiguration existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient configuration not found"));
        String sourceSystem = normalizeSourceSystem(request.sourceSystem());
        String email = normalizeEmail(request.email());
        rejectDuplicate(sourceSystem, request, email, id);
        RecipientConfiguration updated = new RecipientConfiguration(
                id, sourceSystem, request.type(), email, trimToNull(request.displayName()),
                enabled(request), existing.createdAt(), clock.instant());
        if (!repository.update(updated)) {
            throw new ResourceNotFoundException("Recipient configuration not found");
        }
        logChange("updateRecipientConfiguration", updated);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!repository.delete(id)) {
            throw new ResourceNotFoundException("Recipient configuration not found");
        }
        StructuredLog.info(LOGGER, "Recipient configuration deleted", Map.of(
                LogFields.EVENT_ACTION, "deleteRecipientConfiguration",
                LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                LogFields.EVENT_DATASET, "centralized-alert.recipient-configuration",
                AlertLogFields.RECIPIENT_CONFIGURATION_ID, id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipientConfigurationResponse> findAll(String sourceSystem, int limit, int offset) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        String normalizedSource = sourceSystem == null || sourceSystem.isBlank()
                ? null
                : normalizeSourceSystem(sourceSystem);
        return repository.findAll(normalizedSource, limit, offset).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreateAlert.Recipient> resolve(
            String sourceSystem, List<CreateAlert.Recipient> requestRecipients) {
        List<RecipientConfiguration> configured =
                repository.findResolvedForSource(normalizeSourceSystem(sourceSystem));
        if (configured.isEmpty()) {
            return requestRecipients == null ? List.of() : List.copyOf(requestRecipients);
        }
        return configured.stream()
                .filter(RecipientConfiguration::enabled)
                .map(configuration -> new CreateAlert.Recipient(
                        configuration.type(), configuration.email(), configuration.displayName()))
                .toList();
    }

    private void rejectDuplicate(
            String sourceSystem,
            RecipientConfigurationRequest request,
            String email,
            UUID excludedId) {
        if (repository.exists(sourceSystem, request.type(), email, excludedId)) {
            throw new IllegalArgumentException(
                    "Recipient configuration already exists for sourceSystem, type, and email");
        }
    }

    private RecipientConfigurationResponse toResponse(RecipientConfiguration configuration) {
        return new RecipientConfigurationResponse(
                configuration.id(), configuration.sourceSystem(), configuration.type(),
                configuration.email(), configuration.displayName(), configuration.enabled(),
                configuration.createdAt(), configuration.updatedAt());
    }

    private static String normalizeSourceSystem(String value) {
        if (value == null || value.isBlank() || GLOBAL_SOURCE.equals(value.trim())) {
            return GLOBAL_SOURCE;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean enabled(RecipientConfigurationRequest request) {
        return request.enabled() == null || request.enabled();
    }

    private static void logChange(String action, RecipientConfiguration configuration) {
        StructuredLog.info(LOGGER, "Recipient configuration changed", Map.of(
                LogFields.EVENT_ACTION, action,
                LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                LogFields.EVENT_DATASET, "centralized-alert.recipient-configuration",
                AlertLogFields.RECIPIENT_CONFIGURATION_ID, configuration.id(),
                AlertLogFields.RECIPIENT_CONFIGURATION_SOURCE, configuration.sourceSystem(),
                AlertLogFields.RECIPIENT_CONFIGURATION_TYPE, configuration.type(),
                AlertLogFields.RECIPIENT_CONFIGURATION_ENABLED, configuration.enabled()));
    }
}
