package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_reports")
public class AnalysisReportEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long userId;
    private Long resumeId;
    private Long jobId;
    private Integer score;
    private String reportJson;
    private LocalDateTime createdAt;
}
