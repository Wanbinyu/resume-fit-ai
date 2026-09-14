package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_versions")
public class ResumeVersionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private Integer versionNo;
    private String title;
    private String source;
    private Long targetJobId;
    private Long analysisTaskId;
    private String content;
    private String shareToken;
    private Boolean published;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
