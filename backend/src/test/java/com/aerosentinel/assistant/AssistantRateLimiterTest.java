package com.aerosentinel.assistant;

import com.aerosentinel.assistant.service.AssistantRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AssistantRateLimiterTest {

    @Test
    @DisplayName("Rate Limiter permits requests within limit and rejects excess requests")
    void testRateLimiter_EnforcesLimit() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(5);
        String clientIp = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(clientIp), "Request " + (i + 1) + " should be allowed");
        }

        // 6th request within same minute should be rejected
        assertFalse(limiter.tryAcquire(clientIp), "6th request should exceed the rate limit");

        // A different client key should still be allowed
        assertTrue(limiter.tryAcquire("10.0.0.1"), "Different client key should be allowed");
    }

    @Test
    @DisplayName("Concurrency: Concurrent requests for same client strictly enforce limit without race conditions")
    void testRateLimiter_ConcurrentRequests() throws InterruptedException {
        int limit = 10;
        AssistantRateLimiter limiter = new AssistantRateLimiter(limit);
        String clientKey = "concurrent-client-1";

        int totalThreads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (limiter.tryAcquire(clientKey)) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "Threads should finish promptly");
        executor.shutdown();

        assertEquals(limit, allowedCount.get(), "Exactly " + limit + " requests must be permitted under high concurrency");
        assertEquals(totalThreads - limit, rejectedCount.get(), "Remaining requests must be rejected");
    }

    @Test
    @DisplayName("Memory Bounding: High volume of distinct clients triggers pruning and remains bounded")
    void testRateLimiter_MemoryBoundingAndEviction() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(5);

        // Simulate 12,000 distinct client requests (exceeding MAX_TRACKED_CLIENTS = 10,000)
        for (int i = 0; i < 12_000; i++) {
            boolean acquired = limiter.tryAcquire("client-" + i);
            assertTrue(acquired);
        }

        // Verify bounded memory capacity: tracked clients is bounded around MAX_TRACKED_CLIENTS
        assertTrue(limiter.getTrackedClientCount() <= 10_001,
                "Tracked client map must be bounded to MAX_TRACKED_CLIENTS, actual=" + limiter.getTrackedClientCount());

        // Limiter must remain responsive and functional
        String testClient = "test-client-new";
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(testClient), "Request " + (i + 1) + " should be allowed");
        }
        assertFalse(limiter.tryAcquire(testClient), "6th request should be rejected");
    }
}
