package com.cloud.gateway.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = GatewayApplication.BASE_PACKAGE)
public class GatewayApplication {
    public static final String BASE_PACKAGE = "com.cloud.gateway";

    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
}
