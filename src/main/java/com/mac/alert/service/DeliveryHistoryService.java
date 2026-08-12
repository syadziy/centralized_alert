package com.mac.alert.service;

import com.mac.alert.entities.dto.DeliveryHistoryResponse;
import java.util.List;

public interface DeliveryHistoryService {
    List<DeliveryHistoryResponse> findAll(String result, int limit, int offset);

    long count(String result);
}
