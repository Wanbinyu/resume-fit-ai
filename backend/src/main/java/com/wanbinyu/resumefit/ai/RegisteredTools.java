package com.wanbinyu.resumefit.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** 通用进程内注册/分发边界。真正的访问权限由服务端绑定函数负责，本类不是安全沙箱。 */
public final class RegisteredTools {
    @FunctionalInterface public interface Observer {
        void event(String name, String status, String callId, String message);
    }
    private record Entry(ToolDefinition definition, Set<String> required, Function<JsonNode, Object> handler) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final ObjectMapper json;
    private final BooleanSupplier unavailable;
    private final Observer observer;
    private static final int MAX_OUTPUT = 12000;

    public RegisteredTools(ObjectMapper json, BooleanSupplier unavailable, Observer observer) {
        this.json = json; this.unavailable = unavailable; this.observer = observer;
    }
    public void register(String name, String description, Set<String> required, Function<JsonNode, Object> handler) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,79}")) throw new IllegalArgumentException("工具名无效");
        required = Set.copyOf(required);
        Map<String, Object> properties = new LinkedHashMap<>();
        required.stream().sorted().forEach(key -> properties.put(key,
                Map.of("type", "string", "minLength", 1, "maxLength", 80)));
        String schema = encode(Map.of("type", "object", "properties", properties,
                "required", required.stream().sorted().toList(), "additionalProperties", false));
        var definition = ToolDefinition.builder().name(name).description(description).inputSchema(schema).build();
        if (entries.putIfAbsent(name, new Entry(definition, required, Objects.requireNonNull(handler))) != null)
            throw new IllegalStateException("重复工具名");
    }
    public List<ToolCallback> definitions() {
        return entries.values().stream().map(entry -> (ToolCallback) new ToolCallback() {
            public ToolDefinition getToolDefinition() { return entry.definition(); }
            public String call(String input) { throw new IllegalStateException("工具只能由受预算约束的 Agent 循环调用"); }
        }).toList();
    }
    public String execute(String name, String arguments, String callId) {
        var entry = entries.get(name);
        if (entry == null) return failed(name, callId, "UNKNOWN_TOOL", "未知工具");
        JsonNode args;
        try {
            if (arguments == null || arguments.length() > 2000) throw new IllegalArgumentException();
            try (var parser = json.getFactory().createParser(arguments)) {
                parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                args = json.readTree(parser);
                if (parser.nextToken() != null) throw new IllegalArgumentException();
            }
            if (args == null || !args.isObject() || args.size() != entry.required().size()) throw new IllegalArgumentException();
            for (String key : entry.required()) {
                var node = args.path(key);
                if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 80)
                    throw new IllegalArgumentException();
            }
        } catch (Exception exception) { return failed(name, callId, "INVALID_ARGUMENTS", "参数必须符合已注册 schema"); }
        if (unavailable.getAsBoolean()) return failed(name, callId, "CANCELLED", "任务已结束或取消");
        observer.event(name, "RUNNING", callId, "正在执行工具");
        try {
            String output = checkedOutput(entry.handler().apply(args));
            if (unavailable.getAsBoolean()) return failed(name, callId, "CANCELLED", "任务已结束或取消");
            observer.event(name, "SUCCEEDED", callId, "工具执行完成");
            return output;
        } catch (CancellationException exception) { return failed(name, callId, "CANCELLED", "任务已结束或取消"); }
        catch (com.wanbinyu.resumefit.common.BusinessException exception) {
            return failed(name, callId, "ACCESS_DENIED", "输入不存在或无访问权限");
        } catch (ResultTooLarge exception) { return failed(name, callId, "RESULT_TOO_LARGE", "工具结果超出长度限制"); }
        catch (IllegalArgumentException exception) {
            return failed(name, callId, "PRECONDITION_FAILED", "工具前置条件不满足，请先读取简历或岗位内容");
        } catch (Exception exception) { return failed(name, callId, "TOOL_ERROR", "工具执行失败，请补充 JD 或稍后重试"); }
    }
    /** 先验证输出，再提交状态；超长工具不能在内部被标记成功。 */
    public void validateResult(Object value) { checkedOutput(value); }
    /** 脱离供应器对象引用，迟到回调不能再修改已提交结果。 */
    public Object snapshotResult(Object value) {
        try { return json.readTree(checkedOutput(value)).path("data"); }
        catch (ResultTooLarge exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("工具结果无法保存"); }
    }
    private String checkedOutput(Object value) {
        String output = encode(Map.of("ok", true, "data", value == null ? Map.of() : value));
        if (output.length() > MAX_OUTPUT) throw new ResultTooLarge();
        return output;
    }
    private static final class ResultTooLarge extends RuntimeException {}
    private String failed(String name, String callId, String code, String message) {
        observer.event(name, "FAILED", callId, code + "：" + message);
        return encode(Map.of("ok", false, "error", Map.of("code", code, "message", message)));
    }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("JSON 序列化失败"); }
    }
}
