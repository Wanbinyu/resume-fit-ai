package com.wanbinyu.resumefit.career;

import com.wanbinyu.resumefit.analysis.*;
import com.wanbinyu.resumefit.common.BusinessException;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CareerAssistantService {
    public record Request(@NotNull @Positive Long resumeId, @Positive Long versionId, @Positive Long jobId,
                          @Size(max=120) String targetRole, @Pattern(regexp="OVERVIEW|MATCH|COMPANY") String intent,
                          @Positive Long companyId) {}
    private final CareerService career;
    private final AnalysisTaskService tasks;
    public CareerAssistantService(CareerService career, AnalysisTaskService tasks) { this.career=career; this.tasks=tasks; }
    @Transactional(readOnly=false)
    public AnalysisDtos.TaskView create(long userId, Request request) {
        String intent=request.intent()==null ? "OVERVIEW" : request.intent();
        var jobs=request.jobId()==null ? career.listJobs(userId,5) : List.of(career.job(userId,request.jobId()));
        if(intent.equals("MATCH") && jobs.isEmpty()) throw new BusinessException("请先收藏岗位JD，再进行岗位匹配");
        var companies=request.companyId()==null ? career.listCompanies(userId,3) : List.of(career.company(userId,request.companyId()));
        if(intent.equals("COMPANY") && companies.isEmpty()) throw new BusinessException("请先添加公司档案和信息来源");
        var context=new CareerAssistantContext(intent,career.profile(userId),List.copyOf(jobs),List.copyOf(companies),career.summarizeApplications(userId));
        // Career job IDs have a separate namespace. Never write them into legacy job_descriptions foreign references.
        var analysis=new AnalysisDtos.CreateRequest(request.resumeId(),null,request.targetRole(),null,null,null,null,false,request.versionId());
        return tasks.createAssistant(userId,analysis,context);
    }
}
