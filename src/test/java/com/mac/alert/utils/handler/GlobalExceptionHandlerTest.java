package com.mac.alert.utils.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.mac.sdk_util.entities.dto.ResponseDTO;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsUnreadableMessageToBadRequest() {
        var exception = new HttpMessageNotReadableException(
                "Malformed JSON",
                new MockHttpInputMessage(new byte[0]));

        var response = handler.handleMessageNotReadable(exception);

        assertBadRequest(response, "requestBody: invalid or unreadable JSON");
    }

    @Test
    void mapsArgumentTypeMismatchToBadRequest() {
        var exception = new MethodArgumentTypeMismatchException(
                "invalid-uuid",
                UUID.class,
                "alertId",
                null,
                new IllegalArgumentException("invalid UUID"));

        var response = handler.handleTypeMismatch(exception);

        assertBadRequest(response, "alertId: must be of type UUID");
    }

    @Test
    void mapsConstraintViolationToBadRequest() {
        Path propertyPath = proxy(
                Path.class,
                "toString",
                "request.email");
        ConstraintViolation<Object> violation = constraintViolation(
                propertyPath,
                "invalid format");
        var exception = new ConstraintViolationException(Set.of(violation));

        var response = handler.handleConstraintViolation(exception);

        assertBadRequest(response, "request.email: invalid format");
    }

    @SuppressWarnings("unchecked")
    private ConstraintViolation<Object> constraintViolation(Path path, String message) {
        return (ConstraintViolation<Object>) Proxy.newProxyInstance(
                ConstraintViolation.class.getClassLoader(),
                new Class<?>[] {ConstraintViolation.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPropertyPath" -> path;
                    case "getMessage" -> message;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, String methodName, Object returnValue) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> method.getName().equals(methodName)
                        ? returnValue
                        : null);
    }

    @Test
    void mapsIllegalArgumentToBadRequest() {
        var response = handler.handleIllegalArgument(
                new IllegalArgumentException("A TO recipient is required"));

        assertBadRequest(response, "A TO recipient is required");
    }

    private void assertBadRequest(
            ResponseEntity<ResponseDTO<Void>> response,
            String expectedError) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RC-400", response.getBody().getCode());
        assertEquals(Set.of(expectedError), Set.copyOf(response.getBody().getErrors()));
    }
}
