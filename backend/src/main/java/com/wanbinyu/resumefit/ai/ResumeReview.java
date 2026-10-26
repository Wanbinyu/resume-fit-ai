package com.wanbinyu.resumefit.ai;

import java.util.*;
import java.util.regex.Pattern;

/** 简历体检只描述原文中观察到的线索，不把“未识别”当成用户没有经历。 */
public final class ResumeReview {
    private ResumeReview() {}
    public static Map<String, Object> inspect(String resume, ResumeAgentTools tools) {
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("教育背景", "教育|学历|毕业|本科|硕士|博士|大专|大学|学院");
        patterns.put("工作/实习经历", "工作经历|实习|任职|工作经验|职业经历");
        patterns.put("项目经历", "项目|作品|课程设计");
        patterns.put("技能", "技能|技术栈|Java|Python|SQL|开发|设计|运营");
        List<Map<String, Object>> sections = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        patterns.forEach((name, regex) -> {
            var pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            String quote = resume.lines().filter(line -> pattern.matcher(line).find()).findFirst().orElse("");
            sections.add(Map.of("name", name, "found", !quote.isBlank(), "quote", quote.substring(0, Math.min(quote.length(), 180))));
            if (quote.isBlank()) observations.add("未识别到“"+name+"”的明确文字线索，请检查是否已写清楚；不代表你没有相关经历。");
        });
        if (!Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:%|％|倍|毫秒|ms|秒|万元|万次|人次)", Pattern.CASE_INSENSITIVE).matcher(resume).find())
            observations.add("未识别到常见的结果指标格式。若有可验证的成果，可补充真实指标及统计口径；没有数据时写清交付结果，不编造数字。");
        if (resume.contains("参与") || resume.contains("协助")) observations.add("部分表述包含“参与/协助”，建议核实并写清自己实际承担的职责、实现和交付。");
        if (resume.length() < 200) observations.add("当前简历文字较少，请核实是否完整导入，以及项目、职责和结果是否充分描述。");
        return Map.of("characterCount", resume.length(), "sections", sections,
                "technicalKeywords", tools.prepare(resume, resume).keywords(), "observations", observations,
                "notice", "这是简历本身体检。尚未提供目标 JD，不计算岗位匹配分数，不推断未出现的经历或能力。");
    }
}
