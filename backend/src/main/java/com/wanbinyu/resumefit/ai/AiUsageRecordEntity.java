package com.wanbinyu.resumefit.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_usage_records")
public class AiUsageRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String provider;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long durationMs;
    private LocalDateTime createdAt;
}
