package com.cloud.gateway.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Auto-configurations must stay outside the component-scan path. Scanned, they are registered
 * before the bean definitions their conditions inspect exist, so {@code @ConditionalOnBean} silently
 * evaluates false and the conditional beans never appear.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = GatewayApplication.BASE_PACKAGE,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = AutoConfiguration.class))
public class GatewayApplication {
    public static final String BASE_PACKAGE = "com.cloud.gateway";

    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
}
