package com.safari.tms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SafariTmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafariTmsApplication.class, args);
    }
}
