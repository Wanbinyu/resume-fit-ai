package com.wanbinyu.resumefit.ai;

public class DemoAiProvider implements AiProvider {
    @Override
    public String name() {
        return "demo";
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return """
                {
                  "score": 78,
                  "summary": "简历与目标岗位存在较好的技术匹配，建议进一步突出可量化成果和 AI 工程实践。",
                  "strengths": ["Java 后端基础较完整", "具备 RAG 与 Agent 项目经验", "有模型接入、测试和部署实践"],
                  "gaps": ["缺少与目标 JD 一一对应的项目证据", "部分经历缺少结果指标"],
                  "keywords": ["Java", "Spring Boot", "AI Agent", "RAG", "MySQL"],
                  "rewrites": [
                    {"original": "参与项目开发", "suggestion": "负责 Spring Boot 后端接口与 AI 分析任务链路开发，补充任务状态、错误恢复和结果校验。"}
                  ],
                  "interviewQuestions": ["如何设计 AI 分析任务的重试和幂等？", "Agent 工具调用如何限制权限和执行步数？"]
                }
                """;
    }
}
