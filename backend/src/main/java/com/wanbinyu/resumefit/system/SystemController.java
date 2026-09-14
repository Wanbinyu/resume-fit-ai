package com.wanbinyu.resumefit.system;

import com.wanbinyu.resumefit.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class SystemController {
    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "resume-fit-backend", "time", Instant.now().toString()));
    }
}
