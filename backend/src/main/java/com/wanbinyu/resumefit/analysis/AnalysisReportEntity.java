package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 成功分析生成的历史报告快照，与任务关联，不代表用户已保存或发布了简历版本。 */
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
