package com.wanbinyu.resumefit.analysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.common.BusinessException;
import com.wanbinyu.resumefit.resume.ResumeMapper;
import com.wanbinyu.resumefit.resume.ResumeEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnalysisReportService {
    private final AnalysisReportMapper reportMapper;
    private final ResumeMapper resumeMapper;
    private final ObjectMapper objectMapper;

    public AnalysisReportService(AnalysisReportMapper reportMapper, ResumeMapper resumeMapper,
                                 ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.resumeMapper = resumeMapper;
        this.objectMapper = objectMapper;
    }

    public List<AnalysisReportDtos.View> list(Long userId, Long resumeId) {
        ResumeEntity resume = resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, resumeId).eq(ResumeEntity::getUserId, userId));
        if (resume == null) throw new BusinessException("简历不存在");
        return reportMapper.selectList(new LambdaQueryWrapper<AnalysisReportEntity>()
                        .eq(AnalysisReportEntity::getResumeId, resumeId)
                        .eq(AnalysisReportEntity::getUserId, userId)
                        .orderByDesc(AnalysisReportEntity::getCreatedAt))
                .stream().map(this::view).toList();
    }

    private AnalysisReportDtos.View view(AnalysisReportEntity report) {
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(report.getReportJson(), new TypeReference<>() {});
        } catch (Exception ignored) {
            result = Map.of("raw", report.getReportJson());
        }
        return new AnalysisReportDtos.View(report.getId(), report.getTaskId(), report.getResumeId(),
                report.getJobId(), report.getScore(), result, report.getCreatedAt());
    }
}
