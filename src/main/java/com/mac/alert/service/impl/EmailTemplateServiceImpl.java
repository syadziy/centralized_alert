package com.mac.alert.service.impl;

import java.util.Map;

import com.mac.alert.entities.constant.AlertErrorCode;
import com.mac.alert.service.EmailTemplateService;
import com.mac.alert.utils.exception.AlertDeliveryException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailTemplateServiceImpl
        implements EmailTemplateService {

    private final TemplateEngine templateEngine;

    public EmailTemplateServiceImpl(
            @Qualifier("databaseTemplateEngine")
            TemplateEngine templateEngine
    ) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String render(
            String templateContent,
            Map<String, Object> variables
    ) {
        if (templateContent == null
                || templateContent.isBlank()) {
            throw new AlertDeliveryException(
                    AlertErrorCode.TEMPLATE_RENDER_FAILED,
                    "The email body template cannot be empty"
            );
        }

        Context context = new Context();

        if (variables != null && !variables.isEmpty()) {
            context.setVariables(variables);
        }

        try {
            return templateEngine.process(
                    templateContent,
                    context
            );

        } catch (Exception exception) {
            throw new AlertDeliveryException(
                    AlertErrorCode.TEMPLATE_RENDER_FAILED,
                    "Failed to render email template",
                    exception
            );
        }
    }
}