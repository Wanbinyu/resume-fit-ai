package com.wanbinyu.resumefit.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 按 demo-mode 选择离线或真实模型实现；真实模式在创建时检查密钥是否配置。 */
@Configuration
public class AiProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "demo-mode", havingValue = "true", matchIfMissing = true)
    AiProvider demoAiProvider() {
        return new DemoAiProvider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "demo-mode", havingValue = "false")
    AiProvider springAiProvider(
        ChatModel model,
        @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.api-key}") String key
    ) {
        if (key.isBlank() || key.equals("demo-key")) throw new IllegalStateException(
            "真实模型模式需要配置 AI_API_KEY 或 DEEPSEEK_API_KEY"
        );
        return new SpringAiProvider(model);
    }
}
