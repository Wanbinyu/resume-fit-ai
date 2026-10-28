package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.*;
import com.wanbinyu.resumefit.analysis.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDateTime;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.ai.demo-mode=true","spring.datasource.url=jdbc:h2:mem:career_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class CareerDomainApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisTaskRecovery recovery;
    private JsonNode call(MockHttpServletRequestBuilder request,String token,Object body,boolean success) throws Exception {
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response=mvc.perform(request).andReturn().getResponse();
        JsonNode parsed=json.readTree(response.getContentAsByteArray());
        assertEquals(success,parsed.path("success").asBoolean(),parsed.toString());
        if(success) assertEquals(200,response.getStatus()); else assertTrue(response.getStatus()>=400 && response.getStatus()<500,parsed.toString());
        return success ? parsed.path("data") : parsed;
    }
    private JsonNode ok(MockHttpServletRequestBuilder req,String token,Object body)throws Exception{return call(req,token,body,true);}
    private String user()throws Exception{return ok(post("/api/auth/register"),null,Map.of("username","career-"+UUID.randomUUID().toString().substring(0,12),"password","local-test-password-123","displayName","测试账号")).path("token").asText();}
    private long resume(String token)throws Exception{return ok(post("/api/resumes"),token,Map.of("title","测试简历","content","教育背景：计算机本科\n项目经历：负责 Java MySQL 项目开发\n完成接口交付","sourceFileName","test.txt")).path("id").asLong();}
    private JsonNode job(String token)throws Exception{return ok(post("/api/career/jobs"),token,Map.of("title","Java 开发","companyName","测试公司","description","Java MySQL Redis","sourceUrl","https://example.com/jobs/1")).deepCopy();}
    private JsonNode await(String token,long id)throws Exception{
        for(int i=0;i<150;i++){var value=ok(get("/api/analysis/tasks/"+id),token,null); if(List.of("SUCCEEDED","FAILED","NEEDS_INPUT","CANCELLED").contains(value.path("status").asText()))return value; Thread.sleep(40);}
        fail("task did not finish");return null;
    }
    @Test void profileUsesRevisionAndValidatesSalaryAndLimit()throws Exception{
        String token=user();
        assertEquals(0,ok(get("/api/career/profile"),token,null).path("revision").asInt());
        var saved=ok(put("/api/career/profile"),token,Map.of("targetRoles",List.of("Java"),"salaryMin",10000,"salaryMax",20000,"revision",0));
        assertEquals(1,saved.path("revision").asInt());
        call(put("/api/career/profile"),token,Map.of("notes","stale","revision",0),false);
        call(put("/api/career/profile"),token,Map.of("salaryMin",20000,"salaryMax",10000,"revision",1),false);
        call(get("/api/career/jobs?limit=101"),token,null,false);
        assertEquals("Java",ok(get("/api/career/profile"),token,null).at("/targetRoles/0").asText());
    }
    @Test void recordsArePrivateAndApplicationsAreIdempotentWithHistory()throws Exception{
        String owner=user(),other=user(); var job=job(owner); long id=job.path("id").asLong();
        assertEquals(0,ok(get("/api/career/jobs"),other,null).size());
        call(get("/api/career/jobs/"+id),other,null,false);
        call(post("/api/career/applications"),other,Map.of("jobId",id,"stage","APPLIED"),false);
        var app=ok(post("/api/career/applications"),owner,Map.of("jobId",id,"stage","APPLIED"));
        var repeated=ok(post("/api/career/applications"),owner,Map.of("jobId",id,"stage","SAVED"));
        assertEquals(app.path("id"),repeated.path("id")); assertEquals("APPLIED",repeated.path("stage").asText());
        ok(patch("/api/career/applications/"+app.path("id").asLong()),owner,Map.of("stage","INTERVIEW","revision",1));
        call(patch("/api/career/applications/"+app.path("id").asLong()),owner,Map.of("stage","OFFER","revision",1),false);
        call(patch("/api/career/applications/"+app.path("id").asLong()),owner,Map.of("stage","INVALID","revision",2),false);
        assertEquals(2,ok(get("/api/career/applications/"+app.path("id").asLong()+"/events"),owner,null).size());
        call(get("/api/career/applications/"+app.path("id").asLong()+"/events"),other,null,false);
        ok(patch("/api/career/jobs/"+id),owner,Map.of("status","ARCHIVED","revision",1));
        assertEquals(0,ok(get("/api/career/jobs"),owner,null).size());
        assertEquals(1,ok(get("/api/career/jobs?includeArchived=true"),owner,null).size());
    }
    @Test void companyPreservesUncertaintyAndRejectsUnsafeLinksOrForeignReferences()throws Exception{
        String token=user(),other=user();
        var company=ok(post("/api/career/companies"),token,Map.of("name","测试公司","evidence",List.of(Map.of("type","OPINION","category","WORKLIFE","title","一条主观看法","content","待核实的个人观点","sourceUrl","https://example.com/review","observedAt","2026-01-01"))));
        assertTrue(company.at("/evidenceAssessment/reputationScore").isNull());
        assertEquals(1,company.at("/evidenceAssessment/opinionCount").asInt());
        assertEquals("INSUFFICIENT_DATA",company.at("/evidenceAssessment/status").asText());
        call(get("/api/career/companies/"+company.path("id").asLong()),other,null,false);
        call(post("/api/career/companies"),token,Map.of("name","X","website","javascript:alert(1)"),false);
        call(post("/api/career/jobs"),other,Map.of("title","Java","companyName","测试公司","companyId",company.path("id").asLong(),"description","Java"),false);
        call(post("/api/career/jobs"),token,Map.of("title","Java","companyName","名称不一致","companyId",company.path("id").asLong(),"description","Java"),false);
    }
    @Test void selectedVersionIsFrozenAndAssistantActuallyUsesRegisteredTools()throws Exception{
        String token=user();long resume=resume(token);
        long version=ok(post("/api/resumes/"+resume+"/versions"),token,Map.of("title","Python版","content","教育背景：本科\n项目：负责 Python 开发并交付")).path("id").asLong();
        long job=job(token).path("id").asLong();
        var created=ok(post("/api/career/assistant/tasks"),token,Map.of("resumeId",resume,"versionId",version,"jobId",job,"intent","MATCH"));
        long id=created.path("id").asLong();
        var frozen=json.readTree(jdbc.queryForObject("SELECT input_snapshot_json FROM analysis_tasks WHERE id=?",String.class,id));
        assertTrue(frozen.path("resumeContent").asText().contains("Python"));
        assertEquals(version,frozen.path("versionId").asLong());
        ok(put("/api/resumes/"+resume+"/versions/"+version),token,Map.of("title","已修改","content","修改后仅有 Java"));
        assertEquals(frozen,json.readTree(jdbc.queryForObject("SELECT input_snapshot_json FROM analysis_tasks WHERE id=?",String.class,id)));
        var task=await(token,id);assertEquals("SUCCEEDED",task.path("status").asText(),task.toString());
        var result=task.path("result");assertEquals("CAREER_ASSISTANT",result.path("analysisType").asText());
        assertEquals(0,result.at("/assistant/jobMatches/0/coverage").asInt());
        assertFalse(result.at("/assistant/resumeQuality/dimensions").isEmpty());
        assertTrue(result.path("agentTrace").toString().contains("evaluate_saved_jobs"));
        assertEquals(7,result.at("/usage/toolCalls").asInt());
        String other=user();long otherResume=resume(other);
        call(post("/api/career/assistant/tasks"),other,Map.of("resumeId",otherResume,"versionId",version,"intent","OVERVIEW"),false);
        call(post("/api/career/assistant/tasks"),other,Map.of("resumeId",otherResume,"jobId",job,"intent","MATCH"),false);
    }
    @Test void emptyOverviewSucceedsButExplicitMissingDataAndMalformedInputsDoNot()throws Exception{
        String token=user();long resume=resume(token);
        var task=ok(post("/api/career/assistant/tasks"),token,Map.of("resumeId",resume,"intent","OVERVIEW"));
        var done=await(token,task.path("id").asLong());assertEquals("SUCCEEDED",done.path("status").asText(),done.toString());
        assertEquals(0,done.at("/result/assistant/jobMatches").size());
        call(post("/api/career/assistant/tasks"),token,Map.of("resumeId",resume,"intent","MATCH"),false);
        call(post("/api/career/assistant/tasks"),token,Map.of("resumeId",resume,"intent","COMPANY"),false);
        call(post("/api/career/assistant/tasks"),token,Map.of("resumeId",resume,"intent","SHELL"),false);
        assertEquals(200,mvc.perform(get("/assets/workspace/workspace.js")).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(get("/job-assistant.html")).andReturn().getResponse().getStatus());
    }
    @Test void recoveryExpiresAbandonedTasksAndLeavesTerminalStatesUntouched()throws Exception{
        String token=user();long resume=resume(token);
        long user=jdbc.queryForObject("SELECT user_id FROM resumes WHERE id=?",Long.class,resume);
        for(String status:List.of("RUNNING","PENDING","CANCELLED","SUCCEEDED"))jdbc.update("INSERT INTO analysis_tasks(user_id,resume_id,status,created_at,started_at) VALUES(?,?,?,?,?)",user,resume,status,LocalDateTime.now().minusHours(1),LocalDateTime.now().minusHours(1));
        recovery.expire(LocalDateTime.now());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM analysis_tasks WHERE resume_id=? AND status='FAILED'",Integer.class,resume));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM analysis_tasks WHERE resume_id=? AND status='CANCELLED'",Integer.class,resume));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM analysis_tasks WHERE resume_id=? AND status='SUCCEEDED'",Integer.class,resume));
    }
}
