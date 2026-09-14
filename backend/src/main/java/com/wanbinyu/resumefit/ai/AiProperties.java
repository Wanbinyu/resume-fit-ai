package com.wanbinyu.resumefit.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(boolean demoMode, int maxSteps, String model) {
    public AiProperties {
        maxSteps = Math.max(1, Math.min(maxSteps, 10));
        model = model == null || model.isBlank() ? "demo-agent" : model;
    }
}
