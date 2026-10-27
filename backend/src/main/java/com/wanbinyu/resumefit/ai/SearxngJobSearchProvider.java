package com.wanbinyu.resumefit.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 开源 SearXNG 的 JSON 接口适配。服务地址只能由部署者配置，不能来自用户/模型参数。 */
@Component
public class SearxngJobSearchProvider implements JobSearchProvider {

    private final String baseUrl;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public SearxngJobSearchProvider(
        @Value("${app.search.searxng-base-url:}") String baseUrl,
        ObjectMapper json
    ) {
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.json = json;
        if (!this.baseUrl.isBlank()) {
            URI uri = URI.create(this.baseUrl);
            if (
                uri.getHost() == null ||
                uri.getUserInfo() != null ||
                uri.getQuery() != null ||
                uri.getFragment() != null ||
                !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
            ) throw new IllegalArgumentException("SearXNG 地址需要是部署者指定的 HTTP(S) 服务地址");
        }
    }

    @Override
    public boolean available() {
        return !baseUrl.isBlank();
    }

    @Override
    public SearchResult search(String role, String city, String company, String experience) {
        if (!available()) return new SearchResult(
            "UNAVAILABLE",
            "请配置自建 SearXNG 地址，或粘贴 JD。",
            List.of()
        );
        try {
            String query = String.join(" ", role, city, company, experience, "岗位职责 任职要求");
            URI url = URI.create(
                baseUrl +
                    "/search?format=json&categories=general&q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8)
            );
            var request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .GET()
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() != 200) return new SearchResult(
                    "ERROR",
                    "SearXNG 不可用或未开放 JSON 接口，请粘贴 JD。",
                    List.of()
                );
                byte[] bytes = stream.readNBytes(100001);
                if (bytes.length > 100000) return new SearchResult(
                    "ERROR",
                    "搜索结果过大，请粘贴 JD。",
                    List.of()
                );
                List<Source> sources = new ArrayList<>();
                for (var item : json.readTree(bytes).path("results")) {
                    if (sources.size() >= 3) break;
                    String sourceUrl = item.path("url").asText();
                    if (!TavilyJobSearchProvider.publicUrl(sourceUrl)) continue;
                    sources.add(
                        new Source(
                            "source-" + (sources.size() + 1),
                            trim(item.path("title").asText(), 200),
                            sourceUrl,
                            trim(item.path("content").asText(), 1800),
                            Instant.now().toString(),
                            "SEARCH_EXCERPT"
                        )
                    );
                }
                return new SearchResult(
                    sources.isEmpty() ? "EMPTY" : "OK",
                    "SearXNG 搜索摘要：需核实来源、完整 JD 和招聘时效。",
                    List.copyOf(sources)
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new SearchResult("ERROR", "搜索已中断", List.of());
        } catch (Exception exception) {
            return new SearchResult("ERROR", "SearXNG 搜索失败，请检查服务或粘贴 JD。", List.of());
        }
    }

    private String trim(String value, int max) {
        return value.substring(0, Math.min(value.length(), max));
    }
}
