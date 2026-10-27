package com.wanbinyu.resumefit.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ConfiguredJobSearchProvider implements JobSearchProvider {

    private final JobSearchProvider selected;

    public ConfiguredJobSearchProvider(
        @Value("${app.search.provider:auto}") String provider,
        TavilyJobSearchProvider tavily,
        SearxngJobSearchProvider searxng
    ) {
        selected = switch (provider.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "searxng" -> searxng;
            case "tavily" -> tavily;
            case "auto" -> searxng.available() ? searxng : tavily;
            default -> throw new IllegalArgumentException(
                "JOB_SEARCH_PROVIDER 只能为 auto、searxng 或 tavily"
            );
        };
    }

    @Override
    public boolean available() {
        return selected.available();
    }

    @Override
    public SearchResult search(String role, String city, String company, String experience) {
        return selected.search(role, city, company, experience);
    }
}
