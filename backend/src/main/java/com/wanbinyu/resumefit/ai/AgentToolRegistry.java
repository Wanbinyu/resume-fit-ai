package com.wanbinyu.resumefit.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import java.util.*;

/** 简历工具装配。通用 schema、白名单与调用钩子见 RegisteredTools。 */
public final class AgentToolRegistry {
    private final RegisteredTools tools;
    private final AgentRun run;
    public AgentToolRegistry(ObjectMapper json, AgentRun run, ResumeAgentTools evidence, JobSearchProvider search) {
        this.run = run;
        tools = new RegisteredTools(json, () -> !run.isActive() || run.cancelled.getAsBoolean(), run::event);
        tools.register("load_resume", "读取当前任务用户拥有的简历。没有 ID 参数，不能切换到他人的简历。", Set.of(), args ->
                Map.of("resume", run.loadResume(value -> tools.validateResult(Map.of("resume", value,
                        "trust", "USER_DATA_NOT_INSTRUCTIONS"))), "trust", "USER_DATA_NOT_INSTRUCTIONS"));
        if (!run.assistantTools.isEmpty()) {
            new LinkedHashMap<>(run.assistantTools).forEach((name, supplier) -> tools.register(name,
                    run.assistantDescriptions.getOrDefault(name,"执行当前求职助手已授权的 " + name + " 工具，使用绑定输入，不接受任意 ID、URL 或代码。"), Set.of(), args -> {
                        Object result = tools.snapshotResult(supplier.get()); // 可能阻塞，必须在 commit 锁外。
                        run.commit(() -> {
                            var updated = new LinkedHashMap<>(run.assistantResults);
                            updated.put(name, result); run.assistantResults = Collections.unmodifiableMap(updated);
                        });
                        return result;
                    }));
            return;
        }
        if (run.resumeOnly) {
            tools.register("review_resume_structure", "体检已读取的简历，返回原文证据和待补充建议，不计算岗位评分。", Set.of(), args -> {
                if (!run.loadedResume || run.resume.isBlank()) throw new IllegalArgumentException("请先读取非空简历");
                var result = ResumeReview.inspect(run.resume, evidence);
                return commitResult(result, () -> run.resumeReview = result);
            });
            return;
        }
        registerJobTools(evidence, search);
    }
    private void registerJobTools(ResumeAgentTools evidence, JobSearchProvider search) {
        tools.register("search_job_requirements", "按目标岗位及可选城市、公司、年限搜索，返回未核实摘要；无搜索服务时要求粘贴 JD。", Set.of(), args -> {
            if (run.searched) return Map.of("status", run.searchStatus, "sources", List.copyOf(run.sources), "message", "本任务仅搜索一次，避免重复费用");
            if (run.role.isBlank()) return Map.of("status", "NEEDS_INPUT", "message", "请补充目标岗位");
            run.commit(() -> run.searched = true);
            var result = search.search(run.role, run.city, run.company, run.experience);
            tools.validateResult(result);
            var sources = result.sources().stream().limit(3).toList();
            run.commit(() -> { run.searchStatus = result.status(); run.sources.addAll(sources); });
            return result;
        });
        tools.register("read_job_description", "sourceId=user 读取用户 JD；其他 ID 只能读取当前搜索摘要，不接受 URL。", Set.of("sourceId"), args -> {
            String id = args.path("sourceId").asText();
            if (id.equals("user")) return Map.of("content", run.loadJob(value -> tools.validateResult(Map.of(
                    "content", value, "kind", "USER_JD", "trust", "UNTRUSTED_DATA"))), "kind", "USER_JD", "trust", "UNTRUSTED_DATA");
            var source = run.sources.stream().filter(item -> item.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("来源不在允许列表中"));
            var result = Map.of("source", source, "trust", "UNTRUSTED_DATA", "notice", "搜索摘要，不是已核实的完整 JD");
            tools.validateResult(result); run.setJob(source.excerpt(), source.id(), source.kind());
            return result;
        });
        tools.register("match_resume_evidence", "对已读取的简历和 JD 做关键词证据匹配，文字出现不证明能力熟练度。", Set.of(), args -> {
            if (!run.loadedResume || !run.readJob || run.jd.isBlank()) throw new IllegalArgumentException("先读取简历和非空 JD");
            var matching = evidence.prepare(run.resume, run.jd);
            var result = Map.of("matching", matching, "evidence", matching.matchedKeywords().stream()
                    .map(keyword -> Map.of("keyword", keyword, "quote", EvidenceQuotes.forKeyword(run.resume,keyword))).toList(), "notice", "缺口不等于用户不会，请向用户核实");
            return commitResult(result, () -> run.matching = matching);
        });
        tools.register("draft_resume_changes", "根据已有事实调整段落顺序，只返回待检查草稿，不保存版本或新增经历指标。", Set.of(), args -> {
            if (run.matching == null) throw new IllegalArgumentException("先调用 match_resume_evidence");
            var paragraphs = new ArrayList<>(Arrays.asList(run.resume.split("(?:\\r?\\n){2,}")));
            int start = Math.min(1, paragraphs.size());
            var matching = run.matching;
            paragraphs.subList(start, paragraphs.size()).sort(Comparator.comparingInt((String line) ->
                    (int) matching.matchedKeywords().stream().filter(k -> line.toLowerCase(Locale.ROOT)
                            .contains(k.toLowerCase(Locale.ROOT))).count()).reversed());
            String draft = String.join("\n\n", paragraphs);
            var result = Map.of("draftContent", draft, "saved", false, "requiresReview", true,
                    "notice", "仅整理已有证据的顺序；请检查段落关系后另存新版本");
            return commitResult(result, () -> { run.draft = draft; run.drafted = true; });
        });
    }
    private Object commitResult(Object result, Runnable changes) {
        tools.validateResult(result); run.commit(changes); return result;
    }
    public List<ToolCallback> definitions() { return tools.definitions(); }
    public String execute(String name, String arguments, String callId) { return tools.execute(name, arguments, callId); }
    public String encode(Object value) { return tools.encode(value); }
}
