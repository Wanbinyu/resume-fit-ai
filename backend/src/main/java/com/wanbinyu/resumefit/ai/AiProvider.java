package com.wanbinyu.resumefit.ai;

public interface AiProvider {
    String name();

    String generate(String systemPrompt, String userPrompt);
}
