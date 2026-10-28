package com.wanbinyu.resumefit;

import com.wanbinyu.resumefit.common.BusinessException;
import com.wanbinyu.resumefit.resume.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 只使用独立内存数据库和 demo provider，不加载个人文件或调用外部服务。 */
@SpringBootTest(properties = {"spring.config.import=", "app.ai.demo-mode=true", "app.search.api-key=",
        "spring.datasource.url=jdbc:h2:mem:version_boundary;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class ResumeVersionBoundaryTest {
    @Autowired ResumeVersionService service;
    @Autowired ResumeMapper resumes;
    @Autowired ResumeVersionMapper versions;
    private static final long USER = 9501L;

    private ResumeEntity resume() {
        var resume = new ResumeEntity();
        resume.setUserId(USER); resume.setTitle("边界测试"); resume.setContent("测试原文");
        resumes.insert(resume);
        service.createOriginal(resume);
        return resume;
    }

    private ResumeVersionDtos.View version(long resumeId, String content) {
        return service.create(USER, resumeId,
                new ResumeVersionDtos.CreateRequest("测试版本", content, "MANUAL", null, null));
    }

    private ResumeVersionDtos.CompareView compare(String from, String to) {
        var resume = resume();
        return service.compare(USER, resume.getId(), version(resume.getId(), from).id(),
                version(resume.getId(), to).id());
    }

    @Test void simultaneousSavesAllocateDistinctConsecutiveNumbers() throws Exception {
        var resume = resume();
        int count = 8;
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException("并发测试未启动");
                return version(resume.getId(), "并发保存原文").versionNo();
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var actual = new HashSet<Integer>();
            for (var future : futures) actual.add(future.get(15, TimeUnit.SECONDS));
            assertEquals(count, actual.size());
            for (int n = 2; n <= count + 1; n++) assertTrue(actual.contains(n));
        } finally { start.countDown(); }
    }

    @Test void comparisonCountsReorderingAndRepeatedLines() {
        var reordered = compare("甲\n乙\n丙", "甲\n丙\n乙");
        assertEquals(1, reordered.changedLines());
        assertEquals(1, reordered.addedLines());
        assertEquals(1, reordered.removedLines());
        var duplicate = compare("甲\n乙\n甲", "乙\n甲");
        assertEquals(0, duplicate.addedLines());
        assertEquals(1, duplicate.removedLines());
        assertEquals(1, duplicate.changedLines());
    }

    @Test void comparisonHandlesWhitespaceInsertionsAndReplacement() {
        assertEquals(0, compare(" 甲 \r\n\r\n乙", "甲\n乙").changedLines());
        var inserted = compare("甲\n乙", "首\n甲\n乙");
        assertEquals(1, inserted.addedLines()); assertEquals(0, inserted.removedLines());
        var replacement = compare("甲\n乙", "甲\n丙");
        assertEquals(1, replacement.changedLines());
        assertEquals(1, replacement.addedLines()); assertEquals(1, replacement.removedLines());
    }

    @Test void maximumLineCountUsesBoundedMemoryAndTime() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            var result = compare("甲\n".repeat(2999), "乙\n".repeat(2999));
            assertEquals(2999, result.changedLines());
        });
    }

    @Test void ownershipAndVersionNumberOverflowFailWithoutSaving() {
        var resume = resume();
        assertThrows(BusinessException.class, () -> service.create(USER + 1, resume.getId(),
                new ResumeVersionDtos.CreateRequest("越权", "内容", "MANUAL", null, null)));
        var max = new ResumeVersionEntity();
        max.setResumeId(resume.getId()); max.setUserId(USER); max.setVersionNo(Integer.MAX_VALUE);
        max.setTitle("上限"); max.setSource("MANUAL"); max.setContent("内容"); max.setPublished(false);
        versions.insert(max);
        assertThrows(BusinessException.class, () -> version(resume.getId(), "不能溢出"));
    }

    @Test void oversizedLegacyContentCannotStartUnboundedComparison() {
        var resume = resume();
        var oversized = version(resume.getId(), "甲".repeat(6001));
        var other = version(resume.getId(), "乙");
        assertThrows(BusinessException.class,
                () -> service.compare(USER, resume.getId(), oversized.id(), other.id()));
    }
}
