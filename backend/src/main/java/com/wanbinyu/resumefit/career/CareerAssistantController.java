package com.wanbinyu.resumefit.career;

import com.wanbinyu.resumefit.analysis.AnalysisDtos;
import com.wanbinyu.resumefit.ai.AiProperties;
import com.wanbinyu.resumefit.common.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/career/assistant")
public class CareerAssistantController {
    private final CareerAssistantService service;
    private final AiProperties limits;
    public CareerAssistantController(CareerAssistantService service,AiProperties limits) { this.service=service; this.limits=limits; }
    @PostMapping("/tasks") public ApiResponse<AnalysisDtos.TaskView> create(Authentication auth,@Valid @RequestBody CareerAssistantService.Request request) { return ApiResponse.ok(service.create(CurrentUser.id(auth),request)); }
    @GetMapping("/capabilities") public ApiResponse<Map<String,Object>> capabilities() {
        return ApiResponse.ok(Map.of("demo",limits.demoMode(),"model",limits.model(),"maxRounds",limits.maxSteps(),
                "timeoutSeconds",limits.timeoutSeconds(),"liveJobs",false,"reputationScore",false,"writesAllowed",false,
                "notice","当前使用账号内保存的数据；公司证据不等于信誉评分，Agent工具仅供只读分析"));
    }
}
