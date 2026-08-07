package com.mac.alert.entities.constant;

public final class AlertLogFields {

    public static final String ALERT_ID = "alert.id";
    public static final String ALERT_ATTEMPT = "alert.attempt";
    public static final String ALERT_BATCH_SIZE = "alert.batch_size";
    public static final String ALERT_CREATED = "alert.created";
    public static final String ALERT_ERROR_CODE = "alert.error.code";
    public static final String ALERT_NEXT_RETRY_AT = "alert.next_retry_at";
    public static final String ALERT_PARALLELISM = "alert.parallelism";
    public static final String ALERT_PROCESSED_COUNT = "alert.processed_count";
    public static final String ALERT_STATUS = "alert.status";
    public static final String ASYNC_SOURCE = "async.source";
    public static final String EVENT_ID = "event.id";
    public static final String KAFKA_MESSAGE_KEY = "kafka.message.key";
    public static final String KAFKA_DEAD_LETTER_TOPIC = "kafka.dead_letter_topic";
    public static final String KAFKA_OFFSET = "kafka.offset";
    public static final String KAFKA_PARTITION = "kafka.partition";
    public static final String KAFKA_TOPIC = "kafka.topic";
    public static final String TRIGGER_SOURCE = "alert.trigger_source";

    private AlertLogFields() {}
}
