package com.foodbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point. Run this class (or `mvn spring-boot:run`) to start FoodBridge. */
@SpringBootApplication
@EnableScheduling
public class FoodBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodBridgeApplication.class, args);
    }
}
