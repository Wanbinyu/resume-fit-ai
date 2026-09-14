package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.ai.AiProperties;
import com.wanbinyu.resumefit.ai.DemoAiProvider;
import com.wanbinyu.resumefit.ai.ResumeAgentTools;
import com.wanbinyu.resumefit.ai.ResumeOptimizationAgent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResumeOptimizationAgentTest {
    @Test
    void demoAgentRunsBoundedToolWorkflowWithoutModelCall() {
        ResumeOptimizationAgent agent = new ResumeOptimizationAgent(
                new DemoAiProvider(), new ObjectMapper(),
                new AiProperties(true, 5, "demo-agent"), new ResumeAgentTools());

        Map<String, Object> result = agent.analyze(
                "Java 后端工程师，使用 Spring Boot、MySQL 和 Docker 完成项目开发。",
                "岗位要求 Java、Spring Boot、MySQL、Redis、AI Agent。 ");

        assertEquals(78, result.get("score"));
        assertNotNull(result.get("agentTrace"));
        assertEquals(4, ((List<?>) result.get("agentTrace")).size());
        Map<?, ?> matching = (Map<?, ?>) result.get("matching");
        assertFalse(((List<?>) matching.get("matchedKeywords")).isEmpty());
        assertEquals("demo", agent.providerName());
    }
}
