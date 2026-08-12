package com.mac.alert.config;

import com.mac.sdk_util.securities.JwtAuthConverter;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AlertWebSocketAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REQUIRED_AUTHORITY = "PERM_alert:read-notifications";

    private final ObjectProvider<JwtDecoder> jwtDecoder;
    private final ObjectProvider<JwtAuthConverter> jwtAuthConverter;
    private final boolean securityEnabled;

    public AlertWebSocketAuthenticationInterceptor(
            ObjectProvider<JwtDecoder> jwtDecoder,
            ObjectProvider<JwtAuthConverter> jwtAuthConverter,
            @Value("${sdk.security.enabled:true}") boolean securityEnabled) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthConverter = jwtAuthConverter;
        this.securityEnabled = securityEnabled;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }
        boolean rebuildMessage = !accessor.isMutable();
        if (rebuildMessage) {
            accessor = StompHeaderAccessor.wrap(message);
        }
        if (!securityEnabled) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    "local-websocket", "", List.of(new SimpleGrantedAuthority(REQUIRED_AUTHORITY))));
            return result(message, accessor, rebuildMessage);
        }

        if (accessor.getUser() instanceof Authentication handshakeAuthentication
                && handshakeAuthentication.isAuthenticated()) {
            requireNotificationPermission(handshakeAuthentication);
            return result(message, accessor, rebuildMessage);
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("WebSocket bearer token is required");
        }

        try {
            Authentication authentication = jwtAuthConverter.getObject().convert(
                    jwtDecoder.getObject().decode(authorization.substring(BEARER_PREFIX.length())));
            requireNotificationPermission(authentication);
            accessor.setUser(authentication);
            return result(message, accessor, rebuildMessage);
        } catch (JwtException exception) {
            throw new BadCredentialsException("WebSocket bearer token is invalid", exception);
        }
    }

    private static void requireNotificationPermission(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> REQUIRED_AUTHORITY.equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Alert notification permission is required");
        }
    }

    private Message<?> result(
            Message<?> original,
            StompHeaderAccessor accessor,
            boolean rebuildMessage) {
        return rebuildMessage
                ? MessageBuilder.createMessage(original.getPayload(), accessor.getMessageHeaders())
                : original;
    }
}
