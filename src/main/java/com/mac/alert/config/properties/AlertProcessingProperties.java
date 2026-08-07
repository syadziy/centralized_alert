package com.mac.alert.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "alert.processing")
public record AlertProcessingProperties(
        Duration processingTimeout,
        Duration retryInitialDelay,
        Duration retryMaxDelay,
        int maxParallelism) {

    public AlertProcessingProperties {
        if (processingTimeout == null
                || processingTimeout.isNegative()
                || processingTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "alert.processing.processing-timeout tidak boleh negatif");
        }

        if (retryInitialDelay == null
                || retryInitialDelay.isNegative()
                || retryInitialDelay.isZero()) {
            throw new IllegalArgumentException(
                    "alert.processing.retry-initial-delay tidak boleh negatif");
        }

        if (retryMaxDelay == null
                || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException(
                    "retry-max-delay harus lebih besar atau sama dengan retry-initial-delay");
        }

        if (maxParallelism <= 0) {
            throw new IllegalArgumentException(
                    "max-parallelism harus lebih dari 0");
        }
    }
}
