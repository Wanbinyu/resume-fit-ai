package com.wanbinyu.resumefit.analysis;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resumes/{resumeId}/reports")
public class AnalysisReportController {
    private final AnalysisReportService service;

    public AnalysisReportController(AnalysisReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AnalysisReportDtos.View>> list(Authentication authentication,
                                                            @PathVariable Long resumeId) {
        return ApiResponse.ok(service.list(CurrentUser.id(authentication), resumeId));
    }
}
