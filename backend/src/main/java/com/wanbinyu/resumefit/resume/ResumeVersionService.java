package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.analysis.AnalysisTaskEntity;
import com.wanbinyu.resumefit.analysis.AnalysisTaskMapper;
import com.wanbinyu.resumefit.common.BusinessException;
import com.wanbinyu.resumefit.job.JobDescriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ResumeVersionService {
    private final ResumeVersionMapper versionMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeAccessLogService accessLogService;
    private final JobDescriptionService jobService;
    private final AnalysisTaskMapper taskMapper;
    private final SecureRandom random = new SecureRandom();

    public ResumeVersionService(ResumeVersionMapper versionMapper, ResumeMapper resumeMapper,
                                ResumeAccessLogService accessLogService, JobDescriptionService jobService,
                                AnalysisTaskMapper taskMapper) {
        this.versionMapper = versionMapper;
        this.resumeMapper = resumeMapper;
        this.accessLogService = accessLogService;
        this.jobService = jobService;
        this.taskMapper = taskMapper;
    }

    @Transactional
    public void createOriginal(ResumeEntity resume) {
        ResumeVersionEntity version = new ResumeVersionEntity();
        version.setResumeId(resume.getId());
        version.setUserId(resume.getUserId());
        version.setVersionNo(1);
        version.setTitle(resume.getTitle() + " · 原始版");
        version.setSource("ORIGINAL");
        version.setContent(resume.getContent());
        version.setPublished(false);
        versionMapper.insert(version);
    }

    public List<ResumeVersionDtos.View> list(Long userId, Long resumeId) {
        requireResume(userId, resumeId);
        return versionMapper.selectList(new LambdaQueryWrapper<ResumeVersionEntity>()
                        .eq(ResumeVersionEntity::getResumeId, resumeId)
                        .eq(ResumeVersionEntity::getUserId, userId)
                        .orderByDesc(ResumeVersionEntity::getVersionNo))
                .stream().map(this::view).toList();
    }

    public ResumeVersionDtos.View get(Long userId, Long resumeId, Long versionId) {
        return view(requireOwned(userId, resumeId, versionId));
    }

    @Transactional
    public ResumeVersionDtos.View create(Long userId, Long resumeId, ResumeVersionDtos.CreateRequest request) {
        requireResume(userId, resumeId);
        if (request.targetJobId() != null) jobService.requireOwned(userId, request.targetJobId());
        if (request.analysisTaskId() != null) requireTask(userId, request.analysisTaskId(), resumeId);
        Integer nextNo = versionMapper.selectList(new LambdaQueryWrapper<ResumeVersionEntity>()
                        .eq(ResumeVersionEntity::getResumeId, resumeId)
                        .orderByDesc(ResumeVersionEntity::getVersionNo))
                .stream().map(ResumeVersionEntity::getVersionNo).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        ResumeVersionEntity version = new ResumeVersionEntity();
        version.setResumeId(resumeId);
        version.setUserId(userId);
        version.setVersionNo(nextNo);
        version.setTitle(request.title().trim());
        version.setSource(normalizeSource(request.source()));
        version.setTargetJobId(request.targetJobId());
        version.setAnalysisTaskId(request.analysisTaskId());
        version.setContent(request.content().trim());
        version.setPublished(false);
        versionMapper.insert(version);
        return view(version);
    }

    @Transactional
    public ResumeVersionDtos.View update(Long userId, Long resumeId, Long versionId,
                                         ResumeVersionDtos.UpdateRequest request) {
        ResumeVersionEntity version = requireOwned(userId, resumeId, versionId);
        version.setTitle(request.title().trim());
        version.setContent(request.content().trim());
        version.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return view(version);
    }

    @Transactional
    public ResumeVersionDtos.View publish(Long userId, Long resumeId, Long versionId) {
        ResumeVersionEntity version = requireOwned(userId, resumeId, versionId);
        if (version.getShareToken() == null || version.getShareToken().isBlank()) {
            version.setShareToken(createToken());
        }
        version.setPublished(true);
        version.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return view(version);
    }

    @Transactional
    public ResumeVersionDtos.View unpublish(Long userId, Long resumeId, Long versionId) {
        ResumeVersionEntity version = requireOwned(userId, resumeId, versionId);
        version.setPublished(false);
        version.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return view(version);
    }

    public ResumeVersionDtos.CompareView compare(Long userId, Long resumeId, Long fromId, Long toId) {
        ResumeVersionDtos.View from = get(userId, resumeId, fromId);
        ResumeVersionDtos.View to = get(userId, resumeId, toId);
        Set<String> fromLines = lines(from.content());
        Set<String> toLines = lines(to.content());
        int added = (int) toLines.stream().filter(line -> !fromLines.contains(line)).count();
        int removed = (int) fromLines.stream().filter(line -> !toLines.contains(line)).count();
        return new ResumeVersionDtos.CompareView(from, to, Math.max(added, removed), added, removed);
    }

    public ResumeVersionDtos.PublicView publicView(String shareToken) {
        ResumeVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getShareToken, shareToken)
                .eq(ResumeVersionEntity::getPublished, true).last("LIMIT 1"));
        if (version == null) throw new BusinessException("分享链接不存在或已失效");
        accessLogService.record(version.getResumeId(), version.getId(), null, "share");
        return new ResumeVersionDtos.PublicView(version.getVersionNo(), version.getTitle(), version.getContent(),
                accessLogService.countForVersion(version.getId()), version.getUpdatedAt());
    }

    private ResumeVersionEntity requireOwned(Long userId, Long resumeId, Long versionId) {
        ResumeVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getId, versionId)
                .eq(ResumeVersionEntity::getResumeId, resumeId)
                .eq(ResumeVersionEntity::getUserId, userId));
        if (version == null) throw new BusinessException("简历版本不存在");
        return version;
    }

    private void requireResume(Long userId, Long resumeId) {
        if (resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, resumeId).eq(ResumeEntity::getUserId, userId)) == null) {
            throw new BusinessException("简历不存在");
        }
    }

    private void requireTask(Long userId, Long taskId, Long resumeId) {
        AnalysisTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<AnalysisTaskEntity>()
                .eq(AnalysisTaskEntity::getId, taskId).eq(AnalysisTaskEntity::getUserId, userId)
                .eq(AnalysisTaskEntity::getResumeId, resumeId));
        if (task == null) throw new BusinessException("分析任务不存在或不属于该简历");
    }

    private ResumeVersionDtos.View view(ResumeVersionEntity version) {
        return new ResumeVersionDtos.View(version.getId(), version.getResumeId(), version.getVersionNo(),
                version.getTitle(), version.getSource(), version.getTargetJobId(), version.getAnalysisTaskId(),
                version.getContent(), Boolean.TRUE.equals(version.getPublished()) ? true : false,
                Boolean.TRUE.equals(version.getPublished()) ? "/api/public/shares/" + version.getShareToken() : null,
                accessLogService.countForVersion(version.getId()), version.getCreatedAt(), version.getUpdatedAt());
    }

    private Set<String> lines(String content) {
        return java.util.Arrays.stream(content.split("\\R"))
                .map(String::trim).filter(line -> !line.isBlank()).collect(Collectors.toSet());
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "MANUAL";
        return source.trim().toUpperCase().substring(0, Math.min(source.trim().length(), 30));
    }

    private String createToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
