package com.wanbinyu.resumefit.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String bootstrapUsername, String bootstrapPassword) {
    public boolean enabled() {
        return bootstrapUsername != null && !bootstrapUsername.isBlank()
                && bootstrapPassword != null && !bootstrapPassword.isBlank();
    }
}
