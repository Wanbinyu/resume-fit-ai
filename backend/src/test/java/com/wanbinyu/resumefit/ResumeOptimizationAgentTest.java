package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.ai.*;
import com.wanbinyu.resumefit.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.ToolCallback;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ResumeOptimizationAgentTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JobSearchProvider unavailable = (r,c,o,e) -> new JobSearchProvider.SearchResult("UNAVAILABLE", "请粘贴 JD", List.of());
    private AiProperties limits(int rounds) { return new AiProperties(true, rounds, "demo"); }
    private AgentRun run(String resume, String jd) { return new AgentRun("AI 开发", "", "", "", () -> resume, () -> jd, () -> false, e -> {}); }
    private ResumeOptimizationAgent agent(AiProvider p, AiProperties limits) { return new ResumeOptimizationAgent(p, json, limits, new ResumeAgentTools(), unavailable); }
    private AgentToolRegistry registry(AgentRun run) { return new AgentToolRegistry(json, run, new ResumeAgentTools(), unavailable); }
    private AiProvider scripted(java.util.function.Function<List<Message>, AssistantMessage> f) {
        return new AiProvider() {
            public String name() { return "test-mock"; }
            public Turn turn(List<Message> messages, List<ToolCallback> tools, int max) { return new Turn(f.apply(messages), 12, 4); }
        };
    }
    private AssistantMessage call(String id, String name, String args) {
        return new AssistantMessage("", Map.of(), List.of(new AssistantMessage.ToolCall(id, "function", name, args)));
    }
    @Test void demoUsesActualEvidenceAndDoesNotInventExperience() {
        var result = agent(new DemoAiProvider(), limits(6)).analyze("张三\n使用 Java 和 MySQL 完成课程项目。", "Java MySQL Redis RAG");
        assertEquals("COMPLETED", result.get("termination"));
        assertEquals(50, result.get("score"));
        assertEquals(List.of("Java", "MySQL"), result.get("strengths"));
        assertEquals(List.of("Redis", "RAG"), result.get("gaps"));
        assertFalse(result.get("draftContent").toString().contains("RAG"));
        assertEquals(4, ((Map<?,?>) result.get("usage")).get("modelRounds"));
        assertTrue(((List<?>)result.get("agentTrace")).size() > 4);
    }
    @Test void resultIsReturnedWithOriginalCallIdAndSchema() {
        AtomicInteger rounds = new AtomicInteger();
        AiProvider provider = new AiProvider() {
            public String name() { return "mock"; }
            public Turn turn(List<Message> messages, List<ToolCallback> tools, int max) {
                assertEquals(5, tools.size());
                assertTrue(tools.stream().allMatch(t -> t.getToolDefinition().inputSchema().contains("additionalProperties")));
                if (rounds.getAndIncrement() == 0) return new Turn(call("call-123", "load_resume", "{}"), 1, 1);
                assertInstanceOf(AssistantMessage.class, messages.get(messages.size()-2));
                var response = (ToolResponseMessage) messages.getLast();
                assertEquals("call-123", response.getResponses().getFirst().id());
                assertTrue(response.getResponses().getFirst().responseData().contains("课程项目"));
                return new Turn(new AssistantMessage("{\"summary\":\"需要 JD\"}"), 1, 1);
            }
        };
        assertEquals("NEEDS_INPUT", agent(provider, limits(4)).analyze("课程项目", "").get("termination"));
        assertEquals(2, rounds.get());
    }
    @Test void unknownToolAndBadArgumentsAreStructuredErrors() {
        var registry = registry(run("Java", "Java"));
        assertTrue(registry.execute("shell", "{}", "a").contains("UNKNOWN_TOOL"));
        for (String invalid : List.of("{", "[]", "null", "{\"resumeId\":99}", "{\"x\":1}"))
            assertTrue(registry.execute("load_resume", invalid, "b").contains("INVALID_ARGUMENTS"));
        assertTrue(registry.execute("read_job_description", "{\"sourceId\":12}", "c").contains("INVALID_ARGUMENTS"));
        assertTrue(registry.execute("read_job_description", "{\"sourceId\":\"http://127.0.0.1\"}", "c").contains("PRECONDITION_FAILED"));
    }
    @Test void ownershipIsEnforcedBySupplierEvenWhenModelRequestsLoad() {
        var run = new AgentRun("", "", "", "", () -> { throw new BusinessException("other user"); }, () -> "Java", () -> false, e -> {});
        assertTrue(registry(run).execute("load_resume", "{}", "a").contains("ACCESS_DENIED"));
        assertFalse(run.loadedResume);
    }
    @Test void noSearchResultsAsksForInputAndNeverAddsFakeSources() {
        var result = agent(new DemoAiProvider(), limits(6)).analyze(run("Java", ""));
        assertEquals("NEEDS_INPUT", result.get("termination"));
        assertEquals("UNAVAILABLE", result.get("searchStatus"));
        assertEquals(List.of(), result.get("sources"));
        assertEquals("", result.get("draftContent"));
    }
    @Test void injectableSearchPassesOnlyUserConditionsAndOnlyReadsReturnedIds() {
        var run = new AgentRun("Java", "上海", "可选公司", "1年", () -> "private phone", () -> "", () -> false, e -> {});
        AtomicInteger requests = new AtomicInteger();
        JobSearchProvider mock = (role, city, company, experience) -> {
            requests.incrementAndGet(); assertEquals("Java", role); assertEquals("上海", city);
            assertEquals("可选公司", company); assertEquals("1年", experience);
            return new JobSearchProvider.SearchResult("OK", "MOCK ONLY", List.of(new JobSearchProvider.Source("mock-1", "测试来源", "https://example.com/job", "Java Redis", "2026-10-24T00:00:00Z", "MOCK")));
        };
        var tools = new AgentToolRegistry(json, run, new ResumeAgentTools(), mock);
        tools.execute("search_job_requirements", "{}", "a"); tools.execute("search_job_requirements", "{}", "b");
        assertEquals(1, requests.get());
        assertTrue(tools.execute("read_job_description", "{\"sourceId\":\"mock-1\"}", "c").contains("MOCK"));
        assertEquals("Java Redis", run.jd);
    }
    @Test void modelFailureAndInvalidJsonAreSafeTerminalResults() {
        assertEquals("MODEL_ERROR", agent(scripted(m -> { throw new IllegalStateException("secret-test-key"); }), limits(4)).analyze("Java", "Java").get("termination"));
        var result = agent(scripted(m -> new AssistantMessage("not json")), limits(4)).analyze("Java", "Java");
        assertEquals("INVALID_RESPONSE", result.get("termination"));
        assertFalse(result.toString().contains("secret-test-key"));
    }
    @Test void completeJsonFenceIsAcceptedButTrailingProseIsRejected() {
        for(String report:List.of("```json\n{\"summary\":\"需补充 JD\"}\n```","```\n{\"summary\":\"需补充 JD\"}\n```"))
            assertEquals("NEEDS_INPUT",agent(scripted(m->new AssistantMessage(report)),limits(4)).analyze("Java","Java").get("termination"));
        assertEquals("INVALID_RESPONSE",agent(scripted(m->new AssistantMessage("{\"summary\":\"摘要\"} trailing text")),limits(4)).analyze("Java","Java").get("termination"));
    }
    @Test void maxStepsLimitsActualRequestsAndToolBudgetStopsExecution() {
        AtomicInteger calls = new AtomicInteger();
        var p = scripted(m -> call("id-"+calls.incrementAndGet(), "unknown", "{}"));
        var result = agent(p, limits(2)).analyze("Java", "Java");
        assertEquals(2, calls.get()); assertEquals("BUDGET_EXHAUSTED", result.get("termination"));
        var lowTools = new AiProperties(true, 6, "demo", 1, 120, 35, 64000, 1800);
        var bounded = agent(new DemoAiProvider(), lowTools).analyze("Java", "Java");
        assertEquals("BUDGET_EXHAUSTED", bounded.get("termination"));
        assertEquals(0, ((Map<?,?>)bounded.get("usage")).get("toolCalls"));
    }
    @Test void tokenBudgetStopsBeforePaidRequest() {
        var p = scripted(m -> { fail("must not call model"); return null; });
        var lowTokens = new AiProperties(true, 4, "demo", 12, 120, 35, 1000, 128);
        assertEquals("BUDGET_EXHAUSTED", agent(p, lowTokens).analyze("Java", "Java").get("termination"));
    }
    @Test void cancellationStopsBetweenRoundsAndDuringModelWait() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        var run = new AgentRun("", "", "", "", () -> "Java", () -> "Java", cancelled::get, e -> {});
        var p = scripted(m -> { fail("cancelled before call"); return null; });
        assertEquals("CANCELLED", agent(p, limits(4)).analyze(run).get("termination"));
        cancelled.set(false);
        var slow = scripted(m -> { cancelled.set(true); try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return new AssistantMessage("{}"); });
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> assertEquals("CANCELLED", agent(slow, limits(4)).analyze(run).get("termination")));
    }
    @Test void timeoutAndDuplicateIdsTerminate() {
        var slow = scripted(m -> { try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return new AssistantMessage("{}"); });
        var shortCall = new AiProperties(true, 4, "demo", 12, 5, 1, 64000, 1800);
        assertEquals("TIMEOUT", agent(slow, shortCall).analyze("Java", "Java").get("termination"));
        assertEquals("INVALID_RESPONSE", agent(scripted(m -> call("same-id", "load_resume", "{}")), limits(4)).analyze("Java", "Java").get("termination"));
    }
    @Test void arbitraryUrlAndKeywordSubstringAreNotAccepted() {
        for (String url : List.of("http://example.com", "https://127.0.0.1", "https://[::1]", "https://a.local", "file:///etc/passwd", "https://x:y@example.com")) assertFalse(TavilyJobSearchProvider.publicUrl(url));
        assertTrue(TavilyJobSearchProvider.publicUrl("https://example.com/jobs"));
        var result = agent(new DemoAiProvider(), limits(6)).analyze("JavaScript", "Java Git");
        assertEquals(List.of(), result.get("strengths"));
    }
}
