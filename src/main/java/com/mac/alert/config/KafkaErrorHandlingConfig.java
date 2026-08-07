package com.mac.alert.config;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mac.alert.entities.constant.AlertLogFields;
import com.mac.alert.entities.dto.AlertEventRequested;
import com.mac.alert.entities.dto.CreateAlertEvent;
import com.mac.alert.utils.handler.AsyncExceptionHandler;

import jakarta.validation.ConstraintViolationException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String DATASET = "centralized-alert.kafka";

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            AsyncExceptionHandler exceptionHandler,
            KafkaOperations<Object, Object> kafkaOperations,
            @Value("${alert.kafka.dead-letter-topic}") String deadLetterTopic,
            @Value("${alert.kafka.error-handling.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${alert.kafka.error-handling.max-retries:2}") long maxRetries) {
        DeadLetterPublishingRecoverer deadLetterRecoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaOperations,
                        (record, exception) -> new TopicPartition(
                                deadLetterTopic,
                                record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    handleExhaustedRecord(
                            record,
                            exception,
                            deadLetterTopic,
                            exceptionHandler);
                    deadLetterRecoverer.accept(record, exception);
                },
                new FixedBackOff(retryIntervalMs, maxRetries));
        errorHandler.addNotRetryableExceptions(
                ConstraintViolationException.class,
                IllegalArgumentException.class);
        return errorHandler;
    }

    private void handleExhaustedRecord(
            ConsumerRecord<?, ?> record,
            Exception exception,
            String deadLetterTopic,
            AsyncExceptionHandler exceptionHandler) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(AlertLogFields.KAFKA_TOPIC, record.topic());
        fields.put(AlertLogFields.KAFKA_PARTITION, record.partition());
        fields.put(AlertLogFields.KAFKA_OFFSET, record.offset());
        fields.put(AlertLogFields.KAFKA_DEAD_LETTER_TOPIC, deadLetterTopic);
        if (record.key() != null) {
            fields.put(AlertLogFields.KAFKA_MESSAGE_KEY, record.key());
        }

        exceptionHandler.handle(
                resolveTraceId(record),
                DATASET,
                "kafka-listener",
                "consumeKafkaRecord",
                fields,
                exception);
    }

    private String resolveTraceId(ConsumerRecord<?, ?> record) {
        Header correlationHeader = record.headers().lastHeader(CORRELATION_HEADER);
        if (correlationHeader != null && correlationHeader.value() != null) {
            String value = new String(correlationHeader.value(), StandardCharsets.UTF_8).trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        if (record.key() != null && !record.key().toString().isBlank()) {
            return record.key().toString();
        }
        if (record.value() instanceof CreateAlertEvent event
                && event.eventId() != null
                && !event.eventId().isBlank()) {
            return event.eventId();
        }
        if (record.value() instanceof AlertEventRequested event && event.alertId() != null) {
            return event.alertId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
