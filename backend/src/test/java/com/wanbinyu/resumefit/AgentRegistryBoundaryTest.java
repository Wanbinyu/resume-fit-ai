package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.ai.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryBoundaryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JobSearchProvider unavailable = (r,c,o,e) ->
            new JobSearchProvider.SearchResult("UNAVAILABLE", "请粘贴 JD", List.of());
    private AgentRun run(String resume) {
        return new AgentRun("Java", "", "", "", () -> resume, () -> "Java", () -> false, p -> {});
    }
    private AgentToolRegistry registry(AgentRun run) {
        return new AgentToolRegistry(json, run, new ResumeAgentTools(), unavailable);
    }

    @Test void genericRegistryRejectsDuplicatesUnknownFieldsAndCallbackBypass() {
        var tools = new RegisteredTools(json, () -> false, (n,s,i,m) -> {});
        tools.register("echo", "test", Set.of("value"), args -> Map.of("value", args.path("value").asText()));
        assertThrows(IllegalStateException.class, () -> tools.register("echo", "duplicate", Set.of(), args -> Map.of()));
        assertThrows(IllegalArgumentException.class, () -> tools.register("../shell", "bad", Set.of(), args -> Map.of()));
        assertThrows(IllegalStateException.class, () -> tools.definitions().getFirst().call("{}"));
        for (String args : List.of("{\"value\":\"a\",\"value\":\"b\"}",
                "{\"value\":\"a\",\"extra\":\"b\"}", "{\"value\":\"a\"} {}"))
            assertTrue(tools.execute("echo", args, "call").contains("INVALID_ARGUMENTS"));
        assertTrue(tools.execute("echo", "{\"value\":\"ok\"}", "call").contains("\"ok\":true"));
    }

    @Test void assistantModeRegistersBoundToolsOnlyAndCopiesSupplierResults() throws Exception {
        var run = run("Java 项目");
        var original = new LinkedHashMap<String,Object>(); original.put("count", 1);
        run.assistantTools = Map.of("evaluate_saved_jobs", () -> original);
        var registry = registry(run);
        assertEquals(List.of("load_resume", "evaluate_saved_jobs"), registry.definitions().stream()
                .map(tool -> tool.getToolDefinition().name()).toList());
        assertTrue(registry.execute("search_job_requirements", "{}", "a").contains("UNKNOWN_TOOL"));
        assertTrue(registry.execute("evaluate_saved_jobs", "{}", "b").contains("\"count\":1"));
        original.put("count", 999);
        assertEquals(1, json.valueToTree(run.assistantResults).path("evaluate_saved_jobs").path("count").asInt());
        assertThrows(UnsupportedOperationException.class, () -> run.assistantResults.put("extra", 2));
    }

    @Test void sealingRejectsLateUninterruptibleAssistantToolAndItsEvents() throws Exception {
        var run = run("Java");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        run.assistantTools = Map.of("delayed", () -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { /* simulate an uncooperative SDK */ }
            return Map.of("late", true);
        });
        var registry = registry(run);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> registry.execute("delayed", "{}", "late-call"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            run.seal(); int events = run.trace.size(); release.countDown();
            assertTrue(result.get(2, TimeUnit.SECONDS).contains("CANCELLED"));
            assertTrue(run.assistantResults.isEmpty());
            assertEquals(events, run.trace.size());
            assertThrows(CancellationException.class, () -> run.commit(() -> run.draft = "late"));
        } finally { release.countDown(); }
    }

    @Test void sealingRejectsLateSearchAndLoad() throws Exception {
        for (boolean loading : List.of(true, false)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            java.util.function.Supplier<String> block = () -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                return "Java";
            };
            var run = new AgentRun("Java", "", "", "", loading ? block : () -> "Java", () -> "", () -> false, p -> {});
            JobSearchProvider search = (r,c,o,e) -> {
                block.get();
                return new JobSearchProvider.SearchResult("OK", "", List.of(new JobSearchProvider.Source(
                        "one", "title", "https://example.com", "Java", "now", "TEST")));
            };
            var registry = new AgentToolRegistry(json, run, new ResumeAgentTools(), search);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var result = executor.submit(() -> registry.execute(loading ? "load_resume" : "search_job_requirements", "{}", "call"));
                assertTrue(entered.await(2, TimeUnit.SECONDS)); run.seal(); release.countDown();
                assertTrue(result.get(2, TimeUnit.SECONDS).contains("CANCELLED"));
                assertFalse(run.loadedResume); assertTrue(run.sources.isEmpty());
            } finally { release.countDown(); }
        }
    }

    @Test void oversizedEncodedResultsDoNotCommitSuccessState() {
        var run = run("\"".repeat(6000));
        assertTrue(registry(run).execute("load_resume", "{}", "a").contains("RESULT_TOO_LARGE"));
        assertFalse(run.loadedResume);
        var assistant = run("Java");
        assistant.assistantTools = Map.of("huge", () -> Map.of("text", "x".repeat(13000)));
        assertTrue(registry(assistant).execute("huge", "{}", "b").contains("RESULT_TOO_LARGE"));
        assertTrue(assistant.assistantResults.isEmpty());
    }

    @Test void sortingDraftReordersRealParagraphBreaksWithoutAddingFacts() {
        var run = run("姓名\n\n其他项目 Python\n\n相关项目 Java");
        var registry = registry(run);
        registry.execute("load_resume", "{}", "a");
        registry.execute("read_job_description", "{\"sourceId\":\"user\"}", "b");
        registry.execute("match_resume_evidence", "{}", "c");
        assertTrue(registry.execute("draft_resume_changes", "{}", "d").contains("\"ok\":true"));
        assertEquals("姓名\n\n相关项目 Java\n\n其他项目 Python", run.draft);
    }

    @Test void blockedCancellationLookupDoesNotBlockSealOrPermitLateCommit() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var run = new AgentRun("", "", "", "", () -> "", () -> "", () -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return false;
        }, p -> {});
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var commit = executor.submit(() -> assertThrows(CancellationException.class,
                        () -> run.commit(() -> run.draft = "不能提交迟到结果")));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                executor.submit(run::seal).get(1, TimeUnit.SECONDS);
                assertFalse(run.isActive());
                release.countDown(); commit.get(2, TimeUnit.SECONDS);
                assertEquals("", run.draft);
            } finally { release.countDown(); }
        }
    }

    @Test void blockedProgressPersistenceDoesNotBlockSealOrChangeSealedTrace() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var run = new AgentRun("", "", "", "", () -> "", () -> "", () -> false, p -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var event = executor.submit(() -> run.event("test", "RUNNING", "call", "进度数据库阻塞"));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                executor.submit(run::seal).get(1, TimeUnit.SECONDS);
                int events = run.trace.size();
                assertThrows(CancellationException.class, () -> run.commit(() -> run.draft = "迟到"));
                release.countDown(); event.get(2, TimeUnit.SECONDS);
                run.event("late", "SUCCEEDED", "late", "不应新增");
                assertEquals(events, run.trace.size()); assertEquals("", run.draft);
            } finally { release.countDown(); }
        }
    }
}
