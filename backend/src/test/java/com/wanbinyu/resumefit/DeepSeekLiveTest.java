package com.wanbinyu.resumefit;

import com.wanbinyu.resumefit.ai.ResumeOptimizationAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 需要显式开关才消耗真实额度；只用合成输入，不上传个人简历。 */
@EnabledIfEnvironmentVariable(named="RF_REAL_MODEL_TEST", matches="true")
@SpringBootTest(properties={"app.ai.demo-mode=false", "app.search.api-key=", "app.ai.max-steps=6",
        "app.ai.max-output-tokens=1800", "logging.level.org.springframework.ai=OFF",
        "spring.datasource.url=jdbc:h2:mem:live_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class DeepSeekLiveTest {
    @Autowired ResumeOptimizationAgent agent;
    @Test void syntheticCareerAdviceUsesResumeEvidenceAndPreservesDesiredRole() {
        String resume="合成测试候选人\n\n教育背景\n计算机相关专业本科。\n\n课程项目\n使用 Java 和 MySQL 完成课程项目，负责后端接口实现。\n使用 Git 管理代码，使用 Linux 部署课程项目。";
        var run=new com.wanbinyu.resumefit.ai.AgentRun("AI 应用开发工程师","","","",()->resume,()->"",()->false,p->{});
        run.resumeOnly=true;
        var result=agent.analyze(run);
        System.out.println("LIVE_CAREER termination="+result.get("termination")+" usage="+result.get("usage"));
        assertEquals("COMPLETED",result.get("termination"));
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
        var roles=tree.at("/careerAdvice/recommendedRoles");
        assertFalse(roles.isEmpty(),tree.path("careerAdvice").toString());
        for(var role:roles) for(var quote:role.path("evidence")) assertTrue(resume.contains(quote.asText()));
        assertEquals("AI 应用开发工程师",tree.at("/careerAdvice/targetPlan/targetRole").asText());
        assertEquals("ROLE_ONLY",tree.at("/careerAdvice/targetPlan/basis").asText());
        assertFalse(tree.at("/careerAdvice/targetPlan/priorities").isEmpty());
        assertEquals("RESUME_REVIEW",tree.path("analysisType").asText());
        assertEquals("NOT_RUN",tree.path("searchStatus").asText());
    }
    @Test void syntheticInputCompletesRealBoundedToolLoop() {
        var result = agent.analyze("合成测试候选人\n使用 Java 和 MySQL 完成课程项目。", "岗位要求 Java MySQL Redis。Redis 暂无简历证据。");
        System.out.println("LIVE_AGENT_RESULT termination="+result.get("termination")+" usage="+result.get("usage"));
        assertEquals("COMPLETED", result.get("termination"));
        assertFalse(result.get("draftContent").toString().contains("Redis"));
        assertTrue(((Number)((Map<?,?>)result.get("usage")).get("modelRounds")).intValue() <= 6);
    }
    @Test void syntheticResumeReviewNeedsNoJobDescription() {
        var run = new com.wanbinyu.resumefit.ai.AgentRun("", "", "", "",
                () -> "合成测试候选人\n\n教育背景\n计算机相关专业本科。\n\n课程项目\n使用 Java 和 MySQL 完成课程项目，负责接口实现。",
                () -> "", () -> false, progress -> {});
        run.resumeOnly = true;
        var result = agent.analyze(run);
        System.out.println("LIVE_RESUME_REVIEW termination="+result.get("termination")+" errorCode="+result.get("modelErrorCode")+" usage="+result.get("usage"));
        assertEquals("COMPLETED", result.get("termination"));
        assertEquals("RESUME_REVIEW", result.get("analysisType"));
        assertEquals("NOT_RUN", result.get("searchStatus"));
        assertEquals("", result.get("draftContent"));
    }
}
