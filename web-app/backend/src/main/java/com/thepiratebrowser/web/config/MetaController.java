package com.thepiratebrowser.web.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {
    private final PirateProperties properties;

    public MetaController(PirateProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> meta() {
        return Map.of(
                "canary", properties.buildCanary(),
                "registrationEnabled", properties.registrationEnabled()
        );
    }
}
