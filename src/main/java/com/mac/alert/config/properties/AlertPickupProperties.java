package com.mac.alert.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alert.pickup")
public record AlertPickupProperties(
        boolean enabled,
        Duration interval,
        Duration initialDelay,
        int batchSize) {

    public AlertPickupProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "alert.pickup.batch-size harus lebih dari 0");
        }
    }
}
