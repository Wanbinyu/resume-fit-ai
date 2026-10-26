package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.*;
import com.wanbinyu.resumefit.ai.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CareerAdviceTest {
    final ObjectMapper json=new ObjectMapper();
    final String resume="合成候选人\n使用 Java 和 MySQL 完成课程项目，负责后端接口实现。";
    AgentRun run(String role) {
        var run=new AgentRun(role,"","","",()->resume,()->"岗位要求 Java Redis",()->false,p->{});
        run.loadResume(); run.resumeOnly=true; return run;
    }
    JsonNode read(String body,AgentRun run) throws Exception { return json.valueToTree(CareerAdvice.read(json.readTree(body),run)); }
    @Test void quotesMustBeLiteralAndMissingHighlightEvidenceIsRejected() throws Exception {
        var result=read("""
          {"recommendedRoles":[
            {"title":"Java开发","reason":"项目有证据","evidence":["使用 Java 和 MySQL 完成课程项目"]},
            {"title":"架构师","reason":"编造","evidence":["拥有十年架构经验"]}],
           "targetPlan":{"priorities":[
            {"category":"HIGHLIGHT","title":"突出项目","action":"说明职责","evidence":["负责后端接口实现"]},
            {"category":"HIGHLIGHT","title":"无依据","action":"写十年经验","evidence":[]},
            {"category":"VERIFY","title":"待核实","action":"核实测试工作","evidence":[]},
            {"category":"LEARN","title":"后续实践","action":"练习模型接入","evidence":[]},
            {"category":"VERIFY","title":"伪造引用","action":"补充","evidence":["十年经验"]}]}}
          """,run("AI 应用开发工程师"));
        assertEquals(1,result.path("recommendedRoles").size());
        assertEquals(3,result.path("omittedUnsupportedItems").asInt());
        var priorities=result.path("targetPlan").path("priorities");
        assertEquals(3,priorities.size());
        assertTrue(priorities.get(1).path("action").asText().startsWith("仅在确实做过并能核实时补充"));
        assertTrue(priorities.get(2).path("action").asText().startsWith("后续学习/实践建议，不写为已有经历"));
    }
    @Test void demoRecommendsExistingJavaEvidenceInsteadOfDesiredAiRole() {
        var run=run("AI 应用开发工程师");
        JsonNode result=json.valueToTree(CareerAdvice.read(CareerAdvice.demo(run,json),run));
        assertEquals("Java 后端开发工程师",result.path("recommendedRoles").get(0).path("title").asText());
        assertEquals(1,result.path("recommendedRoles").size());
        assertEquals("AI 应用开发工程师",result.at("/targetPlan/targetRole").asText());
        assertEquals("ROLE_ONLY",result.at("/targetPlan/basis").asText());
        assertEquals(3,result.at("/targetPlan/priorities").size());
    }
    @Test void noTargetDoesNotInventTargetAndDoesNotRecommendFromTitleOnly() {
        var run=run(""); run.resume="合成候选人\n求职意向 AI 应用开发工程师";
        JsonNode result=json.valueToTree(CareerAdvice.read(CareerAdvice.demo(run,json),run));
        assertTrue(result.path("recommendedRoles").isEmpty());
        assertEquals("NONE",result.at("/targetPlan/basis").asText());
        assertTrue(result.at("/targetPlan/priorities").isEmpty());
        assertFalse(result.at("/targetPlan/questions").isEmpty());
    }
    @Test void trustedContextDeterminesTargetAndJdBasisEvenWithConflictingModelFields() throws Exception {
        var run=run("Java 工程师"); run.resumeOnly=false; run.loadJob();
        var result=read("{\"targetPlan\":{\"targetRole\":\"伪造岗位\",\"basis\":\"ROLE_ONLY\"}}",run);
        assertEquals("Java 工程师",result.at("/targetPlan/targetRole").asText());
        assertEquals("USER_JD",result.at("/targetPlan/basis").asText());
        run.setJob("搜索摘要 Java","s1","SEARCH_EXCERPT");
        assertEquals("SEARCH_EXCERPT",read("{}",run).at("/targetPlan/basis").asText());
        run.resumeOnly=true;
        assertEquals("ROLE_ONLY",read("{}",run).at("/targetPlan/basis").asText());
        assertDoesNotThrow(()->read("null",run));
        assertTrue(read("{}",run).path("recommendedRoles").isEmpty());
    }
}
