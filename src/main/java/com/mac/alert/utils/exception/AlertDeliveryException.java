package com.mac.alert.utils.exception;

import com.mac.alert.entities.constant.AlertErrorCode;

public class AlertDeliveryException extends RuntimeException {

    private final AlertErrorCode errorCode;

    public AlertDeliveryException(
            AlertErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }

    public AlertDeliveryException(
            AlertErrorCode errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AlertErrorCode getErrorCode() {
        return errorCode;
    }
}