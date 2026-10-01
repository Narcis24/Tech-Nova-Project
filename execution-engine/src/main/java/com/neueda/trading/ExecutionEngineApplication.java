package com.neueda.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExecutionEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionEngineApplication.class, args);
    }
}
