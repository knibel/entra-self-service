package com.knibel.entraselfservice;

import com.knibel.entraselfservice.config.EntraProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EntraProperties.class)
public class EntraSelfServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntraSelfServiceApplication.class, args);
    }
}
