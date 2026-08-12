package com.mac.alert.controller;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import com.mac.alert.service.DeliveryHistoryService;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.entities.dto.PagingDTO;
import com.mac.sdk_util.helper.ResponsePagingHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
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
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResponsePagingHelper.httpOK(service.findAll(result, limit, offset),
                new PagingDTO(limit, offset, service.count(result)));
    }
}
