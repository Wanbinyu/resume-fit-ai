package com.wanbinyu.resumefit.analysis;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;

public final class AnalysisDtos {
    private AnalysisDtos() {}

    public record CreateRequest(@NotNull Long resumeId, @NotNull Long jobId) {}

    public record TaskView(Long id, String status, Integer progress, String message,
                           String errorMessage, Map<String, Object> result,
                           LocalDateTime createdAt, LocalDateTime startedAt,
                           LocalDateTime completedAt) {}
}
