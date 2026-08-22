package com.campusfix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} picks up the {@code @ConfigurationProperties}
 * records — JWT settings and the seed admin — without each one having to be
 * registered by hand.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusFixApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFixApplication.class, args);
    }
}
