package com.wanbinyu.resumefit.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ResumeDtos {
    private ResumeDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 160) String title,
                                @NotBlank @Size(max = 6000) String content,
                                @Size(max = 255) String sourceFileName) {}

    public record View(Long id, String title, String sourceFileName, String content,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ParseResponse(String fileName, String content, int characterCount) {}
}
