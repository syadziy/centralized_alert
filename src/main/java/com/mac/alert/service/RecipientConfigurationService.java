package com.mac.alert.service;

import com.mac.alert.entities.dto.RecipientConfigurationRequest;
import com.mac.alert.entities.dto.RecipientConfigurationResponse;
import com.mac.alert.entities.model.CreateAlert;
import java.util.List;
import java.util.UUID;

public interface RecipientConfigurationService {

    RecipientConfigurationResponse create(RecipientConfigurationRequest request);

    RecipientConfigurationResponse update(UUID id, RecipientConfigurationRequest request);

    void delete(UUID id);

    List<RecipientConfigurationResponse> findAll(String sourceSystem, int limit, int offset);

    List<CreateAlert.Recipient> resolve(
            String sourceSystem, List<CreateAlert.Recipient> requestRecipients);
}
