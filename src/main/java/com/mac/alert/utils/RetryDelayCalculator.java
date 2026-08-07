package com.mac.alert.utils;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.mac.alert.config.properties.AlertProcessingProperties;

@Component
public class RetryDelayCalculator {

    private final AlertProcessingProperties properties;

    public RetryDelayCalculator(
            AlertProcessingProperties properties
    ) {
        this.properties = properties;
    }

    public Instant calculate(
            int attemptNo,
            Instant currentTime
    ) {
        int exponent = Math.max(0, attemptNo - 1);
        int boundedExponent = Math.min(exponent, 20);

        long multiplier = 1L << boundedExponent;

        long initialMillis =
                properties.retryInitialDelay().toMillis();

        long maximumMillis =
                properties.retryMaxDelay().toMillis();

        long calculatedMillis;

        try {
            calculatedMillis = Math.multiplyExact(
                    initialMillis,
                    multiplier
            );
        } catch (ArithmeticException exception) {
            calculatedMillis = maximumMillis;
        }

        return currentTime.plusMillis(
                Math.min(
                        calculatedMillis,
                        maximumMillis
                )
        );
    }
}
