package com.aerosentinel.citizen;

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
 * Thread-safe, memory-bounded sliding-window rate limiter for citizen receipt reference lookups.
 * Defends against automated brute-force guessing and reference enumeration.
 */
@Component
public class CitizenLookupRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CitizenLookupRateLimiter.class);
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long WINDOW_DURATION_MS = 60_000L; // 60 seconds

    private final int maxLookupsPerMinute;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> requestLogs = new ConcurrentHashMap<>();

    public CitizenLookupRateLimiter(@Value("${app.citizen.lookup.rate-limit:20}") int maxLookupsPerMinute) {
        this.maxLookupsPerMinute = Math.max(5, maxLookupsPerMinute);
    }

    /**
     * Checks if a receipt lookup request from the given client key (e.g. validated IP) is permitted.
     * @return true if permitted, false if rate limit exceeded.
     */
    public boolean tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            clientKey = "anonymous-default";
        }

        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_DURATION_MS;

        if (requestLogs.size() > MAX_TRACKED_CLIENTS) {
            pruneEntries(windowStart);
        }

        ConcurrentLinkedQueue<Long> timestamps = requestLogs.computeIfAbsent(clientKey, k -> new ConcurrentLinkedQueue<>());

        while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
            timestamps.poll();
        }

        if (timestamps.size() >= maxLookupsPerMinute) {
            log.warn("CitizenLookupRateLimiter: Rate limit exceeded for clientKey={} (limit={}/min)", clientKey, maxLookupsPerMinute);
            return false;
        }

        timestamps.offer(now);
        return true;
    }

    private synchronized void pruneEntries(long windowStart) {
        if (requestLogs.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }

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

        if (requestLogs.size() > MAX_TRACKED_CLIENTS) {
            int toEvict = requestLogs.size() - MAX_TRACKED_CLIENTS + 100;
            Iterator<Map.Entry<String, ConcurrentLinkedQueue<Long>>> evictIt = requestLogs.entrySet().iterator();
            while (evictIt.hasNext() && toEvict > 0) {
                evictIt.next();
                evictIt.remove();
                toEvict--;
            }
            log.info("CitizenLookupRateLimiter: Evicted stale entries. Active clients: {}", requestLogs.size());
        }
    }

    public int getMaxLookupsPerMinute() {
        return maxLookupsPerMinute;
    }

    public void reset() {
        requestLogs.clear();
    }
}
