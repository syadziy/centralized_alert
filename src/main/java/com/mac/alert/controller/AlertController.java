package com.mac.alert.controller;

import java.util.UUID;
import java.net.URI;
import java.time.Clock;

import com.mac.alert.entities.constant.TriggerSource;
import com.mac.alert.entities.dto.ManualDispatchResponse;
import com.mac.alert.entities.constant.AlertCreatedSource;
import com.mac.alert.entities.dto.CreateAlertRequest;
import com.mac.alert.entities.dto.CreateAlertResponse;
import com.mac.alert.entities.mapper.AlertMapper;
import com.mac.alert.service.AlertDispatchService;
import com.mac.alert.service.AlertCreateService;
import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.utils.ResponseHelper;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class AlertController {

    private final AlertDispatchService alertDispatchService;
    private final AlertCreateService alertCreateService;
    private final AlertMapper alertMapper;
    private final Clock clock;

    public AlertController(
            AlertDispatchService alertDispatchService,
            AlertCreateService alertCreateService,
            AlertMapper alertMapper,
            Clock clock) {
        this.alertDispatchService = alertDispatchService;
        this.alertCreateService = alertCreateService;
        this.alertMapper = alertMapper;
        this.clock = clock;
    }

    @PostMapping("/alert/{alertId}/dispatch")
    public ResponseEntity<ResponseDTO<ManualDispatchResponse>> dispatch(
            @PathVariable UUID alertId) {
        boolean accepted = alertDispatchService.dispatchAlertById(
                alertId,
                TriggerSource.API);

        if (!accepted) {
            return ResponseHelper.httpConflict(
                    new ManualDispatchResponse(
                            alertId,
                            false,
                            "Alert cannot be processed because its status is neither PENDING nor RETRY"));
        }

        return ResponseHelper.httpAccepted(
                new ManualDispatchResponse(
                        alertId,
                        true,
                        "Alert processed successfully"));
    }

    @PostMapping("/alert")
    public ResponseEntity<ResponseDTO<CreateAlertResponse>> createAlert(
            @Valid @RequestBody CreateAlertRequest request) {
        var command = alertMapper.toCommand(
                request,
                AlertCreatedSource.API,
                clock.instant());

        var result = alertCreateService.create(command);

        var response = new CreateAlertResponse(
                result.alertId(),
                result.status(),
                result.created(),
                result.createdAt(),
                result.created()
                        ? "Alert created successfully"
                        : "An alert with the same idempotency key already exists");

        if (!result.created()) {
            return ResponseHelper.httpOK(response);
        }

        URI location = URI.create(
                "/api/v1/alerts/" + result.alertId());

        return ResponseHelper.httpCreated(response, location);
    }
}
