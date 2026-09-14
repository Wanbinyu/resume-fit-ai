package com.wanbinyu.resumefit.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.analysis.AnalysisTaskEntity;
import com.wanbinyu.resumefit.analysis.AnalysisTaskMapper;
import com.wanbinyu.resumefit.ai.AiUsageService;
import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.analysis.AnalysisDtos;
import com.wanbinyu.resumefit.overview.PublicOverviewService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AnalysisTaskMapper taskMapper;
    private final AiUsageService usageService;
    private final PublicOverviewService overviewService;
    private final ObjectMapper objectMapper;

    public AdminController(AnalysisTaskMapper taskMapper, AiUsageService usageService,
                           PublicOverviewService overviewService, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.usageService = usageService;
        this.overviewService = overviewService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        List<AnalysisTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Long> statuses = new LinkedHashMap<>();
        tasks.forEach(task -> statuses.merge(task.getStatus(), 1L, Long::sum));
        long completed = tasks.stream().filter(task -> task.getStartedAt() != null && task.getCompletedAt() != null)
                .mapToLong(task -> Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis()).sum();
        long measured = tasks.stream().filter(task -> task.getStartedAt() != null && task.getCompletedAt() != null).count();
        Map<String, Object> result = new LinkedHashMap<>(Map.of(
                "totalTasks", tasks.size(),
                "statuses", statuses,
                "successRate", tasks.isEmpty() ? 0 : Math.round(statuses.getOrDefault("SUCCEEDED", 0L) * 10000.0 / tasks.size()) / 100.0,
                "averageDurationMs", measured == 0 ? 0 : completed / measured,
                "generatedAt", LocalDateTime.now().toString()));
        result.put("aiUsage", usageService.summary());
        result.put("usageOverview", overviewService.overview());
        return ApiResponse.ok(result);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<AnalysisDtos.TaskView>> recentTasks(
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<AnalysisDtos.TaskView> tasks = taskMapper.selectList(new LambdaQueryWrapper<AnalysisTaskEntity>()
                        .orderByDesc(AnalysisTaskEntity::getCreatedAt)
                        .last("LIMIT " + safeLimit))
                .stream().map(task -> {
                    Map<String, Object> result = null;
                    if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
                        try {
                            result = objectMapper.readValue(task.getResultJson(), new TypeReference<>() {});
                        } catch (Exception ignored) {
                            result = Map.of("raw", task.getResultJson());
                        }
                    }
                    return new AnalysisDtos.TaskView(task.getId(), task.getStatus(), task.getProgress(),
                            task.getMessage(), task.getErrorMessage(), result, task.getCreatedAt(),
                            task.getStartedAt(), task.getCompletedAt());
                }).toList();
        return ApiResponse.ok(tasks);
    }
}
