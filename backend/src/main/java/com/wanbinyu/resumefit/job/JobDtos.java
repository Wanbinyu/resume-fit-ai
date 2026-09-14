package com.wanbinyu.resumefit.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class JobDtos {
    private JobDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 160) String title,
                                @NotBlank @Size(max = 3000) String content) {}

    public record View(Long id, String title, String content, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
