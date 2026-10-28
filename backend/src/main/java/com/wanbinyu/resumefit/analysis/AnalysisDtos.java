package com.wanbinyu.resumefit.analysis;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

public final class AnalysisDtos {
    private AnalysisDtos() {}

    public record CreateRequest(@NotNull Long resumeId, Long jobId,
                                @Size(max = 120) String targetRole, @Size(max = 80) String city,
                                @Size(max = 120) String company, @Size(max = 80) String experience,
                                @Size(max = 3000) String jobDescription, Boolean resumeOnly, Long versionId) {
        public CreateRequest(Long resumeId, Long jobId, String targetRole, String city, String company, String experience, String jobDescription, Boolean resumeOnly) {
            this(resumeId, jobId, targetRole, city, company, experience, jobDescription, resumeOnly, null);
        }
        public CreateRequest(Long resumeId, Long jobId) { this(resumeId, jobId, null, null, null, null, null, false); }
        public CreateRequest(Long resumeId, Long jobId, String targetRole, String city, String company, String experience, String jobDescription) {
            this(resumeId, jobId, targetRole, city, company, experience, jobDescription, false);
        }
    }

    public record TaskView(Long id, String status, Integer progress, String message,
                           String errorMessage, Map<String, Object> result,
                           LocalDateTime createdAt, LocalDateTime startedAt,
                           LocalDateTime completedAt) {}
}
