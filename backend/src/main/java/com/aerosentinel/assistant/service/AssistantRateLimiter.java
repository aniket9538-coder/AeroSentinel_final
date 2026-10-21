package com.aerosentinel.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe, memory-bounded in-memory sliding-window rate limiter for the citizen assistant.
 * Enforces per-client request limits while protecting process memory from unbounded growth.
 */
@Component
public class AssistantRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AssistantRateLimiter.class);
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long WINDOW_DURATION_MS = 60_000L; // 60 seconds

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> requestLogs = new ConcurrentHashMap<>();

    public AssistantRateLimiter(@Value("${app.assistant.rate-limit.requests-per-minute:30}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = Math.max(5, maxRequestsPerMinute);
    }

    /**
     * Checks if a request from the given client key (e.g. validated IP address) is permitted.
     * @return true if permitted, false if rate limit exceeded.
     */
    public boolean tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            clientKey = "anonymous-default";
        }

        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_DURATION_MS;

        // Prevent memory exhaustion: prune empty/expired entries if client count is high
        if (requestLogs.size() > MAX_TRACKED_CLIENTS) {
            pruneEntries(windowStart);
        }

        ConcurrentLinkedQueue<Long> timestamps = requestLogs.computeIfAbsent(clientKey, k -> new ConcurrentLinkedQueue<>());

        // Evict timestamps older than 60 seconds
        while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
            timestamps.poll();
        }

        if (timestamps.size() >= maxRequestsPerMinute) {
            return false;
        }

        timestamps.offer(now);
        return true;
    }

    private synchronized void pruneEntries(long windowStart) {
        if (requestLogs.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }

        // 1. Evict entries that are already expired (> 60s old) or empty
        Iterator<Map.Entry<String, ConcurrentLinkedQueue<Long>>> it = requestLogs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ConcurrentLinkedQueue<Long>> entry = it.next();
            ConcurrentLinkedQueue<Long> queue = entry.getValue();
            while (!queue.isEmpty() && queue.peek() < windowStart) {
                queue.poll();
            }
            if (queue.isEmpty()) {
                it.remove();
            }
        }

        // 2. Hard memory bound: if client count still exceeds MAX_TRACKED_CLIENTS under burst traffic,
        // evict oldest excess entries to guarantee bounded heap memory
        if (requestLogs.size() > MAX_TRACKED_CLIENTS) {
            int toEvict = requestLogs.size() - MAX_TRACKED_CLIENTS + 100;
            Iterator<Map.Entry<String, ConcurrentLinkedQueue<Long>>> evictIt = requestLogs.entrySet().iterator();
            while (evictIt.hasNext() && toEvict > 0) {
                evictIt.next();
                evictIt.remove();
                toEvict--;
            }
            log.info("AssistantRateLimiter: Hard capacity cap enforced. Active tracked clients: {}", requestLogs.size());
        }
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public int getTrackedClientCount() {
        return requestLogs.size();
    }

    public void clear() {
        requestLogs.clear();
    }
}
