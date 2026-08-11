package com.mac.alert.service.impl;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import com.mac.alert.repository.DeliveryHistoryRepository;
import com.mac.alert.service.DeliveryHistoryService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DeliveryHistoryServiceImpl implements DeliveryHistoryService {
    private final DeliveryHistoryRepository repository;

    public DeliveryHistoryServiceImpl(DeliveryHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DeliveryHistoryResponse> findAll(String result, int limit, int offset) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        String normalized = result == null || result.isBlank() ? null : result.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !normalized.equals("SUCCESS") && !normalized.equals("FAILED")) {
            throw new IllegalArgumentException("result must be SUCCESS or FAILED");
        }
        return repository.findAll(normalized, limit, offset);
    }
}
