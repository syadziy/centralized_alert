package com.mac.alert.utils;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkerIdentity {

    private final String workerId;

    public WorkerIdentity(
            @Value("${spring.application.name}")
            String applicationName
    ) {
        String hostname = System.getenv()
                .getOrDefault("HOSTNAME", "local");

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        this.workerId =
                applicationName
                + "-"
                + hostname
                + "-"
                + suffix;
    }

    public String getWorkerId() {
        return workerId;
    }
}
