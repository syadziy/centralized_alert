package com.mac.alert.job;

import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.service.AlertDispatchService;

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

    public AlertPickupScheduler(
            AlertDispatchService alertDispatchService
    ) {
        this.alertDispatchService =
                alertDispatchService;
    }

    @Scheduled(
        fixedDelayString =
            "${alert.pickup.interval:PT1M}",
        initialDelayString =
            "${alert.pickup.initial-delay:PT10S}"
    )
    public void pickupPendingAlerts() {
        try {
            int processed =
                    alertDispatchService
                            .dispatchPendingAlerts(
                                    TriggerSource.SCHEDULER
                            );

            LOGGER.info(
                    "Alert scheduler completed. processed={}",
                    processed
            );

        } catch (Exception exception) {
            LOGGER.error(
                    "Alert scheduler execution failed",
                    exception
            );
        }
    }
}