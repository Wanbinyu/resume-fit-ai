package com.wanbinyu.resumefit.overview;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.resume.ResumeAccessLogService;
import com.wanbinyu.resumefit.resume.ResumeVersionDtos;
import com.wanbinyu.resumefit.resume.ResumeVersionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicOverviewController {
    private final PublicOverviewService overviewService;
    private final ResumeAccessLogService accessLogService;
    private final ResumeVersionService versionService;

    public PublicOverviewController(PublicOverviewService overviewService,
                                    ResumeAccessLogService accessLogService, ResumeVersionService versionService) {
        this.overviewService = overviewService;
        this.accessLogService = accessLogService;
        this.versionService = versionService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(overviewService.overview());
    }

    @PostMapping("/resumes/{resumeId}/access")
    public ApiResponse<Void> recordResumeAccess(@PathVariable Long resumeId,
                                                @RequestParam(required = false) String source) {
        accessLogService.record(resumeId, null, source);
        return ApiResponse.ok();
    }

    @org.springframework.web.bind.annotation.GetMapping("/shares/{shareToken}")
    public ApiResponse<ResumeVersionDtos.PublicView> sharedResume(@PathVariable String shareToken) {
        return ApiResponse.ok(versionService.publicView(shareToken));
    }
}
