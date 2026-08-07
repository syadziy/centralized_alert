package com.mac.alert.utils.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mac.alert.entities.constant.AlertLogFields;
import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AsyncExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncExceptionHandler.class);

    public void handle(
            String traceId,
            String dataset,
            String source,
            String action,
            Map<String, Object> additionalFields,
            Throwable exception) {
        Map<String, String> context = StructuredLog.copyMdc();
        context.put(
                LogFields.TRACE_ID,
                traceId == null || traceId.isBlank()
                        ? context.getOrDefault(LogFields.TRACE_ID, UUID.randomUUID().toString())
                        : traceId);
        context.put(LogFields.EVENT_DATASET, dataset);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(LogFields.EVENT_ACTION, action);
        fields.put(LogFields.EVENT_OUTCOME, LogFields.OUTCOME_FAILURE);
        fields.put(LogFields.EVENT_DATASET, dataset);
        fields.put(AlertLogFields.ASYNC_SOURCE, source);
        if (additionalFields != null) {
            fields.putAll(additionalFields);
        }

        StructuredLog.withMdc(
                context,
                () -> StructuredLog.error(
                        LOGGER,
                        "Asynchronous operation failed",
                        fields,
                        exception));
    }
}
