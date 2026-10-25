package com.dreadvoice.pylos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PylosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PylosApplication.class, args);
    }
}
