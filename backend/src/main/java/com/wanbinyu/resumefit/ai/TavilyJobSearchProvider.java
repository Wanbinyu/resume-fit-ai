package com.wanbinyu.resumefit.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class TavilyJobSearchProvider implements JobSearchProvider {
    private final String apiKey;
    private final ObjectMapper json;
    // 固定 HTTPS API，不接受模型提供的 URL；禁止重定向，不抓取搜索结果页面。
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public TavilyJobSearchProvider(@Value("${app.search.api-key:}") String apiKey, ObjectMapper json) {
        this.apiKey = apiKey;
        this.json = json;
    }

    @Override
    public boolean available() { return !apiKey.isBlank(); }
    @Override
    public SearchResult search(String role, String city, String company, String experience) {
        if (apiKey.isBlank()) return new SearchResult("UNAVAILABLE", "未配置搜索服务，请粘贴目标岗位 JD。", List.of());
        try {
            // 仅发送用户主动填写的岗位条件，绝不发送简历或模型自由拼接的查询。
            String query = String.join(" ", role, city, company, experience, "岗位职责 任职要求");
            String body = json.writeValueAsString(Map.of("query", query, "max_results", 3,
                    "include_raw_content", false, "search_depth", "basic"));
            var request = HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
                    .timeout(Duration.ofSeconds(12)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            // 限制读取字节数，不把外部异常正文或凭据写进日志/前端。
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() != 200) return new SearchResult("ERROR", "搜索服务暂不可用，请粘贴 JD。", List.of());
                byte[] bytes = stream.readNBytes(100001);
                if (bytes.length > 100000) return new SearchResult("ERROR", "搜索响应过大，请粘贴 JD。", List.of());
                var root = json.readTree(bytes);
                List<Source> sources = new ArrayList<>();
                for (var item : root.path("results")) {
                    if (sources.size() >= 3) break;
                    String url = item.path("url").asText();
                    if (!publicUrl(url)) continue;
                    sources.add(new Source("source-" + (sources.size() + 1), trim(item.path("title").asText(), 200),
                            url, trim(item.path("content").asText(), 1800), Instant.now().toString(), "SEARCH_EXCERPT"));
                }
                return new SearchResult(sources.isEmpty() ? "EMPTY" : "OK",
                        "搜索摘要仅供岗位需求参考，未验证为当前在招岗位或完整 JD；请核实来源。", List.copyOf(sources));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new SearchResult("ERROR", "搜索已中断", List.of());
        } catch (Exception exception) {
            return new SearchResult("ERROR", "搜索失败，请粘贴 JD。", List.of());
        }
    }

    public static boolean publicUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.contains(".") && !host.matches("[0-9.]+") && !host.contains(":")
                    && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal");
        } catch (Exception ignored) { return false; }
    }
    private static String trim(String value, int max) { return value.substring(0, Math.min(value.length(), max)); }
}
