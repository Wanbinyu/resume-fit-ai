package com.wanbinyu.resumefit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class ResumeFitBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResumeFitBackendApplication.class, args);
    }
}
