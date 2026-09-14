package com.wanbinyu.resumefit.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.analysis.AnalysisTaskEntity;
import com.wanbinyu.resumefit.analysis.AnalysisTaskMapper;
import com.wanbinyu.resumefit.ai.AiProperties;
import com.wanbinyu.resumefit.ai.AiUsageService;
import com.wanbinyu.resumefit.job.JobDescriptionEntity;
import com.wanbinyu.resumefit.job.JobDescriptionMapper;
import com.wanbinyu.resumefit.resume.ResumeAccessLogService;
import com.wanbinyu.resumefit.resume.ResumeEntity;
import com.wanbinyu.resumefit.resume.ResumeMapper;
import com.wanbinyu.resumefit.user.UserEntity;
import com.wanbinyu.resumefit.user.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicOverviewService {
    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobMapper;
    private final AnalysisTaskMapper taskMapper;
    private final ResumeAccessLogService accessLogService;
    private final AiProperties aiProperties;
    private final AiUsageService usageService;

    public PublicOverviewService(UserMapper userMapper, ResumeMapper resumeMapper,
                                 JobDescriptionMapper jobMapper, AnalysisTaskMapper taskMapper,
                                 ResumeAccessLogService accessLogService, AiProperties aiProperties,
                                 AiUsageService usageService) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.taskMapper = taskMapper;
        this.accessLogService = accessLogService;
        this.aiProperties = aiProperties;
        this.usageService = usageService;
    }

    public Map<String, Object> overview() {
        List<AnalysisTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<>());
        long succeeded = tasks.stream().filter(task -> "SUCCEEDED".equals(task.getStatus())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "Resume Fit AI");
        result.put("status", "UP");
        result.put("aiMode", aiProperties.demoMode() ? "demo" : "spring-ai");
        result.put("totalUsers", userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()));
        result.put("totalResumes", resumeMapper.selectCount(new LambdaQueryWrapper<ResumeEntity>()));
        result.put("totalJobs", jobMapper.selectCount(new LambdaQueryWrapper<JobDescriptionEntity>()));
        result.put("totalAnalysisTasks", tasks.size());
        result.put("successfulAnalysisTasks", succeeded);
        result.put("analysisSuccessRate", tasks.isEmpty() ? 0 : Math.round(succeeded * 10000.0 / tasks.size()) / 100.0);
        result.put("totalResumeViews", accessLogService.count());
        result.put("todayResumeViews", accessLogService.countSince(LocalDate.now().atStartOfDay()));
        result.put("aiUsage", usageService.summary());
        result.put("generatedAt", LocalDateTime.now().toString());
        return result;
    }
}
