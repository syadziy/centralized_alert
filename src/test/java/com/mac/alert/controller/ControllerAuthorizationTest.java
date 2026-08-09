package com.mac.alert.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ControllerAuthorizationTest {

    @Test
    void recipientEndpointsDeclareTheirPermissionOnTheController() {
        assertPermission("findAll", "PERM_alert:read-recipients");
        assertPermission("create", "PERM_alert:manage-recipients");
        assertPermission("update", "PERM_alert:manage-recipients");
        assertPermission("delete", "PERM_alert:manage-recipients");
    }

    @Test
    void trustedInternalAlertEndpointsDoNotRequireMethodAuthorization() {
        assertNull(method(AlertController.class, "createAlert").getAnnotation(PreAuthorize.class));
        assertNull(method(AlertController.class, "dispatch").getAnnotation(PreAuthorize.class));
    }

    private static void assertPermission(String methodName, String permission) {
        PreAuthorize annotation = method(RecipientConfigurationController.class, methodName)
                .getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('" + permission + "')", annotation.value());
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
