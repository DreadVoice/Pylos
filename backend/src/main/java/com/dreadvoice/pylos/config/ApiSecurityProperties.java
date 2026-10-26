package com.dreadvoice.pylos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pylos.api")
public record ApiSecurityProperties(

        String token,

        List<String> allowedOrigins
) {

    public ApiSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
