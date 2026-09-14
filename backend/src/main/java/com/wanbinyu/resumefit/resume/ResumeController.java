package com.wanbinyu.resumefit.resume;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/parse")
    public ApiResponse<ResumeDtos.ParseResponse> parse(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.parse(file));
    }

    @PostMapping
    public ApiResponse<ResumeDtos.View> create(Authentication authentication,
                                               @Valid @RequestBody ResumeDtos.CreateRequest request) {
        return ApiResponse.ok(resumeService.create(CurrentUser.id(authentication), request));
    }

    @GetMapping
    public ApiResponse<List<ResumeDtos.View>> list(Authentication authentication) {
        return ApiResponse.ok(resumeService.list(CurrentUser.id(authentication)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDtos.View> get(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(resumeService.getOwned(CurrentUser.id(authentication), id));
    }
}
