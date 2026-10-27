package com.wanbinyu.resumefit.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import java.util.List;

/** 使用 Spring AI 发送一轮请求，并返回模型消息与供应商报告的 token 用量。 */
public class SpringAiProvider implements AiProvider {

    private final ChatModel chatModel;

    public SpringAiProvider(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String name() {
        return "spring-ai-openai-compatible";
    }

    @Override
    public Turn turn(List<Message> messages, List<ToolCallback> tools, int maxOutputTokens) {
        // 禁用 SDK 内部工具执行，确保所有工具都经过外层次数、超时和取消检查。
        var options = OpenAiChatOptions.builder()
            .toolCallbacks(tools)
            .internalToolExecutionEnabled(false)
            .maxTokens(maxOutputTokens)
            .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
            .build();
        var response = chatModel.call(new Prompt(messages, options));
        if (response == null || response.getResult() == null) throw new IllegalStateException("AI 返回为空");
        var usage = response.getMetadata().getUsage();
        return new Turn(
            response.getResult().getOutput(),
            value(usage.getPromptTokens()),
            value(usage.getCompletionTokens())
        );
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
