package com.wanbinyu.resumefit.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiUsageService {
    private final AiUsageRecordMapper mapper;
    private final AiProperties properties;

    public AiUsageService(AiUsageRecordMapper mapper, AiProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public void record(Long taskId, String provider, int inputChars, int outputChars, long durationMs) {
        AiUsageRecordEntity record = new AiUsageRecordEntity();
        record.setTaskId(taskId);
        record.setProvider(provider);
        record.setModelName(properties.model());
        record.setInputTokens(estimateTokens(inputChars));
        record.setOutputTokens(estimateTokens(outputChars));
        record.setDurationMs(Math.max(0, durationMs));
        record.setCreatedAt(LocalDateTime.now());
        mapper.insert(record);
    }

    public Map<String, Object> summary() {
        List<AiUsageRecordEntity> records = mapper.selectList(new LambdaQueryWrapper<>());
        long inputTokens = records.stream().mapToLong(record -> value(record.getInputTokens())).sum();
        long outputTokens = records.stream().mapToLong(record -> value(record.getOutputTokens())).sum();
        long durationMs = records.stream().mapToLong(record -> value(record.getDurationMs())).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalls", records.size());
        result.put("estimatedInputTokens", inputTokens);
        result.put("estimatedOutputTokens", outputTokens);
        result.put("totalDurationMs", durationMs);
        result.put("model", properties.model());
        return result;
    }

    private int estimateTokens(int characters) {
        return Math.max(1, (int) Math.ceil(Math.max(0, characters) / 4.0));
    }

    private long value(Number number) {
        return number == null ? 0 : number.longValue();
    }
}
