package com.mac.alert.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("alert.websocket")
public record AlertWebSocketProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
        @DefaultValue("/topic/alerts") String destination) {}
