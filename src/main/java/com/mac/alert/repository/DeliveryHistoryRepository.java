package com.mac.alert.repository;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import java.util.List;

public interface DeliveryHistoryRepository {
    List<DeliveryHistoryResponse> findAll(String result, int limit, int offset);

    long count(String result);
}
