package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.common.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ResumeAccessLogService {
    private final ResumeAccessLogMapper mapper;
    private final ResumeMapper resumeMapper;

    public ResumeAccessLogService(ResumeAccessLogMapper mapper, ResumeMapper resumeMapper) {
        this.mapper = mapper;
        this.resumeMapper = resumeMapper;
    }

    public void record(Long resumeId, Long viewerUserId, String source) {
        record(resumeId, null, viewerUserId, source);
    }

    public void record(Long resumeId, Long versionId, Long viewerUserId, String source) {
        if (resumeMapper.selectById(resumeId) == null) {
            throw new BusinessException("简历不存在");
        }
        ResumeAccessLogEntity log = new ResumeAccessLogEntity();
        log.setResumeId(resumeId);
        log.setVersionId(versionId);
        log.setViewerUserId(viewerUserId);
        log.setSource(normalizeSource(source));
        log.setAccessedAt(LocalDateTime.now());
        mapper.insert(log);
    }

    public long count() {
        return mapper.selectCount(null);
    }

    public long countSince(LocalDateTime time) {
        return mapper.selectCount(new LambdaQueryWrapper<ResumeAccessLogEntity>()
                .ge(ResumeAccessLogEntity::getAccessedAt, time));
    }

    public long countForVersion(Long versionId) {
        return mapper.selectCount(new LambdaQueryWrapper<ResumeAccessLogEntity>()
                .eq(ResumeAccessLogEntity::getVersionId, versionId));
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "unknown";
        String value = source.trim();
        return value.substring(0, Math.min(value.length(), 40));
    }
}
