package com.mac.alert.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.mac.alert.config.properties.AlertPickupProperties;
import com.mac.alert.config.properties.AlertProcessingProperties;
import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.model.ClaimedAlert;
import com.mac.alert.entities.model.DeliveryFailure;
import com.mac.alert.repository.AlertRepository;
import com.mac.alert.service.AlertDispatchService;
import com.mac.alert.service.EmailService;
import com.mac.alert.utils.FailureClassifier;
import com.mac.alert.utils.RetryDelayCalculator;
import com.mac.alert.utils.WorkerIdentity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AlertDispatchServiceImpl
        implements AlertDispatchService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AlertDispatchServiceImpl.class
            );

    private final AlertRepository alertRepository;
    private final EmailService emailService;
    private final FailureClassifier failureClassifier;
    private final RetryDelayCalculator retryDelayCalculator;
    private final AlertPickupProperties pickupProperties;
    private final AlertProcessingProperties processingProperties;
    private final WorkerIdentity workerIdentity;
    private final Clock clock;
    private final ExecutorService alertExecutor;

    public AlertDispatchServiceImpl(
            AlertRepository alertRepository,
            EmailService emailService,
            FailureClassifier failureClassifier,
            RetryDelayCalculator retryDelayCalculator,
            AlertPickupProperties pickupProperties,
            AlertProcessingProperties processingProperties,
            WorkerIdentity workerIdentity,
            Clock clock,
            @Qualifier("alertVirtualThreadExecutor")
            ExecutorService alertExecutor
    ) {
        this.alertRepository = alertRepository;
        this.emailService = emailService;
        this.failureClassifier = failureClassifier;
        this.retryDelayCalculator = retryDelayCalculator;
        this.pickupProperties = pickupProperties;
        this.processingProperties = processingProperties;
        this.workerIdentity = workerIdentity;
        this.clock = clock;
        this.alertExecutor = alertExecutor;
    }

    @Override
    public int dispatchPendingAlerts(
            TriggerSource triggerSource
    ) {
        String workerId = workerIdentity.getWorkerId();

        int maximumBatchSize =
                pickupProperties.batchSize();

        int maximumParallelism =
                processingProperties.maxParallelism();

        int totalProcessed = 0;
        int remaining = maximumBatchSize;

        while (remaining > 0) {
            int claimSize = Math.min(
                    maximumParallelism,
                    remaining
            );

            List<ClaimedAlert> claimedAlerts =
                    alertRepository.claimPendingAlerts(
                            claimSize,
                            processingProperties
                                    .processingTimeout(),
                            workerId
                    );

            if (claimedAlerts.isEmpty()) {
                break;
            }

            processAlertsInParallel(
                    claimedAlerts,
                    triggerSource,
                    workerId
            );

            totalProcessed += claimedAlerts.size();
            remaining -= claimedAlerts.size();

            /*
            * Jika jumlah yang ditemukan lebih sedikit
            * daripada claimSize, kemungkinan tidak ada
            * alert eligible lainnya pada saat query.
            */
            if (claimedAlerts.size() < claimSize) {
                break;
            }
        }

        LOGGER.info(
                "Alert dispatch completed. "
                        + "processed={}, batchSize={}, parallelism={}",
                totalProcessed,
                maximumBatchSize,
                maximumParallelism
        );

        return totalProcessed;
    }

    @Override
    public boolean dispatchAlertById(
            UUID alertId,
            TriggerSource triggerSource
    ) {
        String workerId =
                workerIdentity.getWorkerId();

        return alertRepository.claimById(
                        alertId,
                        processingProperties
                                .processingTimeout(),
                        workerId
                )
                .map(claimedAlert -> {
                    processAlert(
                            claimedAlert,
                            triggerSource,
                            workerId
                    );

                    return true;
                })
                .orElse(false);
    }

    private void processAlertsInParallel(
            List<ClaimedAlert> claimedAlerts,
            TriggerSource triggerSource,
            String workerId
    ) {
        List<Callable<Void>> tasks = claimedAlerts
                .stream()
                .map(claimedAlert ->
                        createAlertTask(
                                claimedAlert,
                                triggerSource,
                                workerId
                        )
                )
                .toList();

        try {
            List<Future<Void>> futures =
                    alertExecutor.invokeAll(tasks);

            verifyTaskResults(futures);

        } catch (InterruptedException exception) {
            /*
            * Wajib mengembalikan interrupted flag.
            */
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Parallel alert processing interrupted",
                    exception
            );
        }
    }

    private void processAlert(
            ClaimedAlert claimedAlert,
            TriggerSource triggerSource,
            String workerId
    ) {
        Instant startedAt = clock.instant();

        try {
            var alertMessage =
                    alertRepository.findMessageById(
                            claimedAlert.alertId()
                    );

            var sendResult =
                    emailService.send(alertMessage);

            Instant completedAt = clock.instant();

            alertRepository.markSuccess(
                    claimedAlert.alertId(),
                    claimedAlert.attemptNo(),
                    triggerSource,
                    sendResult.messageId(),
                    workerId,
                    startedAt,
                    completedAt
            );

            LOGGER.info(
                    "Alert sent successfully. alertId={}, attemptNo={}",
                    claimedAlert.alertId(),
                    claimedAlert.attemptNo()
            );

        } catch (Exception exception) {
            handleFailure(
                    claimedAlert,
                    triggerSource,
                    workerId,
                    startedAt,
                    exception
            );
        }
    }

    private Callable<Void> createAlertTask(
            ClaimedAlert claimedAlert,
            TriggerSource triggerSource,
            String workerId
    ) {
        return () -> {
            processAlert(
                    claimedAlert,
                    triggerSource,
                    workerId
            );

            return null;
        };
    }

    private void verifyTaskResults(
            List<Future<Void>> futures
    ) {
        for (Future<Void> future : futures) {
            try {
                future.get();

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "Waiting for alert task was interrupted",
                        exception
                );

            } catch (ExecutionException exception) {
                /*
                * Ini hanya menangkap error tak terduga.
                *
                * Error normal dari SMTP seharusnya sudah
                * ditangani processAlert() dan dicatat ke
                * delivery history.
                */
                LOGGER.error(
                        "Unexpected virtual-thread task failure",
                        exception.getCause()
                );
            }
        }
    }

    private void handleFailure(
            ClaimedAlert claimedAlert,
            TriggerSource triggerSource,
            String workerId,
            Instant startedAt,
            Exception exception
    ) {
        Instant completedAt = clock.instant();

        DeliveryFailure failure =
                failureClassifier.classify(exception);

        boolean retryAvailable =
                failure.retryable()
                && claimedAlert.attemptNo()
                    <= claimedAlert.maxRetry();

        String targetStatus;
        Instant nextRetryAt = null;

        if (retryAvailable) {
            targetStatus = "RETRY";

            nextRetryAt =
                    retryDelayCalculator.calculate(
                            claimedAlert.attemptNo(),
                            completedAt
                    );

        } else if (failure.retryable()) {
            targetStatus = "DEAD";

        } else {
            targetStatus = "FAILED";
        }

        alertRepository.markFailure(
                claimedAlert.alertId(),
                claimedAlert.attemptNo(),
                triggerSource,
                failure,
                targetStatus,
                nextRetryAt,
                workerId,
                startedAt,
                completedAt
        );

        LOGGER.error(
                "Alert delivery failed. alertId={}, status={}, errorCode={}, nextRetryAt={}",
                claimedAlert.alertId(),
                targetStatus,
                failure.errorCode(),
                nextRetryAt,
                exception
        );
    }
}
