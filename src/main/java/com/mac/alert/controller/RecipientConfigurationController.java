package com.mac.alert.controller;

import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.dto.RecipientConfigurationResponse;
import com.mac.alert.service.RecipientConfigurationService;
import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.entities.dto.PagingDTO;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.sdk_util.helper.ResponsePagingHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/alert/recipients")
public class RecipientConfigurationController {

    private final RecipientConfigurationService service;

    public RecipientConfigurationController(RecipientConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Role.ALERT_READ_RECIPIENTS)
    public ResponseEntity<ResponseDTO<List<RecipientConfigurationResponse>>> findAll(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResponsePagingHelper.httpOK(service.findAll(sourceSystem, limit, offset),
                new PagingDTO(limit, offset, service.count(sourceSystem)));
    }

    @PostMapping
    @PreAuthorize(Role.ALERT_MANAGE_RECIPIENTS)
    public ResponseEntity<ResponseDTO<RecipientConfigurationResponse>> create(
            @Valid @RequestBody RecipientConfigurationRequest request) {
        RecipientConfigurationResponse response = service.create(request);
        return ResponseHelper.httpCreated(
                response, URI.create("/api/v1/alert/recipients/" + response.id()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Role.ALERT_MANAGE_RECIPIENTS)
    public ResponseEntity<ResponseDTO<RecipientConfigurationResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody RecipientConfigurationRequest request) {
        return ResponseHelper.httpOK(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Role.ALERT_MANAGE_RECIPIENTS)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseHelper.httpOK(Map.of("id", id, "deleted", true));
    }
}
