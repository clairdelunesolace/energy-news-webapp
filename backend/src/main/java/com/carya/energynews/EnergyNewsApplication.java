package com.carya.energynews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EnergyNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnergyNewsApplication.class, args);
    }
}
