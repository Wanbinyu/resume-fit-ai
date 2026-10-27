package com.wanbinyu.resumefit.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 持久化及汇总调用用量；Agent 路径使用模型报告的 token，用量未知时不估造账单。 */
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
        long inputTokens = records
            .stream()
            .mapToLong(record -> value(record.getInputTokens()))
            .sum();
        long outputTokens = records
            .stream()
            .mapToLong(record -> value(record.getOutputTokens()))
            .sum();
        long durationMs = records
            .stream()
            .mapToLong(record -> value(record.getDurationMs()))
            .sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalls", records.size());
        result.put("estimatedInputTokens", inputTokens);
        result.put("estimatedOutputTokens", outputTokens);
        result.put("totalDurationMs", durationMs);
        result.put("model", properties.model());
        return result;
    }

    public void recordAgent(Long taskId, String provider, Map<?, ?> usage, long durationMs) {
        // 每个模型请求一条用量记录，避免把多轮 Agent 当成一次调用。
        int rounds = ((Number) usage.get("modelRounds")).intValue();
        int input = ((Number) usage.get("reportedInputTokens")).intValue();
        int output = ((Number) usage.get("reportedOutputTokens")).intValue();
        for (int i = 0; i < rounds; i++) {
            AiUsageRecordEntity record = new AiUsageRecordEntity();
            record.setTaskId(taskId);
            record.setProvider(provider);
            record.setModelName(properties.model());
            // 汇总用量存第一条，其余记录为 0；保留调用次数，禁止猜测 token 或费用。
            record.setInputTokens(i == 0 ? input : 0);
            record.setOutputTokens(i == 0 ? output : 0);
            record.setDurationMs(i == 0 ? durationMs : 0);
            record.setCreatedAt(LocalDateTime.now());
            mapper.insert(record);
        }
    }

    private int estimateTokens(int characters) {
        return Math.max(1, (int) Math.ceil(Math.max(0, characters) / 4.0));
    }

    private long value(Number number) {
        return number == null ? 0 : number.longValue();
    }
}
