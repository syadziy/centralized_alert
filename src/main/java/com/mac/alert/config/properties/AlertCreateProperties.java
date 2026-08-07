package com.mac.alert.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alert.create")
public record AlertCreateProperties(
        int defaultMaxRetry,
        int defaultPriority) {

    public AlertCreateProperties {
        if (defaultMaxRetry < 0) {
            throw new IllegalArgumentException(
                    "alert.create.default-max-retry tidak boleh negatif");
        }

        if (defaultPriority < 1 || defaultPriority > 9) {
            throw new IllegalArgumentException(
                    "alert.create.default-priority harus antara 1 dan 9");
        }
    }
}