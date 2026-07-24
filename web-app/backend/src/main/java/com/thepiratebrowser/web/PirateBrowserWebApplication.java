package com.thepiratebrowser.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PirateBrowserWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(PirateBrowserWebApplication.class, args);
    }
}
