package com.mac.alert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@Configuration
public class TemplateConfig {

    @Bean(name = "databaseTemplateEngine")
    public TemplateEngine databaseTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();

        resolver.setTemplateMode(TemplateMode.HTML);

        /*
         * Body dapat berubah di database.
         * Karena itu resolver tidak menyimpan cache template.
         */
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();

        templateEngine.setTemplateResolver(resolver);
        templateEngine.setEnableSpringELCompiler(true);

        return templateEngine;
    }
}