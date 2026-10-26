package com.wanbinyu.resumefit.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import java.util.List;

public interface AiProvider {
    String name();

    // 每次只向模型请求一轮，工具由外层循环执行，避免 SDK 内部递归绕过预算。
    Turn turn(List<Message> messages, List<ToolCallback> tools, int maxOutputTokens);

    record Turn(AssistantMessage message, int inputTokens, int outputTokens) {}
}
