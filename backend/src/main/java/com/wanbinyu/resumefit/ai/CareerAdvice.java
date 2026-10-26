package com.wanbinyu.resumefit.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;

/** 职业方向是待核实的推断；引用必须逐字来自当前简历，不能让模型伪造“原文证据”。 */
public final class CareerAdvice {
    private CareerAdvice() {}

    public static Map<String, Object> read(JsonNode advice, AgentRun run) {
        List<Map<String, Object>> roles = new ArrayList<>(), steps = new ArrayList<>();
        int omitted = 0;
        for (JsonNode item : items(advice.path("recommendedRoles"), 3)) {
            String title = text(item.path("title"), 80), reason = text(item.path("reason"), 400);
            List<String> quotes = quotes(item.path("evidence"), run.resume);
            if (title.isBlank() || reason.isBlank() || quotes.isEmpty()) { omitted++; continue; }
            roles.add(Map.of("title", title, "reason", reason, "evidence", quotes,
                    "uncertainties", strings(item.path("uncertainties"), 3, 250)));
        }
        boolean hasJd = !run.resumeOnly && run.readJob && !run.jd.isBlank();
        String target = !run.role.isBlank() ? run.role : hasJd ? "所提供 JD 对应的岗位" : "";
        String basis = hasJd ? run.analysisBasis : target.isBlank() ? "NONE" : "ROLE_ONLY";
        if (!target.isBlank()) for (JsonNode item : items(advice.path("targetPlan").path("priorities"), 6)) {
            String category = text(item.path("category"), 20), title = text(item.path("title"), 100);
            String action = text(item.path("action"), 600);
            List<String> quotes = quotes(item.path("evidence"), run.resume);
            if (!Set.of("HIGHLIGHT", "VERIFY", "LEARN").contains(category) || title.isBlank() || action.isBlank()
                    || (category.equals("HIGHLIGHT") && quotes.isEmpty())
                    || (item.path("evidence").isArray() && !item.path("evidence").isEmpty() && quotes.isEmpty())) { omitted++; continue; }
            // 写作建议、待核实经历、后续能力建设是不同状态，不能直接变成新增简历事实。
            String instruction = switch (category) {
                case "HIGHLIGHT" -> "基于已有原文突出：";
                case "VERIFY" -> "仅在确实做过并能核实时补充：";
                default -> "后续学习/实践建议，不写为已有经历：";
            };
            steps.add(Map.of("category", category, "title", title, "action", instruction+action, "evidence", quotes));
        }
        String notice = switch (basis) {
            case "NONE" -> "请填写期望岗位，再生成具体调整方向。当前先展示简历已有证据支持的岗位方向。";
            case "ROLE_ONLY" -> "尚未使用具体 JD，以下仅按期望岗位名称给出初步方向；补充真实 JD 后可细化要求和差距。";
            case "USER_JD" -> "调整方向结合用户提供的 JD。缺失文字需要核实，不等于用户不会，也不能直接补写经历。";
            default -> "依据搜索摘要提出初步调整方向；摘要不是已核实的完整 JD，需核实岗位要求与招聘时效。";
        };
        return Map.of("recommendedRoles", roles,
                "recommendationNotice", "基于当前简历内容的初步岗位方向，不代表在招职位、能力认证或录用概率。证据不足时不强行推荐。",
                "targetPlan", Map.of("targetRole", target, "basis", basis, "priorities", steps, "notice", notice,
                        "questions", target.isBlank() ? List.of("你的期望岗位是什么？如有具体 JD，可以一起提供。")
                                : strings(advice.path("targetPlan").path("questions"), 4, 250)),
                "omittedUnsupportedItems", omitted);
    }

    private static List<String> quotes(JsonNode node, String resume) {
        if (!node.isArray() || node.isEmpty() || node.size()>3) return List.of();
        List<String> result = new ArrayList<>();
        for (var quote : node) {
            if (!quote.isTextual()) return List.of();
            String value = quote.asText().trim();
            if (value.length()<4 || value.length()>300 || !resume.contains(value)) return List.of();
            if (!result.contains(value)) result.add(value);
        }
        return List.copyOf(result);
    }
    private static String text(JsonNode node, int max) {
        return node.isTextual() && node.asText().trim().length()<=max ? node.asText().trim() : "";
    }
    private static List<String> strings(JsonNode node, int count, int max) {
        List<String> result = new ArrayList<>();
        for (var item : items(node, count)) { String value=text(item,max); if(!value.isBlank())result.add(value); }
        return List.copyOf(result);
    }
    private static List<JsonNode> items(JsonNode node, int max) {
        if (!node.isArray()) return List.of();
        List<JsonNode> result=new ArrayList<>();
        for (var item:node) { if(result.size()>=max)break; result.add(item); }
        return result;
    }

