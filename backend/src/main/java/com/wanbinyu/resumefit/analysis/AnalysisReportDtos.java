package com.wanbinyu.resumefit.analysis;

import java.time.LocalDateTime;
import java.util.Map;

public final class AnalysisReportDtos {
    private AnalysisReportDtos() {}

    public record View(Long id, Long taskId, Long resumeId, Long jobId,
                       Integer score, Map<String, Object> report, LocalDateTime createdAt) {}
}
