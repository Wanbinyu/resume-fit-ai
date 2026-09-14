package com.wanbinyu.resumefit.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic tools used before the model call. They make the Agent workflow
 * inspectable and keep the Demo mode completely offline.
 */
@Component
public class ResumeAgentTools {
    private static final List<String> TECHNICAL_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "Spring Security", "MyBatis-Plus",
            "MySQL", "Redis", "Docker", "Linux", "RAG", "AI Agent", "Spring AI",
            "LangChain", "向量数据库", "微服务", "消息队列", "SSE", "JWT", "Python", "Git");

    public AgentContext prepare(String resume, String jobDescription) {
        List<String> keywords = extractJobKeywords(jobDescription);
        List<String> matched = keywords.stream()
                .filter(keyword -> containsIgnoreCase(resume, keyword))
                .toList();
        List<String> missing = keywords.stream()
                .filter(keyword -> !matched.contains(keyword))
                .toList();
        int coverage = keywords.isEmpty() ? 0 : Math.round(matched.size() * 100.0f / keywords.size());
        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(step("extract_job_keywords", "从 JD 提取技术关键词", keywords.size()));
        trace.add(step("match_resume_evidence", "在简历中检索已有证据", matched.size()));
        trace.add(step("calculate_coverage", "计算关键词覆盖率", coverage));
        trace.add(step("guard_fabrication", "开启事实约束，禁止补写未出现的经历", true));
        return new AgentContext(keywords, matched, missing, coverage, trace);
    }

    private List<String> extractJobKeywords(String jobDescription) {
        Set<String> result = new LinkedHashSet<>();
        for (String keyword : TECHNICAL_KEYWORDS) {
            if (containsIgnoreCase(jobDescription, keyword)) result.add(keyword);
        }
        return List.copyOf(result);
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> step(String name, String message, Object output) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("message", message);
        result.put("output", output);
        result.put("status", "SUCCEEDED");
        return result;
    }

    public record AgentContext(List<String> keywords, List<String> matchedKeywords,
                               List<String> missingKeywords, int coverage,
                               List<Map<String, Object>> trace) {
    }
}
