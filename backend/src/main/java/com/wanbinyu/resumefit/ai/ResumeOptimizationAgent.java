package com.wanbinyu.resumefit.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ResumeOptimizationAgent {
    private static final String SYSTEM_PROMPT = """
            你是一个严谨的简历与岗位匹配 Agent。只能基于用户提供的简历事实进行分析，不得虚构公司、项目、技术、数据或职责。
            你的输出必须是合法 JSON，不要输出 Markdown 代码块。字段必须包含：score(0-100)、summary、strengths、gaps、keywords、rewrites、interviewQuestions。
            rewrites 是对象数组，每项包含 original 和 suggestion。所有建议都要能被简历事实支持。
            """;

    private final AiProvider provider;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final ResumeAgentTools tools;

    public ResumeOptimizationAgent(AiProvider provider, ObjectMapper objectMapper,
                                  AiProperties properties, ResumeAgentTools tools) {
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tools = tools;
    }

    public Map<String, Object> analyze(String resume, String jobDescription) {
        if (resume == null || resume.isBlank() || jobDescription == null || jobDescription.isBlank()) {
            throw new BusinessException("简历和岗位 JD 不能为空");
        }
        String safeResume = limit(resume, 6000);
        String safeJob = limit(jobDescription, 3000);
        ResumeAgentTools.AgentContext context = tools.prepare(safeResume, safeJob);
        String userPrompt = """
                请分析以下简历和岗位 JD，并给出可执行的优化结果。

                【简历】
                %s

                【岗位 JD】
                %s

                【Agent 工具执行结果】
                JD 关键词：%s
                简历已覆盖：%s
                简历缺口：%s
                关键词覆盖率：%d%%
                Agent 执行约束：最多执行 %d 步；如果没有证据支持某项建议，应明确标记为缺口，不要补写事实。
                """.formatted(safeResume, safeJob, context.keywords(), context.matchedKeywords(),
                context.missingKeywords(), context.coverage(), properties.maxSteps());

        String raw = provider.generate(SYSTEM_PROMPT, userPrompt);
        Map<String, Object> result = parseJson(raw);
        result.put("agentTrace", context.trace().subList(0, Math.min(properties.maxSteps(), context.trace().size())));
        result.put("matching", Map.of(
                "jobKeywords", context.keywords(),
                "matchedKeywords", context.matchedKeywords(),
                "missingKeywords", context.missingKeywords(),
                "coverage", context.coverage()));
        return result;
    }

    public String providerName() {
        return provider.name();
    }

    private Map<String, Object> parseJson(String raw) {
        String json = raw.trim();
        if (json.startsWith("```") && json.endsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> sanitized = new LinkedHashMap<>();
            sanitized.put("score", normalizeScore(result.get("score")));
            sanitized.put("summary", stringValue(result.get("summary"), "暂无总结"));
            sanitized.put("strengths", result.getOrDefault("strengths", java.util.List.of()));
            sanitized.put("gaps", result.getOrDefault("gaps", java.util.List.of()));
            sanitized.put("keywords", result.getOrDefault("keywords", java.util.List.of()));
            sanitized.put("rewrites", result.getOrDefault("rewrites", java.util.List.of()));
            sanitized.put("interviewQuestions", result.getOrDefault("interviewQuestions", java.util.List.of()));
            return sanitized;
        } catch (Exception exception) {
            throw new BusinessException("AI 返回格式无法识别，请稍后重试");
        }
    }

    private int normalizeScore(Object value) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value))));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
