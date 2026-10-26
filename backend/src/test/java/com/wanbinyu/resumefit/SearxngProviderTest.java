package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wanbinyu.resumefit.ai.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** 本机 Mock HTTP 服务，验证开源搜索适配协议，不访问公共搜索、不冒充真实岗位。 */
class SearxngProviderTest {
    @Test void jsonSearchSupportsSelfHostedServiceAndHandlesEmptyErrorsSizeAndRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger mode = new AtomicInteger(), trap = new AtomicInteger();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/search", exchange -> {
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            String body = switch (mode.get()) {
                case 0 -> "{\"results\":[{\"title\":\"MOCK 岗位\",\"url\":\"https://example.com/jobs/1\",\"content\":\"Java Redis\"},{\"url\":\"https://127.0.0.1/private\",\"content\":\"blocked\"}]}";
                case 1 -> "{\"results\":[]}";
                case 2 -> "<html>JSON disabled</html>";
                case 3 -> "x".repeat(100001);
                default -> "redirect";
            };
            int status = mode.get() == 2 ? 403 : mode.get() == 4 ? 302 : 200;
            if (status == 302) exchange.getResponseHeaders().add("Location", "/trap");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/trap", exchange -> { trap.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        try {
            var provider = new SearxngJobSearchProvider("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
            var result = provider.search("AI 应用开发", "成都", "", "应届");
            assertEquals("OK", result.status()); assertEquals(1, result.sources().size());
            assertEquals("SEARCH_EXCERPT", result.sources().getFirst().kind());
            assertTrue(query.get().contains("format=json")); assertTrue(query.get().contains("成都"));
            assertTrue(query.get().contains("AI 应用开发"));
            mode.set(1); assertEquals("EMPTY", provider.search("Java", "", "", "").status());
            mode.set(2); assertEquals("ERROR", provider.search("Java", "", "", "").status());
            mode.set(3); assertEquals("ERROR", provider.search("Java", "", "", "").status());
            mode.set(4); assertEquals("ERROR", provider.search("Java", "", "", "").status()); assertEquals(0, trap.get());
        } finally { server.stop(0); }
        var noConfig = new SearxngJobSearchProvider("", new ObjectMapper());
        assertFalse(noConfig.available()); assertEquals("UNAVAILABLE", noConfig.search("Java", "", "", "").status());
        assertThrows(IllegalArgumentException.class, () -> new SearxngJobSearchProvider("file:///etc", new ObjectMapper()));
    }
}
