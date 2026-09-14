package com.wanbinyu.resumefit.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "demo-mode", havingValue = "true", matchIfMissing = true)
    AiProvider demoAiProvider() {
        return new DemoAiProvider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "demo-mode", havingValue = "false")
    AiProvider springAiProvider(ChatClient.Builder builder) {
        return new SpringAiProvider(builder.build());
    }
}
