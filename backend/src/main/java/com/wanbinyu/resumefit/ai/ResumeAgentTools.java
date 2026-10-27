package com.wanbinyu.resumefit.ai;

import org.springframework.stereotype.Component;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关键词匹配的确定性实现，由模型通过注册表按需调用。
 */
@Component
public class ResumeAgentTools {

    private static final List<String> TECHNICAL_KEYWORDS = List.of(
        "Java",
        "Spring Boot",
        "Spring Cloud",
        "Spring Security",
        "MyBatis-Plus",
        "MySQL",
        "Redis",
        "Docker",
        "Linux",
        "RAG",
        "AI Agent",
        "Spring AI",
        "LangChain",
        "向量数据库",
        "微服务",
        "消息队列",
        "SSE",
        "JWT",
        "Python",
        "Git"
    );

    public AgentContext prepare(String resume, String jobDescription) {
        List<String> keywords = extractJobKeywords(jobDescription);
        List<String> matched = keywords
            .stream()
            .filter(keyword -> containsIgnoreCase(resume, keyword))
            .toList();
        List<String> missing = keywords
            .stream()
            .filter(keyword -> !matched.contains(keyword))
            .toList();
        int coverage = keywords.isEmpty() ? 0 : Math.round(matched.size() * 100.0f / keywords.size());
        return new AgentContext(keywords, matched, missing, coverage, List.of());
    }

    private List<String> extractJobKeywords(String jobDescription) {
        Set<String> result = new LinkedHashSet<>();
        for (String keyword : TECHNICAL_KEYWORDS) {
            if (containsIgnoreCase(jobDescription, keyword)) result.add(keyword);
        }
        return List.copyOf(result);
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9])" + java.util.regex.Pattern.quote(keyword) + "(?![A-Za-z0-9])",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
            .matcher(text)
            .find();
    }

    public record AgentContext(
        List<String> keywords,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        int coverage,
        List<Map<String, Object>> trace
    ) {}
}
