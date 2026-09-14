package com.wanbinyu.resumefit.job;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobDescriptionController {
    private final JobDescriptionService service;

    public JobDescriptionController(JobDescriptionService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<JobDtos.View> create(Authentication authentication, @Valid @RequestBody JobDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(CurrentUser.id(authentication), request));
    }

    @GetMapping
    public ApiResponse<List<JobDtos.View>> list(Authentication authentication) {
        return ApiResponse.ok(service.list(CurrentUser.id(authentication)));
    }
}
