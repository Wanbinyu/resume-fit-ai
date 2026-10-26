package com.wanbinyu.resumefit.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(boolean demoMode, int maxSteps, String model, int maxToolCalls,
                           int timeoutSeconds, int callTimeoutSeconds, int tokenBudget, int maxOutputTokens) {
    public AiProperties(boolean demoMode, int maxSteps, String model) {
        this(demoMode, maxSteps, model, 12, 120, 35, 64000, 1800);
    }
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AiProperties {
        maxSteps = Math.max(1, Math.min(maxSteps, 10));
        model = model == null || model.isBlank() ? "demo-agent" : model;
        maxToolCalls = clamp(maxToolCalls, 1, 24, 12);
        timeoutSeconds = clamp(timeoutSeconds, 5, 180, 120);
        callTimeoutSeconds = clamp(callTimeoutSeconds, 1, 60, 35);
        tokenBudget = clamp(tokenBudget, 1000, 200000, 64000);
        maxOutputTokens = clamp(maxOutputTokens, 128, 4096, 1800);
    }
    private static int clamp(int value, int min, int max, int fallback) {
        return value == 0 ? fallback : Math.max(min, Math.min(value, max));
    }
}
