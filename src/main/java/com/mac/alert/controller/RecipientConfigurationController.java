package com.mac.alert.controller;

import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.dto.RecipientConfigurationResponse;
import com.mac.alert.service.RecipientConfigurationService;
import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.utils.ResponseHelper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/alert/recipients")
public class RecipientConfigurationController {

    private final RecipientConfigurationService service;

    public RecipientConfigurationController(RecipientConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_alert:read-recipients')")
    public ResponseEntity<ResponseDTO<List<RecipientConfigurationResponse>>> findAll(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseHelper.httpOK(service.findAll(sourceSystem, limit, offset));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_alert:manage-recipients')")
    public ResponseEntity<ResponseDTO<RecipientConfigurationResponse>> create(
            @Valid @RequestBody RecipientConfigurationRequest request) {
        RecipientConfigurationResponse response = service.create(request);
        return ResponseHelper.httpCreated(
                response, URI.create("/api/v1/alert/recipients/" + response.id()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_alert:manage-recipients')")
    public ResponseEntity<ResponseDTO<RecipientConfigurationResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody RecipientConfigurationRequest request) {
        return ResponseHelper.httpOK(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_alert:manage-recipients')")
    public ResponseEntity<ResponseDTO<Map<String, Object>>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseHelper.httpOK(Map.of("id", id, "deleted", true));
    }
}
