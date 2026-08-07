package com.mac.alert.subscriber;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.constant.AlertLogFields;
import com.mac.alert.entities.dto.AlertEventRequested;
import com.mac.alert.entities.constant.AlertCreatedSource;
import com.mac.alert.entities.dto.CreateAlertEvent;
import com.mac.alert.entities.mapper.AlertMapper;
import com.mac.alert.service.AlertCreateService;
import com.mac.alert.service.AlertDispatchService;
import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class AlertKafkaConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AlertKafkaConsumer.class);

    private final AlertDispatchService alertDispatchService;
    private final AlertCreateService alertCreateService;
    private final AlertMapper alertMapper;
    private final Validator validator;
    private final Clock clock;

    public AlertKafkaConsumer(
            AlertDispatchService alertDispatchService,
            AlertCreateService alertCreateService,
            AlertMapper alertMapper,
            Validator validator,
            Clock clock) {
        this.alertDispatchService = alertDispatchService;
        this.alertCreateService = alertCreateService;
        this.alertMapper = alertMapper;
        this.validator = validator;
        this.clock = clock;
    }

    @KafkaListener(topics = "${alert.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(AlertEventRequested event) {
        boolean accepted = alertDispatchService.dispatchAlertById(
                event.alertId(),
                TriggerSource.KAFKA);

        if (!accepted) {
            StructuredLog.warn(LOGGER, "Kafka alert was not dispatched", Map.of(
                    LogFields.EVENT_ACTION, "dispatchAlert",
                    LogFields.EVENT_OUTCOME, LogFields.OUTCOME_FAILURE,
                    LogFields.EVENT_DATASET, "centralized-alert.kafka",
                    AlertLogFields.ALERT_ID, event.alertId(),
                    AlertLogFields.TRIGGER_SOURCE, TriggerSource.KAFKA.name()));
        }
    }

    @KafkaListener(topics = "${alert.kafka.create-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            CreateAlertEvent event,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
        validate(event);

        var command = alertMapper.toCommand(
                event.data(),
                AlertCreatedSource.KAFKA,
                clock.instant());

        var result = alertCreateService.create(command);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(LogFields.EVENT_ACTION, "createAlert");
        fields.put(LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS);
        fields.put(LogFields.EVENT_DATASET, "centralized-alert.kafka");
        fields.put(AlertLogFields.EVENT_ID, event.eventId());
        fields.put(AlertLogFields.ALERT_ID, result.alertId());
        fields.put(AlertLogFields.ALERT_CREATED, result.created());
        fields.put(AlertLogFields.ALERT_STATUS, result.status());
        fields.put(AlertLogFields.TRIGGER_SOURCE, TriggerSource.KAFKA.name());
        if (kafkaKey != null) {
            fields.put(AlertLogFields.KAFKA_MESSAGE_KEY, kafkaKey);
        }

        StructuredLog.info(LOGGER, "Create alert event processed", fields);
    }

    private void validate(
            CreateAlertEvent event) {
        Set<ConstraintViolation<CreateAlertEvent>> violations = validator.validate(event);

        if (violations.isEmpty()) {
            return;
        }

        String message = violations
                .stream()
                .map(violation -> violation.getPropertyPath()
                        + ": "
                        + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));

        throw new ConstraintViolationException(
                "Kafka alert event not valid: " + message,
                violations);
    }
}
