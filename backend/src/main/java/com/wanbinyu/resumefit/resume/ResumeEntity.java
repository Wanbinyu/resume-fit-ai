package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 初始简历记录。后续编辑内容保存在 ResumeVersionEntity 中，不用分析报告替代简历正文。 */
@Data
@TableName("resumes")
public class ResumeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String sourceFileName;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
