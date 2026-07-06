package ru.daniil.shifts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShiftsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShiftsApplication.class, args);
    }
}
