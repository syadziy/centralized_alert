package com.mac.alert.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mac.alert.config.properties.AlertWebSocketProperties;
import com.mac.alert.entities.dto.AlertWebNotification;
import com.mac.alert.service.impl.AlertWebNotificationPublisher;
import com.mac.sdk_util.config.securities.properties.JwtAuthConverterProperties;
import com.mac.sdk_util.securities.JwtAuthConverter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AlertWebSocketTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void authenticatesStompConnectAndRequiresNotificationPermission() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        JwtAuthConverterProperties converterProperties = new JwtAuthConverterProperties();
        converterProperties.setPrincipleAttribute("username");
        JwtAuthConverter converter = new JwtAuthConverter(converterProperties);
        AlertWebSocketAuthenticationInterceptor interceptor = new AlertWebSocketAuthenticationInterceptor(
                provider(decoder), provider(converter), true);
        when(decoder.decode("valid")).thenReturn(jwt(List.of("alert:read-notifications")));
        when(decoder.decode("denied")).thenReturn(jwt(List.of("alert:write")));

        Message<?> authorized = interceptor.preSend(connect("Bearer valid"), mock(MessageChannel.class));
        assertEquals("operator", StompHeaderAccessor.wrap(authorized).getUser().getName());

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(connect("Bearer denied"), mock(MessageChannel.class)));
        assertThrows(BadCredentialsException.class,
                () -> interceptor.preSend(connect(null), mock(MessageChannel.class)));
    }

    @Test
    void permitsLocalConnectWhenSdkSecurityIsDisabled() {
        AlertWebSocketAuthenticationInterceptor interceptor = new AlertWebSocketAuthenticationInterceptor(
                provider(null), provider(null), false);
        Message<?> message = interceptor.preSend(connect(null), mock(MessageChannel.class));
        assertEquals("local-websocket", StompHeaderAccessor.wrap(message).getUser().getName());
    }

    @Test
    void acceptsAuthenticatedHttpHandshakePrincipalFromGatewayCookieRelay() {
        AlertWebSocketAuthenticationInterceptor interceptor = new AlertWebSocketAuthenticationInterceptor(
                provider(null), provider(null), true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(new UsernamePasswordAuthenticationToken("cookie-owner", "",
                List.of(new SimpleGrantedAuthority("PERM_alert:read-notifications"))));
        Message<byte[]> connect = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> authorized = interceptor.preSend(connect, mock(MessageChannel.class));

        assertEquals("cookie-owner", StompHeaderAccessor.wrap(authorized).getUser().getName());
    }

    @Test
    void publishesSanitizedNotificationAndDoesNotPropagateBrokerFailure() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        AlertWebSocketProperties properties = new AlertWebSocketProperties(
                true, List.of("http://localhost:5173"), "/topic/alerts");
        AlertWebNotificationPublisher publisher = new AlertWebNotificationPublisher(template, properties);
        AlertWebNotification notification = new AlertWebNotification(
                UUID.randomUUID(), "ALERT_CREATED", "scheduler", "Task failed", 8, "PENDING", NOW);

        publisher.publish(notification);
        verify(template).convertAndSend("/topic/alerts", notification);

        doThrow(new IllegalStateException("offline"))
                .when(template).convertAndSend("/topic/alerts", notification);
        publisher.publish(notification);
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Jwt jwt(List<String> permissions) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("operator")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(60))
                .claim("username", "operator")
                .claim("permissions", permissions)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
