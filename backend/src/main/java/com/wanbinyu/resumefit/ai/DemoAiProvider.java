package com.wanbinyu.resumefit.ai;

import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.ToolCallback;
import java.util.*;

/** 明确标注的离线模型替身：走同一工具循环，不调用模型或伪造岗位/经历。 */
public class DemoAiProvider implements AiProvider {
    @Override public String name() { return "demo"; }
    @Override public Turn turn(List<Message> messages, List<ToolCallback> tools, int maxOutputTokens) {
        Set<String> done = new HashSet<>();
        String lastRead = "";
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage responses) for (var response : responses.getResponses()) {
                done.add(response.name());
                if (response.name().equals("read_job_description")) lastRead = response.responseData();
            }
        }
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        if (tools.stream().anyMatch(tool -> tool.getToolDefinition().name().equals("review_resume_structure"))) {
            if (!done.contains("load_resume")) calls.add(call("load_resume", "{}"));
            else if (!done.contains("review_resume_structure")) calls.add(call("review_resume_structure", "{}"));
            else return new Turn(new AssistantMessage("{\"summary\":\"离线简历体检已完成。请结合下方原文线索检查结构、职责和成果表达；未识别不代表没有相关经历。补充真实目标 JD 后可进一步进行岗位匹配。\",\"interviewQuestions\":[\"项目中你实际负责哪部分工作，交付了什么可验证的结果？\"]}"), 0, 0);
            return new Turn(new AssistantMessage("", Map.of(), calls), 0, 0);
        }
        if (!done.contains("load_resume")) {
            calls.add(call("load_resume", "{}"));
            calls.add(call("read_job_description", "{\"sourceId\":\"user\"}"));
        } else if (lastRead.contains("\"content\":\"\"") && !done.contains("search_job_requirements")) {
            calls.add(call("search_job_requirements", "{}"));
        } else if (lastRead.contains("\"content\":\"\"")) {
            return new Turn(new AssistantMessage("{\"summary\":\"离线演示：请补充目标岗位的完整 JD，搜索摘要需要手动核实。\"}"), 0, 0);
        } else if (!done.contains("match_resume_evidence")) calls.add(call("match_resume_evidence", "{}"));
        else if (!done.contains("draft_resume_changes")) calls.add(call("draft_resume_changes", "{}"));
        else return new Turn(new AssistantMessage("{\"summary\":\"离线演示已完成关键词证据匹配和原文排序草稿。覆盖率不代表录用概率；缺失项需向用户核实。\",\"interviewQuestions\":[\"请解释简历中最相关项目的职责、实现和结果。\"]}"), 0, 0);
        return new Turn(new AssistantMessage("", Map.of(), calls), 0, 0);
    }
    private AssistantMessage.ToolCall call(String name, String arguments) {
        return new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function", name, arguments);
    }
}
