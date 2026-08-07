package com.mac.alert.service;

import java.util.Map;

public interface EmailTemplateService {

    String render(
            String templateContent,
            Map<String, Object> variables
    );
}
