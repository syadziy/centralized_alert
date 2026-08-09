package com.mac.alert.repository;

import com.mac.alert.entities.constant.RecipientType;
import com.mac.alert.entities.model.RecipientConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipientConfigurationRepository {

    RecipientConfiguration insert(RecipientConfiguration configuration);

    boolean update(RecipientConfiguration configuration);

    boolean delete(UUID id);

    Optional<RecipientConfiguration> findById(UUID id);

    boolean exists(String sourceSystem, RecipientType type, String email, UUID excludedId);

    List<RecipientConfiguration> findAll(String sourceSystem, int limit, int offset);

    List<RecipientConfiguration> findResolvedForSource(String sourceSystem);
}
