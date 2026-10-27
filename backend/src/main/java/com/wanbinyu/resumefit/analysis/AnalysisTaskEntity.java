package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 持久化的任务状态、输入和结果。resultJson 在运行中可保存进度，结束后保存最终分析结果。 */
@Data
@TableName("analysis_tasks")
public class AnalysisTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long resumeId;
    private Long jobId;
    private String requestJson;
    private String status;
    private Integer progress;
    private String message;
    private String resultJson;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
