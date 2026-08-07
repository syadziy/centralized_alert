package com.mac.alert.service;

import com.mac.alert.entities.model.AlertCreateResult;
import com.mac.alert.entities.model.CreateAlert;

public interface AlertCreateService {

    AlertCreateResult create(
            CreateAlert model
    );
}