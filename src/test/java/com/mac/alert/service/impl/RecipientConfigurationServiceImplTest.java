package com.mac.alert.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.model.CreateAlert;
import com.mac.alert.entities.model.RecipientConfiguration;
import com.mac.alert.repository.RecipientConfigurationRepository;
import com.mac.sdk_util.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipientConfigurationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private RecipientConfigurationRepository repository;
    private RecipientConfigurationServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(RecipientConfigurationRepository.class);
        service = new RecipientConfigurationServiceImpl(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsNormalizedGlobalRecipientWithDefaults() {
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new RecipientConfigurationRequest(
                "  ", RecipientType.TO, " Admin@Example.COM ", " Admin ", null));

        assertEquals("*", response.sourceSystem());
        assertEquals("admin@example.com", response.email());
        assertEquals("Admin", response.displayName());
        assertTrue(response.enabled());
        assertEquals(NOW, response.createdAt());
        verify(repository).exists("*", RecipientType.TO, "admin@example.com", null);
    }

    @Test
    void rejectsInvalidAndDuplicateCreateInput() {
        when(repository.exists(anyString(), any(), anyString(), isNull())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new RecipientConfigurationRequest("SOURCE", RecipientType.TO,
                        "user@example.com", null, true)));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                new RecipientConfigurationRequest("SOURCE", RecipientType.TO, " ", null, true)));
    }

    @Test
    void updatesAndDeletesExistingRecipient() {
        UUID id = UUID.randomUUID();
        RecipientConfiguration existing = configuration(id, "OLD", true, NOW.minusSeconds(60));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenReturn(true);
        when(repository.delete(id)).thenReturn(true);

        var response = service.update(id, new RecipientConfigurationRequest(
                " payment ", RecipientType.CC, " New@Example.com ", " ", false));

        assertEquals("PAYMENT", response.sourceSystem());
        assertEquals("new@example.com", response.email());
        assertNull(response.displayName());
        assertFalse(response.enabled());
        assertEquals(existing.createdAt(), response.createdAt());
        service.delete(id);

        verify(repository).exists("PAYMENT", RecipientType.CC, "new@example.com", id);
        verify(repository).delete(id);
    }

    @Test
    void reportsMissingRowsDuringUpdateAndDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(id,
                new RecipientConfigurationRequest("SOURCE", RecipientType.TO,
                        "user@example.com", null, true)));

        when(repository.findById(id)).thenReturn(Optional.of(configuration(id, "SOURCE", true, NOW)));
        when(repository.update(any())).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.update(id,
                new RecipientConfigurationRequest("SOURCE", RecipientType.TO,
                        "user@example.com", null, true)));

        when(repository.delete(id)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.delete(id));
    }

    @Test
    void listsWithNormalizedFilterAndValidatesPagination() {
        RecipientConfiguration item = configuration(UUID.randomUUID(), "PAYMENT", true, NOW);
        when(repository.findAll("PAYMENT", 20, 5)).thenReturn(List.of(item));
        assertEquals(1, service.findAll(" payment ", 20, 5).size());

        service.findAll(null, 200, 0);
        verify(repository).findAll(null, 200, 0);
        assertThrows(IllegalArgumentException.class, () -> service.findAll(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findAll(null, 201, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findAll(null, 10, -1));
    }

    @Test
    void configuredRecipientsOverridePayloadAndPayloadIsFallback() {
        var payload = List.of(new CreateAlert.Recipient(
                RecipientType.TO, "payload@example.com", "Payload"));
        RecipientConfiguration configured = configuration(
                UUID.randomUUID(), "PAYMENT", true, NOW);
        when(repository.findResolvedForSource("PAYMENT")).thenReturn(List.of(configured));

        var resolved = service.resolve(" payment ", payload);
        assertEquals("configured@example.com", resolved.getFirst().email());

        when(repository.findResolvedForSource("OTHER")).thenReturn(List.of());
        assertEquals(payload, service.resolve("OTHER", payload));
        assertTrue(service.resolve("OTHER", null).isEmpty());

        when(repository.findResolvedForSource("DISABLED")).thenReturn(List.of(
                configuration(UUID.randomUUID(), "DISABLED", false, NOW)));
        assertTrue(service.resolve("DISABLED", payload).isEmpty());
    }

    private static RecipientConfiguration configuration(
            UUID id, String source, boolean enabled, Instant createdAt) {
        return new RecipientConfiguration(id, source, RecipientType.TO,
                "configured@example.com", "Configured", enabled, createdAt, NOW);
    }
}
