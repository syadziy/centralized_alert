package com.mac.alert.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alert.create")
public record AlertCreateProperties(
        int defaultMaxRetry,
        int defaultPriority) {

    public AlertCreateProperties {
        if (defaultMaxRetry < 0) {
            throw new IllegalArgumentException(
                    "alert.create.default-max-retry must not be negative");
        }

        if (defaultPriority < 1 || defaultPriority > 9) {
            throw new IllegalArgumentException(
                    "alert.create.default-priority must be between 1 and 9");
        }
    }
}
