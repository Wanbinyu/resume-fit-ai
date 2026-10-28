package com.wanbinyu.resumefit.career;

import com.wanbinyu.resumefit.ai.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Read-only tools. All data is bound and frozen by the server before the model starts. */
public final class CareerAssistantTools {
    private CareerAssistantTools() {}
    public static Map<String,String> descriptions() {
        return Map.of("load_career_profile","读取本任务冻结的本人求职偏好，包括目标方向、城市、薪资和优先条件。缺失值表示未知。",
                "evaluate_resume_quality","先load_resume；检查简历表达和结构，返回规则检查分及逐字证据，不评价能力或录用概率。",
                "list_saved_jobs","列出本任务绑定的已收藏岗位概要；不联网、不保证仍在招聘。",
                "evaluate_saved_jobs","先load_resume；比较绑定岗位JD关键词与简历原文，按覆盖率整理且过期项排后。无支持词时分数为空。",
                "assess_company_evidence","整理绑定公司的事实、观点、未核实信息和来源日期，提示缺失或过期证据，不计算企业信誉分。",
                "summarize_applications","读取本人投递阶段统计和逾期跟进摘要；不会自动投递、联系企业或发送提醒。");
    }
    public static Map<String,Supplier<Object>> bind(AgentRun run, CareerAssistantContext context, ResumeAgentTools evidence) {
        Map<String,Supplier<Object>> tools = new LinkedHashMap<>();
        tools.put("load_career_profile", () -> context.profile());
        tools.put("evaluate_resume_quality", () -> { requireResume(run); return ResumeQuality.inspect(run.resume); });
        tools.put("list_saved_jobs", () -> context.jobs().stream().map(job -> Map.of(
                "id",job.id(), "title",job.title(), "companyName",job.companyName(), "location",job.location(),
                "status",job.status(), "sourceUrl",job.sourceUrl(), "collectedAt",job.collectedAt().toString())).toList());
        if (!context.intent().equals("COMPANY")) tools.put("evaluate_saved_jobs", () -> {
            requireResume(run);
            return context.jobs().stream().map(job -> match(run, job, evidence))
                    .sorted(Comparator.<Map<String,Object>,Boolean>comparing(item -> (Boolean)item.get("expired"))
                            .thenComparing(Comparator.comparingInt((Map<String,Object> item) -> item.get("coverage") instanceof Number number ? number.intValue() : -1).reversed()))
                    .toList();
        });
        tools.put("assess_company_evidence", () -> context.companies().stream().limit(3).map(company -> Map.of(
                "id",company.id(), "name",company.name(), "industry",company.industry(), "website",company.website(),
                "evidenceAssessment",company.evidenceAssessment(), "evidence",company.evidence().stream().limit(1).map(item -> Map.of(
                        "type",item.type(),"category",item.category(),"title",item.title(),"content",item.content().substring(0,Math.min(180,item.content().length())),
                        "sourceUrl",item.sourceUrl(),"observedAt",item.observedAt())).toList(),
                "notice","分析展示最多3家公司，每家1条证据摘要；完整证据请查看公司档案")).toList());
        tools.put("summarize_applications", () -> context.applications());
        return Collections.unmodifiableMap(tools);
    }
    private static Map<String,Object> match(AgentRun run, CareerDtos.JobView job, ResumeAgentTools evidence) {
        var matching = evidence.prepare(run.resume, job.description());
        boolean expired = job.expiresAt()!=null && job.expiresAt().isBefore(LocalDate.now());
        Map<String,Object> result=new LinkedHashMap<>(Map.of("jobId",job.id(), "title",job.title(), "companyName",job.companyName(),
                "coverage",matching.coverage(), "matchedKeywords",matching.matchedKeywords(), "missingKeywords",matching.missingKeywords(),
                "expired",expired, "sourceUrl",job.sourceUrl(), "notice", "保存JD的关键词文字覆盖率；不是能力或录用概率，也不保证岗位仍在招聘"));
        if(matching.keywords().isEmpty()) { result.put("coverage",null); result.put("notice","未识别到支持的技术关键词，当前规则无法计算该岗位覆盖率，请人工核实JD"); }
        return result;
    }
    private static void requireResume(AgentRun run) {
        if (!run.loadedResume || run.resume.isBlank()) throw new IllegalArgumentException("请先调用 load_resume 读取非空简历");
    }
    public static Map<String,Object> result(AgentRun run, CareerAssistantContext context) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("intent",context.intent());
        var outputs = run.assistantResults;
        Map<String,String> fields = Map.of("load_career_profile","profile", "evaluate_resume_quality","resumeQuality",
                "list_saved_jobs","savedJobs", "evaluate_saved_jobs","jobMatches", "assess_company_evidence","companyEvidence",
                "summarize_applications","applications");
        fields.forEach((tool,field) -> { if(outputs.containsKey(tool)) result.put(field,outputs.get(tool)); });
        result.put("notices",List.of("仅使用提交时保存的数据。概览最多分析最近更新的5个收藏岗位和3家公司；可在列表中选择特定记录单独分析。岗位推荐不等于已核实的在招机会。", "公司信息区分事实、意见和未核实内容；证据覆盖不代表企业信誉评分。"));
        return result;
    }
}
