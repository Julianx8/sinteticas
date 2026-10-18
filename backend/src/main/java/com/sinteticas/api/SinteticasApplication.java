package com.sinteticas.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SinteticasApplication {

    public static void main(String[] args) {
        SpringApplication.run(SinteticasApplication.class, args);
    }

}
