package com.wanbinyu.resumefit.analysis;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 历史报告查询接口；报告和简历版本分别管理，查看报告不会生成新版本。 */
@RestController
@RequestMapping("/api/resumes/{resumeId}/reports")
public class AnalysisReportController {

    private final AnalysisReportService service;

    public AnalysisReportController(AnalysisReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AnalysisReportDtos.View>> list(
        Authentication authentication,
        @PathVariable Long resumeId
    ) {
        return ApiResponse.ok(service.list(CurrentUser.id(authentication), resumeId));
    }
}
