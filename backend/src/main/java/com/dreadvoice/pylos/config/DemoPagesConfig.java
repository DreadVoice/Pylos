package com.dreadvoice.pylos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoPagesConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/demo").setViewName("forward:/demo/index.html");
        registry.addViewController("/demo/").setViewName("forward:/demo/index.html");
        registry.addViewController("/demo/chat").setViewName("forward:/demo/chat.html");
        registry.addViewController("/demo/order").setViewName("forward:/demo/order.html");
        registry.addViewController("/demo/unpaid").setViewName("forward:/demo/unpaid.html");
    }
}
