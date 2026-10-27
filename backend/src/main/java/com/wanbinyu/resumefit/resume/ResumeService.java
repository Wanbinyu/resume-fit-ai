package com.wanbinyu.resumefit.resume;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/** 管理初始简历与文件解析；创建简历时同时创建原始版本，读取受用户所有权限制。 */
@Service
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final FileTextExtractor extractor;
    private final ResumeAccessLogService accessLogService;
    private final ResumeVersionService versionService;

    public ResumeService(
        ResumeMapper resumeMapper,
        FileTextExtractor extractor,
        ResumeAccessLogService accessLogService,
        ResumeVersionService versionService
    ) {
        this.resumeMapper = resumeMapper;
        this.extractor = extractor;
        this.accessLogService = accessLogService;
        this.versionService = versionService;
    }

    @Transactional
    public ResumeDtos.View create(Long userId, ResumeDtos.CreateRequest request) {
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(request.title());
        entity.setContent(request.content().trim());
        entity.setSourceFileName(request.sourceFileName());
        resumeMapper.insert(entity);
        versionService.createOriginal(entity);
        return view(entity);
    }

    public ResumeDtos.ParseResponse parse(MultipartFile file) {
        String content = extractor.extract(file);
        return new ResumeDtos.ParseResponse(file.getOriginalFilename(), content, content.length());
    }

    public ResumeDtos.View getOwned(Long userId, Long id) {
        ResumeEntity entity = resumeMapper.selectOne(
            new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, id)
                .eq(ResumeEntity::getUserId, userId)
        );
        if (entity == null) throw new BusinessException("简历不存在");
        accessLogService.record(id, userId, "authenticated");
        return view(entity);
    }

    public List<ResumeDtos.View> list(Long userId) {
        return resumeMapper
            .selectList(
                new LambdaQueryWrapper<ResumeEntity>()
                    .eq(ResumeEntity::getUserId, userId)
                    .orderByDesc(ResumeEntity::getUpdatedAt)
            )
            .stream()
            .map(this::view)
            .toList();
    }

    /** 供任务等内部业务读取；检查归属，但不增加面向用户页面的访问计数。 */
    public ResumeEntity requireOwned(Long userId, Long id) {
        ResumeEntity entity = resumeMapper.selectOne(
            new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, id)
                .eq(ResumeEntity::getUserId, userId)
        );
        if (entity == null) throw new BusinessException("简历不存在");
        return entity;
    }

    private ResumeDtos.View view(ResumeEntity entity) {
        return new ResumeDtos.View(
            entity.getId(),
            entity.getTitle(),
            entity.getSourceFileName(),
            entity.getContent(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
