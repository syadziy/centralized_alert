package com.mac.alert.subscriber;

import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;

import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.dto.AlertEventRequested;
import com.mac.alert.entities.constant.AlertCreatedSource;
import com.mac.alert.entities.dto.CreateAlertEvent;
import com.mac.alert.entities.mapper.AlertMapper;
import com.mac.alert.service.AlertCreateService;
import com.mac.alert.service.AlertDispatchService;

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
            LOGGER.warn(
                    "Kafka alert was not dispatched. alertId={}",
                    event.alertId());
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

        LOGGER.info(
                """
                        Create alert event processed. \
                        eventId={}, kafkaKey={}, alertId={}, \
                        created={}, status={}
                        """,
                event.eventId(),
                kafkaKey,
                result.alertId(),
                result.created(),
                result.status());
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
