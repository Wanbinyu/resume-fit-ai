package com.wanbinyu.resumefit.analysis;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/analysis/tasks")
public class AnalysisController {
    private final AnalysisTaskService service;

    public AnalysisController(AnalysisTaskService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<AnalysisDtos.TaskView> create(Authentication authentication,
                                                      @Valid @RequestBody AnalysisDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(CurrentUser.id(authentication), request));
    }

    @GetMapping
    public ApiResponse<List<AnalysisDtos.TaskView>> list(Authentication authentication) {
        return ApiResponse.ok(service.list(CurrentUser.id(authentication)));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AnalysisDtos.TaskView> get(Authentication authentication, @PathVariable Long taskId) {
        return ApiResponse.ok(service.get(CurrentUser.id(authentication), taskId));
    }

    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, @PathVariable Long taskId) {
        return service.stream(CurrentUser.id(authentication), taskId);
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<AnalysisDtos.TaskView> cancel(Authentication authentication, @PathVariable Long taskId) {
        return ApiResponse.ok(service.cancel(CurrentUser.id(authentication), taskId));
    }
}
