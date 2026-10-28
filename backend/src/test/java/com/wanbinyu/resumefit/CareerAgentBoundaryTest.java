package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.ai.*;
import com.wanbinyu.resumefit.career.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.ToolCallback;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CareerAgentBoundaryTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final JobSearchProvider unavailable=(r,c,o,e)->new JobSearchProvider.SearchResult("UNAVAILABLE","offline",List.of());
    private AgentRun run() { return new AgentRun("Java","","","",()->"教育背景 本科\n项目负责 Java MySQL 开发并交付",()->"",()->false,e->{}); }
    private CareerDtos.JobView job(long id,String jd,LocalDate expires) {
        return new CareerDtos.JobView(id,"Java岗位","公司",null,"",null,null,jd,"https://example.com/"+"x".repeat(975),LocalDate.now(),expires,"ACTIVE",1,LocalDateTime.now(),LocalDateTime.now());
    }
    private CareerAssistantContext context(List<CareerDtos.JobView> jobs,List<CareerDtos.CompanyView> companies) {
        return new CareerAssistantContext("OVERVIEW",new CareerDtos.ProfileView(List.of(),List.of(),null,null,false,List.of(),"",0,null),jobs,companies,Map.of("total",0,"stageCounts",Map.of(),"overdue",List.of()));
    }
    @Test void unknownVocabularyHasNoFakeZeroAndExpiredHighCoverageIsRankedAfterLiveJobs()throws Exception {
        var run=run();var context=context(List.of(job(1,"Java MySQL",LocalDate.now().minusDays(1)),job(2,"Java Redis",null),job(3,"客户沟通与文案",null)),List.of());
        run.assistantTools=CareerAssistantTools.bind(run,context,new ResumeAgentTools());
        var registry=new AgentToolRegistry(json,run,new ResumeAgentTools(),unavailable);
        registry.execute("load_resume","{}","load");
        var output=json.readTree(registry.execute("evaluate_saved_jobs","{}","match"));
        assertTrue(output.path("ok").asBoolean());
        var matches=output.path("data");
        assertEquals(2,matches.get(0).path("jobId").asLong());
        assertEquals(3,matches.get(1).path("jobId").asLong());
        assertTrue(matches.get(1).path("coverage").isNull());
        assertTrue(matches.get(2).path("expired").asBoolean());
    }
    @Test void maximumAcceptedJobAndCompanyFieldsStayWithinToolOutputBudget()throws Exception {
        var evidence=new CareerDtos.EvidenceInput("UNVERIFIED","RISK","t".repeat(160),"正文".repeat(600),"https://example.com/"+"x".repeat(975),LocalDate.now());
        var company=new CareerDtos.CompanyView(1,"n".repeat(160),"i".repeat(100),"https://example.com/"+"x".repeat(975),Collections.nCopies(30,evidence),CompanyEvidenceAssessment.inspect(Collections.nCopies(30,evidence)),1,LocalDateTime.now(),LocalDateTime.now());
        var run=run();run.assistantTools=CareerAssistantTools.bind(run,context(Collections.nCopies(5,job(1,"Java MySQL Redis",null)),Collections.nCopies(3,company)),new ResumeAgentTools());
        var registry=new AgentToolRegistry(json,run,new ResumeAgentTools(),unavailable);
        assertTrue(json.readTree(registry.execute("load_resume","{}","load")).path("ok").asBoolean());
        for(String tool:run.assistantTools.keySet()) {
            String output=registry.execute(tool,"{}",tool);
            assertTrue(json.readTree(output).path("ok").asBoolean(),tool+":"+output);
            assertTrue(output.length()<=12000);
        }
    }
    @Test void modelTransientFailureRetriesOnlyOnceWithinTheSameBudgets() {
        AtomicInteger calls=new AtomicInteger();
        AiProvider provider=new AiProvider(){
            final DemoAiProvider demo=new DemoAiProvider();
            public String name(){return "mock";}
            public Turn turn(List<Message> messages,List<ToolCallback> tools,int max){
                if(calls.getAndIncrement()==0)throw new IllegalStateException("HTTP 429 transient");
                return demo.turn(messages,tools,max);
            }
        };
        var run=run();run.assistantTools=CareerAssistantTools.bind(run,context(List.of(),List.of()),new ResumeAgentTools());
        var agent=new ResumeOptimizationAgent(provider,json,new AiProperties(true,6,"mock"),new ResumeAgentTools(),unavailable);
        var result=agent.analyze(run);
        assertEquals("COMPLETED",result.get("termination"),result.toString());
        assertEquals(1,((Map<?,?>)result.get("usage")).get("providerRetries"));
        assertEquals(4,calls.get());
        assertFalse(run.isActive());
    }
    @Test void failedModelNeverReportsUnreadResumeAsZeroQualityAndAlwaysSeals() {
        AiProvider provider=new AiProvider(){public String name(){return "mock";}public Turn turn(List<Message> messages,List<ToolCallback> tools,int max){throw new IllegalStateException("HTTP 401 secret");}};
        var run=run();
        var result=new ResumeOptimizationAgent(provider,json,new AiProperties(true,6,"mock"),new ResumeAgentTools(),unavailable).analyze(run);
        assertEquals("MODEL_ERROR",result.get("termination"));
        assertFalse(((Map<?,?>)result.get("resumeQuality")).containsKey("score"));
        assertFalse(run.isActive());assertFalse(result.toString().contains("secret"));
    }
    @Test void largeSingleLineQuotesRemainVerbatimBoundedAndContainTheKeyword() {
        String text="前".repeat(3000)+"Java MySQL Redis"+"后".repeat(2000);
        String quote=EvidenceQuotes.forKeyword(text,"MySQL");
        assertTrue(text.contains(quote));assertTrue(quote.contains("MySQL"));assertTrue(quote.length()<=240);
        assertEquals("真正的 Java 交付",EvidenceQuotes.forKeyword("JavaScript项目\n真正的 Java 交付","Java"));
        assertTrue(EvidenceQuotes.forKeyword("İ".repeat(3000)+" MySQL", "MySQL").contains("MySQL"));
        var run=new AgentRun("","","","",()->text,()->"Java MySQL Redis",()->false,e->{});
        var registry=new AgentToolRegistry(json,run,new ResumeAgentTools(),unavailable);
        registry.execute("load_resume","{}","a");registry.execute("read_job_description","{\"sourceId\":\"user\"}","b");
        assertTrue(registry.execute("match_resume_evidence","{}","c").contains("\"ok\":true"));
    }
}
