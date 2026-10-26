package com.dreadvoice.pylos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class ProdConfigCheck {

    private static final Logger log = LoggerFactory.getLogger(ProdConfigCheck.class);
    private static final String JDBC_POSTGRES_PREFIX = "jdbc:postgresql:";

    public ProdConfigCheck(@Value("${spring.datasource.url:}") String datasourceUrl,
                           @Value("${spring.datasource.username:}") String datasourceUsername,
                           @Value("${spring.datasource.password:}") String datasourcePassword,
                           ApiSecurityProperties apiSecurityProperties) {
        List<String> problems = new ArrayList<>();

        if (!StringUtils.hasText(datasourceUrl)) {
            problems.add("POSTGRES_URL is not set; expected a JDBC url such as "
                    + "jdbc:postgresql://127.0.0.1:5432/pylos");
        } else if (!datasourceUrl.startsWith(JDBC_POSTGRES_PREFIX)) {
            problems.add("POSTGRES_URL must be a JDBC url starting with " + JDBC_POSTGRES_PREFIX
                    + " but starts with " + scheme(datasourceUrl)
                    + "; a postgres:// connection string will not work");
        }
        if (!StringUtils.hasText(datasourceUsername)) {
            problems.add("POSTGRES_USERNAME is not set");
        }
        if (!StringUtils.hasText(datasourcePassword)) {
            problems.add("POSTGRES_PASSWORD is not set");
        }
        if (!StringUtils.hasText(apiSecurityProperties.token())) {
            problems.add("PYLOS_API_TOKEN is not set; the api would accept no requests at all "
                    + "and money-moving endpoints must never be deployed unauthenticated");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the prod profile: "
                    + String.join("; ", problems));
        }

        if (apiSecurityProperties.allowedOrigins().isEmpty()) {
            log.warn("PYLOS_ALLOWED_ORIGINS is empty; browser clients will be blocked by CORS");
        }
        log.info("Prod configuration check passed");
    }

    private static String scheme(String url) {
        int separator = url.indexOf(':');
        return separator < 0 ? url : url.substring(0, separator + 1);
    }
}
