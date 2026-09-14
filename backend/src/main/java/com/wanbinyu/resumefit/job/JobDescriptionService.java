package com.wanbinyu.resumefit.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanbinyu.resumefit.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobDescriptionService {
    private final JobDescriptionMapper mapper;

    public JobDescriptionService(JobDescriptionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public JobDtos.View create(Long userId, JobDtos.CreateRequest request) {
        JobDescriptionEntity entity = new JobDescriptionEntity();
        entity.setUserId(userId);
        entity.setTitle(request.title());
        entity.setContent(request.content().trim());
        mapper.insert(entity);
        return view(entity);
    }

    public List<JobDtos.View> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<JobDescriptionEntity>()
                        .eq(JobDescriptionEntity::getUserId, userId).orderByDesc(JobDescriptionEntity::getUpdatedAt))
                .stream().map(this::view).toList();
    }

    public JobDescriptionEntity requireOwned(Long userId, Long id) {
        JobDescriptionEntity entity = mapper.selectOne(new LambdaQueryWrapper<JobDescriptionEntity>()
                .eq(JobDescriptionEntity::getId, id).eq(JobDescriptionEntity::getUserId, userId));
        if (entity == null) throw new BusinessException("岗位 JD 不存在");
        return entity;
    }

    private JobDtos.View view(JobDescriptionEntity entity) {
        return new JobDtos.View(entity.getId(), entity.getTitle(), entity.getContent(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
