package com.mac.alert.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.alert.config.properties.*;
import com.mac.alert.entities.constant.*;
import com.mac.alert.entities.model.*;
import com.mac.alert.repository.AlertRepository;
import com.mac.alert.service.EmailService;
import com.mac.alert.utils.*;
import com.mac.alert.utils.handler.AsyncExceptionHandler;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class AlertDispatchServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void dispatchesBatchesAndDirectAlertsSuccessfully() {
        Fixture fixture = new Fixture(Executors.newFixedThreadPool(2));
        ClaimedAlert first = new ClaimedAlert(UUID.randomUUID(), 1, 2);
        ClaimedAlert second = new ClaimedAlert(UUID.randomUUID(), 1, 2);
        when(fixture.repository.claimPendingAlerts(anyInt(), any(), anyString()))
                .thenReturn(List.of(first, second), List.of());
        when(fixture.repository.findMessageById(any())).thenReturn(message());
        when(fixture.emailService.send(any())).thenReturn(new EmailSendResult("message-id"));
        assertEquals(2, fixture.service.dispatchPendingAlerts(TriggerSource.SCHEDULER));
        verify(fixture.repository, times(2)).markSuccess(any(), eq(1), eq(TriggerSource.SCHEDULER),
                eq("message-id"), anyString(), eq(NOW), eq(NOW));

        when(fixture.repository.claimById(eq(first.alertId()), any(), anyString()))
                .thenReturn(Optional.of(first));
        when(fixture.repository.claimById(eq(second.alertId()), any(), anyString()))
                .thenReturn(Optional.empty());
        assertTrue(fixture.service.dispatchAlertById(first.alertId(), TriggerSource.API));
        assertFalse(fixture.service.dispatchAlertById(second.alertId(), TriggerSource.API));
        fixture.close();
    }

    @Test
    void mapsFailuresToRetryDeadAndFailed() {
        Fixture fixture = new Fixture(Executors.newSingleThreadExecutor());
        when(fixture.repository.findMessageById(any())).thenReturn(message());
        when(fixture.emailService.send(any())).thenThrow(new IllegalStateException("smtp"));

        ClaimedAlert retry = new ClaimedAlert(UUID.randomUUID(), 1, 2);
        when(fixture.repository.claimById(eq(retry.alertId()), any(), anyString())).thenReturn(Optional.of(retry));
        when(fixture.classifier.classify(any())).thenReturn(
                new DeliveryFailure(AlertErrorCode.UNKNOWN_ERROR, "temporary", "x", null));
        when(fixture.retry.calculate(1, NOW)).thenReturn(NOW.plusSeconds(1));
        assertTrue(fixture.service.dispatchAlertById(retry.alertId(), TriggerSource.API));
        verify(fixture.repository).markFailure(eq(retry.alertId()), eq(1), eq(TriggerSource.API), any(),
                eq("RETRY"), eq(NOW.plusSeconds(1)), anyString(), eq(NOW), eq(NOW));

        ClaimedAlert dead = new ClaimedAlert(UUID.randomUUID(), 3, 2);
        when(fixture.repository.claimById(eq(dead.alertId()), any(), anyString())).thenReturn(Optional.of(dead));
        assertTrue(fixture.service.dispatchAlertById(dead.alertId(), TriggerSource.KAFKA));
        verify(fixture.repository).markFailure(eq(dead.alertId()), eq(3), eq(TriggerSource.KAFKA), any(),
                eq("DEAD"), isNull(), anyString(), eq(NOW), eq(NOW));

        ClaimedAlert permanent = new ClaimedAlert(UUID.randomUUID(), 1, 2);
        when(fixture.repository.claimById(eq(permanent.alertId()), any(), anyString()))
                .thenReturn(Optional.of(permanent));
        when(fixture.classifier.classify(any())).thenReturn(
                new DeliveryFailure(AlertErrorCode.INVALID_RECIPIENT, "bad", "x", null));
        assertTrue(fixture.service.dispatchAlertById(permanent.alertId(), TriggerSource.API));
        verify(fixture.repository).markFailure(eq(permanent.alertId()), eq(1), eq(TriggerSource.API), any(),
                eq("FAILED"), isNull(), anyString(), eq(NOW), eq(NOW));
        fixture.close();
    }

    @Test
    void reportsUnexpectedTaskErrorsAndPreservesInterrupts() throws Exception {
        Fixture fixture = new Fixture(Executors.newSingleThreadExecutor());
        ClaimedAlert alert = new ClaimedAlert(UUID.randomUUID(), 1, 1);
        when(fixture.repository.claimPendingAlerts(anyInt(), any(), anyString()))
                .thenReturn(List.of(alert));
        when(fixture.repository.findMessageById(alert.alertId())).thenThrow(new AssertionError("fatal"));
        assertEquals(1, fixture.service.dispatchPendingAlerts(TriggerSource.SCHEDULER));
        verify(fixture.exceptionHandler).handle(anyString(), eq("centralized-alert.delivery"),
                eq("virtual-thread"), eq("processAlertTask"), anyMap(), any(AssertionError.class));
        fixture.close();

        ExecutorService interruptedExecutor = mock(ExecutorService.class);
        when(interruptedExecutor.invokeAll(anyCollection())).thenThrow(new InterruptedException("stop"));
        Fixture interrupted = new Fixture(interruptedExecutor);
        when(interrupted.repository.claimPendingAlerts(anyInt(), any(), anyString()))
                .thenReturn(List.of(alert));
        assertThrows(IllegalStateException.class,
                () -> interrupted.service.dispatchPendingAlerts(TriggerSource.SCHEDULER));
        assertTrue(Thread.interrupted());
    }

    private static AlertMessage message() {
        return new AlertMessage(UUID.randomUUID(), "sender@example.com", null, null, "subject", "body",
                AlertBodyType.TEXT, Map.of(), List.of("to@example.com"), List.of(), List.of(), List.of());
    }

    private static final class Fixture {
        final AlertRepository repository = mock(AlertRepository.class);
        final EmailService emailService = mock(EmailService.class);
        final FailureClassifier classifier = mock(FailureClassifier.class);
        final RetryDelayCalculator retry = mock(RetryDelayCalculator.class);
        final AsyncExceptionHandler exceptionHandler = mock(AsyncExceptionHandler.class);
        final ExecutorService executor;
        final AlertDispatchServiceImpl service;

        Fixture(ExecutorService executor) {
            this.executor = executor;
            service = new AlertDispatchServiceImpl(repository, emailService, classifier, retry,
                    new AlertPickupProperties(true, Duration.ofSeconds(1), Duration.ZERO, 4),
                    new AlertProcessingProperties(Duration.ofMinutes(1), Duration.ofSeconds(1),
                            Duration.ofMinutes(1), 2),
                    new WorkerIdentity("test-worker"), Clock.fixed(NOW, ZoneOffset.UTC), executor, exceptionHandler);
        }

        void close() {
            executor.shutdownNow();
        }
    }
}