    /** 离线演示仅按少量规则识别方向，不冒充模型判断或实时岗位搜索。 */
    public static JsonNode demo(AgentRun run, ObjectMapper json) {
        List<Map<String,Object>> roles=new ArrayList<>(), steps=new ArrayList<>();
        addDemoRole(roles,run.resume,"Java 后端开发工程师","Java","Spring|后端|接口|MySQL", "Java 与后端相关实现的文字线索较明确，可优先核实后端岗位要求。");
        addDemoRole(roles,run.resume,"AI 应用开发工程师","RAG|LangChain|Spring AI|DeepSeek|大模型","开发|项目|接入|调用|实现|部署", "原文出现模型应用或相关项目线索，可进一步核实模型接入和工程实践。");
        addDemoRole(roles,run.resume,"前端开发工程师","Vue|React|前端","JavaScript|TypeScript|CSS|页面", "原文有前端技术与页面实现线索，可考虑前端开发方向。");
        addDemoRole(roles,run.resume,"Python 应用开发工程师","Python","Django|Flask|FastAPI|后端|接口", "Python 与应用开发线索可作为岗位方向的初步依据。");
        addDemoRole(roles,run.resume,"数据分析相关岗位","数据分析|数据清洗|可视化|Pandas","SQL|Excel|Python", "原文有数据处理与工具使用线索，可核实分析方法及业务成果。");
        if (roles.size()>3) roles=new ArrayList<>(roles.subList(0,3));
        boolean hasTarget=!run.role.isBlank() || (!run.resumeOnly && !run.jd.isBlank());
        if(hasTarget){
            if(!roles.isEmpty()) steps.add(Map.of("category","HIGHLIGHT","title","突出与目标相关的已有项目", "action","把真实承担的职责、实现方式和可验证交付放在显眼位置；保留事实边界。", "evidence",roles.getFirst().get("evidence")));
            steps.add(Map.of("category","VERIFY","title","补全做过但尚未写清的细节", "action","核实是否有与期望岗位相关但没有写清的工作；写明自己的贡献，不能把团队成果全算作个人成果。", "evidence",List.of()));
            String learning=run.role.matches("(?i).*?(AI|人工智能|大模型).*?")
                    ? "如缺少 AI 应用实践，可完成模型 API 接入、RAG 检索或工具调用的练习，并记录测试与评测结果；做完后以真实项目性质说明。"
                    : "结合具体 JD 选择最重要的能力缺口做可运行的练习，并记录验证过程；补充 JD 后再细化。";
            steps.add(Map.of("category","LEARN","title","补足经核实仍欠缺的实践", "action",learning,"evidence",List.of()));
        }
        return json.valueToTree(Map.of("recommendedRoles",roles,"targetPlan",Map.of("priorities",steps,"questions",List.of("请核实项目实际职责、完成范围和可验证成果。"))));
    }
    private static void addDemoRole(List<Map<String,Object>> roles,String resume,String title,String required,String supporting,String reason){
        var first=Pattern.compile(required,Pattern.CASE_INSENSITIVE);
        var second=Pattern.compile(supporting,Pattern.CASE_INSENSITIVE);
        if(!first.matcher(resume).find() || !second.matcher(resume).find())return;
        List<String> quotes=resume.lines().filter(line->first.matcher(line).find()||second.matcher(line).find())
                .filter(line->line.trim().length()>=4).limit(2).map(line->line.trim().substring(0,Math.min(line.trim().length(),180))).toList();
        if(quotes.isEmpty())return;
        roles.add(Map.of("title",title,"reason",reason,"evidence",quotes,"uncertainties",List.of("离线规则初筛，需核实独立完成范围、熟练程度及具体岗位要求。")));
    }
}
