package com.yzzhang.weeklyreport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ProjectPathConfig pathConfig;

    public WebConfig(ProjectPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dist = pathConfig.frontendDist().toUri().toString();
        registry.addResourceHandler("/assets/**").addResourceLocations(dist + "assets/");
    }
}
