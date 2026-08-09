package com.mac.alert.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.dto.RecipientConfigurationResponse;
import com.mac.alert.service.RecipientConfigurationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RecipientConfigurationControllerTest {

    @Test
    void delegatesAllDashboardOperations() {
        RecipientConfigurationService service = mock(RecipientConfigurationService.class);
        RecipientConfigurationController controller = new RecipientConfigurationController(service);
        UUID id = UUID.randomUUID();
        var request = new RecipientConfigurationRequest(
                "PAYMENT", RecipientType.TO, "ops@example.com", "Ops", true);
        var response = new RecipientConfigurationResponse(id, "PAYMENT", RecipientType.TO,
                "ops@example.com", "Ops", true, Instant.EPOCH, Instant.EPOCH);
        when(service.findAll("PAYMENT", 10, 2)).thenReturn(List.of(response));
        when(service.create(request)).thenReturn(response);
        when(service.update(id, request)).thenReturn(response);

        assertEquals(HttpStatus.OK,
                controller.findAll("PAYMENT", 10, 2).getStatusCode());
        assertEquals(HttpStatus.CREATED, controller.create(request).getStatusCode());
        assertEquals("/api/v1/alert/recipients/" + id,
                controller.create(request).getHeaders().getLocation().toString());
        assertEquals(HttpStatus.OK, controller.update(id, request).getStatusCode());
        assertEquals(HttpStatus.OK, controller.delete(id).getStatusCode());
        verify(service).delete(id);
    }
}
