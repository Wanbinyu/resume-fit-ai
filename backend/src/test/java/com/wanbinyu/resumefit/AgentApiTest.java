package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.ai.demo-mode=true", "app.search.api-key=",
        "spring.datasource.url=jdbc:h2:mem:agent_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class AgentApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private JsonNode request(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        var payload = json.readTree(response.getContentAsByteArray());
        assertEquals(200, response.getStatus(), payload.toString());
        assertTrue(payload.path("success").asBoolean(), payload.toString());
        return payload.path("data");
    }
    private String user() throws Exception {
        return request(post("/api/auth/register"), null, Map.of("username", "test-" + UUID.randomUUID().toString().substring(0,12),
                "password", "test-local-password-123", "displayName", "测试用户")).path("token").asText();
    }
    private JsonNode await(String token, long id) throws Exception {
        for (int i = 0; i < 100; i++) {
            var task = request(get("/api/analysis/tasks/" + id), token, null);
            if (List.of("SUCCEEDED", "FAILED", "NEEDS_INPUT", "CANCELLED").contains(task.path("status").asText())) return task;
            Thread.sleep(50);
        }
        fail("async task did not terminate"); return null;
    }
    @Test void completeTaskPersistsReportAndOnlyExplicitSaveCreatesUnpublishedVersion() throws Exception {
        String token = user();
        long resumeId = request(post("/api/resumes"), token, Map.of("title", "测试简历", "content", "测试用户\nJava MySQL 课程项目", "sourceFileName", "test.txt")).path("id").asLong();
        long id = request(post("/api/analysis/tasks"), token, Map.of("resumeId", resumeId, "targetRole", "Java 工程师",
                "city", "杭州", "jobDescription", "要求 Java MySQL Redis")).path("id").asLong();
        var task = await(token, id);
        assertEquals("SUCCEEDED", task.path("status").asText(), task.toString());
        assertEquals("COMPLETED", task.path("result").path("termination").asText());
        assertEquals(1, request(get("/api/resumes/" + resumeId + "/versions"), token, null).size());
        assertEquals(1, request(get("/api/resumes/" + resumeId + "/reports"), token, null).size());
        var version = request(post("/api/resumes/"+resumeId+"/versions"), token, Map.of("title", "核实后草稿", "content", task.path("result").path("draftContent").asText(),
                "source", "JOB_TARGETED", "analysisTaskId", id));
        assertFalse(version.path("published").asBoolean());
        assertTrue(version.path("sharePath").isNull());
        assertEquals(2, request(get("/api/resumes/" + resumeId + "/versions"), token, null).size());
        String other = user();
        var denied = mvc.perform(get("/api/analysis/tasks/" + id).header("Authorization", "Bearer " + other)).andReturn().getResponse();
        assertFalse(json.readTree(denied.getContentAsByteArray()).path("success").asBoolean());
        var deniedCreate = mvc.perform(post("/api/analysis/tasks").header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("resumeId", resumeId, "targetRole", "Java")))).andReturn().getResponse();
        assertFalse(json.readTree(deniedCreate.getContentAsByteArray()).path("success").asBoolean());
    }
    @Test void targetOnlyTaskNeedsJdWithoutCreatingReportOrDraft() throws Exception {
        String token = user();
        long resumeId = request(post("/api/resumes"), token, Map.of("title", "无JD测试", "content", "Java", "sourceFileName", "test.txt")).path("id").asLong();
        long id = request(post("/api/analysis/tasks"), token, Map.of("resumeId", resumeId, "targetRole", "AI 应用开发工程师")).path("id").asLong();
        var task = await(token, id);
        assertEquals("NEEDS_INPUT", task.path("status").asText(), task.toString());
        assertEquals("UNAVAILABLE", task.path("result").path("searchStatus").asText());
        assertEquals(0, request(get("/api/resumes/" + resumeId + "/reports"), token, null).size());
        assertEquals(1, request(get("/api/resumes/" + resumeId + "/versions"), token, null).size());
        assertTrue(request(get("/api/analysis/tasks/capabilities"), token, null).path("demo").asBoolean());
    }

    @Test void savedJdTitleBecomesDesiredRoleWhenRoleInputIsEmpty() throws Exception {
        String token=user();
        long resumeId=request(post("/api/resumes"),token,Map.of("title","测试简历","content","使用 Java MySQL 完成后端项目","sourceFileName","test.txt")).path("id").asLong();
        long jobId=request(post("/api/jobs"),token,Map.of("title","Java 后端工程师","content","要求 Java MySQL Redis")).path("id").asLong();
        long taskId=request(post("/api/analysis/tasks"),token,Map.of("resumeId",resumeId,"jobId",jobId)).path("id").asLong();
        var result=await(token,taskId);
        assertEquals("SUCCEEDED",result.path("status").asText());
        assertEquals("Java 后端工程师",result.at("/result/careerAdvice/targetPlan/targetRole").asText());
        assertEquals("USER_JD",result.at("/result/careerAdvice/targetPlan/basis").asText());
        assertFalse(result.at("/result/careerAdvice/recommendedRoles").isEmpty());
    }

    @Test void importedFileCanBeSavedAndReviewedWithoutAnyJd() throws Exception {
        String token = user();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "candidate.txt", "text/plain",
                "Candidate\n\nProjects\nJava MySQL course project\n\nEducation\nUniversity".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var parsed = request(multipart("/api/resumes/parse").file(file), token, null);
        assertTrue(parsed.path("content").asText().contains("\n"));
        var resume = request(post("/api/resumes"), token, Map.of("title", "导入测试", "content", parsed.path("content").asText(), "sourceFileName", parsed.path("fileName").asText()));
        long resumeId=resume.path("id").asLong();
        var created = request(post("/api/analysis/tasks"), token, Map.of("resumeId", resumeId, "resumeOnly", true, "targetRole", "AI 应用开发工程师"));
        var task = await(token, created.path("id").asLong());
        assertEquals("SUCCEEDED", task.path("status").asText(), task.toString());
        var result=task.path("result");
        assertEquals("RESUME_REVIEW", result.path("analysisType").asText());
        assertEquals("COMPLETED", result.path("termination").asText());
        assertFalse(result.path("resumeReview").isEmpty());
        assertEquals("NOT_RUN", result.path("searchStatus").asText());
        assertEquals("", result.path("draftContent").asText());
        assertEquals("AI 应用开发工程师", result.at("/careerAdvice/targetPlan/targetRole").asText());
        assertEquals("ROLE_ONLY", result.at("/careerAdvice/targetPlan/basis").asText());
        assertEquals("Java 后端开发工程师", result.at("/careerAdvice/recommendedRoles/0/title").asText());
        assertFalse(result.at("/careerAdvice/targetPlan/priorities").isEmpty());
        assertEquals(1, request(get("/api/resumes/"+resumeId+"/versions"), token, null).size());
        assertEquals(1, request(get("/api/resumes/"+resumeId+"/reports"), token, null).size());
        var stored=request(get("/api/resumes/"+resumeId+"/reports"), token, null).get(0).path("report");
        assertEquals(result.path("careerAdvice"),stored.path("careerAdvice"));
    }
}
