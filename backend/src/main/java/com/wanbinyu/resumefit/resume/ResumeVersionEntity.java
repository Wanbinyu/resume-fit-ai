package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 某份简历的具体版本。resumeId 关联初始简历，analysisTaskId 可关联建议来源，published 决定能否公开分享。 */
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
