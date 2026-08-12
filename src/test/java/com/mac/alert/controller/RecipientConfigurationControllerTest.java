package com.mac.alert.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.dto.RecipientConfigurationResponse;
import com.mac.alert.controller.DeliveryHistoryController;
import com.mac.alert.service.DeliveryHistoryService;
import com.mac.alert.service.RecipientConfigurationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RecipientConfigurationControllerTest {

    @Test
    void deliveryHistoryIncludesServerTotal() {
        DeliveryHistoryService service = mock(DeliveryHistoryService.class);
        when(service.findAll("SUCCESS", 10, 20)).thenReturn(List.of());
        when(service.count("SUCCESS")).thenReturn(35L);

        var body = new DeliveryHistoryController(service)
                .findAll("SUCCESS", 10, 20).getBody();

        assertEquals(20, body.getPaging().getOffset());
        assertEquals(35, body.getPaging().getTotalRecord());
    }

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
        when(service.count("PAYMENT")).thenReturn(42L);
        when(service.create(request)).thenReturn(response);
        when(service.update(id, request)).thenReturn(response);

        var list = controller.findAll("PAYMENT", 10, 2);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertEquals(42, list.getBody().getPaging().getTotalRecord());
        assertEquals(HttpStatus.CREATED, controller.create(request).getStatusCode());
        assertEquals("/api/v1/alert/recipients/" + id,
                controller.create(request).getHeaders().getLocation().toString());
        assertEquals(HttpStatus.OK, controller.update(id, request).getStatusCode());
        assertEquals(HttpStatus.OK, controller.delete(id).getStatusCode());
        verify(service).delete(id);
    }
}
