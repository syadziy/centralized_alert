package com.mac.alert.service;

import java.util.UUID;

import com.mac.alert.entities.constant.TriggerSource;

public interface AlertDispatchService {

    int dispatchPendingAlerts(TriggerSource triggerSource);

    boolean dispatchAlertById(
            UUID alertId,
            TriggerSource triggerSource
    );
}
