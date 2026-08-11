package com.mac.alert.controller;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import com.mac.alert.service.DeliveryHistoryService;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.helper.ResponseHelper;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alert/delivery-history")
public class DeliveryHistoryController {
    private final DeliveryHistoryService service;

    public DeliveryHistoryController(DeliveryHistoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Role.ALERT_READ_NOTIFICATIONS)
    public ResponseEntity<ResponseDTO<List<DeliveryHistoryResponse>>> findAll(
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseHelper.httpOK(service.findAll(result, limit, offset));
    }
}
