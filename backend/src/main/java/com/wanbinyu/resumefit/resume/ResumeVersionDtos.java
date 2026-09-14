package com.wanbinyu.resumefit.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ResumeVersionDtos {
    private ResumeVersionDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 6000) String content,
            @Size(max = 30) String source,
            Long targetJobId,
            Long analysisTaskId) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 6000) String content) {}

    public record View(Long id, Long resumeId, Integer versionNo, String title,
                       String source, Long targetJobId, Long analysisTaskId,
                       String content, Boolean published, String sharePath,
                       long viewCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record CompareView(View from, View to, int changedLines,
                              int addedLines, int removedLines) {}

    public record PublicView(Integer versionNo, String title, String content,
                             long viewCount, LocalDateTime publishedAt) {}
}
