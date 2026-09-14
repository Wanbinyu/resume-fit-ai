package com.wanbinyu.resumefit.ai;

import org.springframework.ai.chat.client.ChatClient;

public class SpringAiProvider implements AiProvider {
    private final ChatClient chatClient;

    public SpringAiProvider(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return "spring-ai-openai-compatible";
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        String result = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        if (result == null || result.isBlank()) throw new IllegalStateException("AI 返回为空");
        return result;
    }
}
