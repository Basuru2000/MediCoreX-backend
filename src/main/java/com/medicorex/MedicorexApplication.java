package com.medicorex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync  // Add this
public class MedicorexApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicorexApplication.class, args);
    }
}