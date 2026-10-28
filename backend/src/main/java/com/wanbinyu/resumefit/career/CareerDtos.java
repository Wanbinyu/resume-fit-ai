package com.wanbinyu.resumefit.career;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** HTTP and read-only tool contracts. A missing value means unknown, not zero confidence. */
public final class CareerDtos {
    private CareerDtos() {}

    public record ProfileInput(
            @Size(max = 6) List<@NotBlank @Size(max = 80) String> targetRoles,
            @Size(max = 8) List<@NotBlank @Size(max = 40) String> preferredCities,
            @Min(0) @Max(2000000) Integer salaryMin,
            @Min(0) @Max(2000000) Integer salaryMax,
            Boolean remoteAccepted,
            @Size(max = 8) List<@NotBlank @Size(max = 40) String> priorities,
            @Size(max = 1000) String notes,
            @PositiveOrZero Long revision) {}

    public record ProfileView(List<String> targetRoles, List<String> preferredCities,
                              Integer salaryMin, Integer salaryMax, boolean remoteAccepted,
                              List<String> priorities, String notes, long revision,
                              LocalDateTime updatedAt) {}

    public record JobInput(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 160) String companyName,
            @Positive Long companyId,
            @Size(max = 80) String location,
            @Min(0) @Max(2000000) Integer salaryMin,
            @Min(0) @Max(2000000) Integer salaryMax,
            @NotBlank @Size(max = 3000) String description,
            @Size(max = 1000) String sourceUrl,
            LocalDate collectedAt,
            LocalDate expiresAt) {}

    public record JobPatch(@NotBlank String status, @NotNull @PositiveOrZero Long revision) {}

    public record JobView(long id, String title, String companyName, Long companyId,
                          String location, Integer salaryMin, Integer salaryMax,
                          String description, String sourceUrl, LocalDate collectedAt,
                          LocalDate expiresAt, String status, long revision,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record EvidenceInput(
            @NotBlank @Pattern(regexp = "FACT|OPINION|UNVERIFIED") String type,
            @NotBlank @Pattern(regexp = "IDENTITY|BUSINESS|COMPENSATION|WORKLIFE|RISK|OTHER") String category,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1200) String content,
            @Size(max = 1000) String sourceUrl,
            @NotNull LocalDate observedAt) {}

    public record CompanyInput(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 100) String industry,
            @Size(max = 1000) String website,
            @Size(max = 30) List<@Valid EvidenceInput> evidence,
            @PositiveOrZero Long revision) {}

    /** Source coverage is informational. It is deliberately not a company-reputation score. */
    public record EvidenceAssessment(String status, Integer reputationScore, int factCount,
                                     int opinionCount, int unverifiedCount, int linkedSourceCount,
                                     int staleSourceCount, List<String> coveredCategories,
                                     List<String> questions, String notice) {}

    public record CompanyView(long id, String name, String industry, String website,
                              List<EvidenceInput> evidence, EvidenceAssessment evidenceAssessment,
                              long revision, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ApplicationInput(@NotNull @Positive Long jobId,
                                    @NotBlank String stage,
                                    @Size(max = 2000) String notes,
                                    LocalDateTime nextActionAt) {}

    public record ApplicationPatch(@NotBlank String stage,
                                    @Size(max = 2000) String notes,
                                    LocalDateTime nextActionAt,
                                    @NotNull @PositiveOrZero Long revision) {}

    public record ApplicationView(long id, long jobId, String jobTitle, String companyName,
                                  String stage, String notes, LocalDateTime nextActionAt,
                                  long revision, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ApplicationEventView(long id, long applicationId, String fromStage,
                                       String toStage, String action, LocalDateTime occurredAt) {}
}
