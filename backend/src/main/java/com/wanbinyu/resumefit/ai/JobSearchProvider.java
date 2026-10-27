package com.wanbinyu.resumefit.ai;

import java.util.List;

/** 搜索是可替换的边界；测试注入 Mock，上线使用服务端凭据。 */
public interface JobSearchProvider {
    default boolean available() {
        return true;
    }

    SearchResult search(String role, String city, String company, String experience);

    record Source(String id, String title, String url, String excerpt, String retrievedAt, String kind) {}

    record SearchResult(String status, String message, List<Source> sources) {}
}
