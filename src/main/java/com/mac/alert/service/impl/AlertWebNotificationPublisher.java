package com.mac.alert.service.impl;

import com.mac.alert.config.properties.AlertWebSocketProperties;
import com.mac.alert.entities.dto.AlertWebNotification;
import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "alert.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertWebNotificationPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AlertWebNotificationPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertWebSocketProperties properties;

    public AlertWebNotificationPublisher(
            SimpMessagingTemplate messagingTemplate,
            AlertWebSocketProperties properties) {
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(AlertWebNotification notification) {
        try {
            messagingTemplate.convertAndSend(properties.destination(), notification);
            StructuredLog.info(LOG, "Realtime alert notification published", Map.of(
                    LogFields.EVENT_ACTION, "publishAlertWebNotification",
                    LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                    LogFields.EVENT_DATASET, "centralized-alert.websocket",
                    "alert.id", notification.alertId()));
        } catch (RuntimeException exception) {
            StructuredLog.error(LOG, "Realtime alert notification failed", Map.of(
                    LogFields.EVENT_ACTION, "publishAlertWebNotification",
                    LogFields.EVENT_OUTCOME, LogFields.OUTCOME_FAILURE,
                    LogFields.EVENT_DATASET, "centralized-alert.websocket",
                    "alert.id", notification.alertId()), exception);
        }
    }
}
