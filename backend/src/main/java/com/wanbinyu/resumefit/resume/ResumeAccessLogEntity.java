package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_access_logs")
public class ResumeAccessLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long versionId;
    private Long viewerUserId;
    private String source;
    private LocalDateTime accessedAt;
}
