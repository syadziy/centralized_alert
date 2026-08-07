package com.mac.alert.job;

import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.constant.AlertLogFields;
import com.mac.alert.service.AlertDispatchService;
import com.mac.alert.utils.handler.AsyncExceptionHandler;
import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "alert.pickup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AlertPickupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AlertPickupScheduler.class
            );

    private final AlertDispatchService alertDispatchService;
    private final AsyncExceptionHandler exceptionHandler;

    public AlertPickupScheduler(
            AlertDispatchService alertDispatchService,
            AsyncExceptionHandler exceptionHandler
    ) {
        this.alertDispatchService =
                alertDispatchService;
        this.exceptionHandler = exceptionHandler;
    }

    @Scheduled(
        fixedDelayString =
            "${alert.pickup.interval:PT1M}",
        initialDelayString =
            "${alert.pickup.initial-delay:PT10S}"
    )
    public void pickupPendingAlerts() {
        StructuredLog.withMdc(
                Map.of(
                        LogFields.TRACE_ID, UUID.randomUUID().toString(),
                        LogFields.EVENT_DATASET, "centralized-alert.scheduler"),
                this::executePickup);
    }

    private void executePickup() {
        try {
            int processed =
                    alertDispatchService
                            .dispatchPendingAlerts(
                                    TriggerSource.SCHEDULER
                            );

            StructuredLog.info(LOGGER, "Alert scheduler completed", Map.of(
                    LogFields.EVENT_ACTION, "pickupPendingAlerts",
                    LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                    LogFields.EVENT_DATASET, "centralized-alert.scheduler",
                    AlertLogFields.ALERT_PROCESSED_COUNT, processed,
                    AlertLogFields.TRIGGER_SOURCE, TriggerSource.SCHEDULER.name()));

        } catch (Exception exception) {
            exceptionHandler.handle(
                    null,
                    "centralized-alert.scheduler",
                    "scheduler",
                    "pickupPendingAlerts",
                    Map.of(
                            AlertLogFields.TRIGGER_SOURCE, TriggerSource.SCHEDULER.name()),
                    exception);
        }
    }
}
