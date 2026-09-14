package com.wanbinyu.resumefit.resume;

import com.wanbinyu.resumefit.common.ApiResponse;
import com.wanbinyu.resumefit.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resumes/{resumeId}/versions")
public class ResumeVersionController {
    private final ResumeVersionService service;

    public ResumeVersionController(ResumeVersionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ResumeVersionDtos.View>> list(Authentication authentication,
                                                           @PathVariable Long resumeId) {
        return ApiResponse.ok(service.list(CurrentUser.id(authentication), resumeId));
    }

    @GetMapping("/{versionId}")
    public ApiResponse<ResumeVersionDtos.View> get(Authentication authentication,
                                                    @PathVariable Long resumeId, @PathVariable Long versionId) {
        return ApiResponse.ok(service.get(CurrentUser.id(authentication), resumeId, versionId));
    }

    @PostMapping
    public ApiResponse<ResumeVersionDtos.View> create(Authentication authentication,
                                                       @PathVariable Long resumeId,
                                                       @Valid @RequestBody ResumeVersionDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(CurrentUser.id(authentication), resumeId, request));
    }

    @PutMapping("/{versionId}")
    public ApiResponse<ResumeVersionDtos.View> update(Authentication authentication,
                                                       @PathVariable Long resumeId, @PathVariable Long versionId,
                                                       @Valid @RequestBody ResumeVersionDtos.UpdateRequest request) {
        return ApiResponse.ok(service.update(CurrentUser.id(authentication), resumeId, versionId, request));
    }

    @PostMapping("/{versionId}/publish")
    public ApiResponse<ResumeVersionDtos.View> publish(Authentication authentication,
                                                        @PathVariable Long resumeId, @PathVariable Long versionId) {
        return ApiResponse.ok(service.publish(CurrentUser.id(authentication), resumeId, versionId));
    }

    @DeleteMapping("/{versionId}/publish")
    public ApiResponse<ResumeVersionDtos.View> unpublish(Authentication authentication,
                                                          @PathVariable Long resumeId, @PathVariable Long versionId) {
        return ApiResponse.ok(service.unpublish(CurrentUser.id(authentication), resumeId, versionId));
    }

    @GetMapping("/compare")
    public ApiResponse<ResumeVersionDtos.CompareView> compare(Authentication authentication,
                                                              @PathVariable Long resumeId,
                                                              @RequestParam Long from,
                                                              @RequestParam Long to) {
        return ApiResponse.ok(service.compare(CurrentUser.id(authentication), resumeId, from, to));
    }
}
