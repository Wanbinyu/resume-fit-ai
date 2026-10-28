package com.wanbinyu.resumefit.career;

import java.util.*;
import java.util.regex.Pattern;

/** An explainable writing checklist. A score never measures ability or hiring probability. */
public final class ResumeQuality {
    private ResumeQuality() {}
    public static Map<String,Object> inspect(String content) {
        String text = content == null ? "" : content;
        List<Map<String,Object>> dimensions = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        check(dimensions, missing, text, "教育信息", "教育|学历|本科|硕士|博士|大专|大学|学院", 15, "核实教育信息是否完整写出");
        check(dimensions, missing, text, "相关经历", "项目|实习|工作经历|工作经验|任职|作品", 20, "写清真实的工作、实习或项目经历");
        check(dimensions, missing, text, "职责和交付", "负责|实现|开发|设计|完成|交付|优化|维护|搭建", 20, "说明自己负责的部分和具体交付");
        check(dimensions, missing, text, "结果描述", "结果|成果|上线|交付|提升|降低|减少|完成|解决|验证", 15, "补充可验证的结果；没有数据也不要编造指标");
        check(dimensions, missing, text, "技能描述", "技能|技术栈|熟悉|掌握|Java|Python|SQL|Kotlin|运营|设计", 15, "写清与求职方向相关的技能和实践");
        boolean readable = text.length() >= 200 && text.length() <= 6000 && text.lines().filter(s -> !s.isBlank()).count() >= 5;
        dimensions.add(Map.of("name", "基础可读性", "score", readable ? 15 : 0, "maximum", 15,
                "found", readable, "quote", "", "explanation", "至少200字、5个非空行的基础检查，不评价排版设计"));
        if (!readable) missing.add("核实导入是否完整，使用适量分段方便阅读");
        int score = dimensions.stream().mapToInt(d -> (int)d.get("score")).sum();
        return Map.of("score", score, "scoreMeaning", "简历表达检查清单得分（规则版），独立于岗位关键词覆盖率；不代表能力、真实性或录用概率",
                "dimensions", dimensions, "missingInformation", missing, "characterCount", text.length(),
                "notice", "未识别到文字线索不代表没有相关经历；通过该检查也不证明经历真实。请结合实际情况复核。");
    }
    private static void check(List<Map<String,Object>> dimensions, List<String> missing, String text,
                              String name, String regex, int maximum, String suggestion) {
        var pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        String quote = text.lines().filter(line -> pattern.matcher(line).find()).findFirst().orElse("");
        boolean found = !quote.isBlank();
        dimensions.add(Map.of("name", name, "score", found ? maximum : 0, "maximum", maximum,
                "found", found, "quote", quote.substring(0, Math.min(180, quote.length())), "explanation", suggestion));
        if (!found) missing.add(suggestion);
    }
}
