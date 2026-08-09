package com.mac.alert.config;

import com.mac.alert.config.properties.AlertWebSocketProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "alert.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AlertWebSocketAuthenticationInterceptor authenticationInterceptor;
    private final AlertWebSocketProperties properties;

    public AlertWebSocketConfig(
            AlertWebSocketAuthenticationInterceptor authenticationInterceptor,
            AlertWebSocketProperties properties) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/alerts")
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}
