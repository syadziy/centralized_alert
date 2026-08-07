package com.mac.alert.entities.model;

import com.mac.alert.entities.constant.AlertErrorCode;
import com.mac.alert.entities.constant.FailureCategory;

public record DeliveryFailure(
        AlertErrorCode errorCode,
        String errorMessage,
        String exceptionClass,
        String providerResponseCode
) {

    public FailureCategory category() {
        return errorCode.getCategory();
    }

    public boolean retryable() {
        return errorCode.isRetryable();
    }
}
