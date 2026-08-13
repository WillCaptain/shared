package org.twelve.shared.llm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe API key pool with round-robin acquire and temporary cooldown on failures.
 *
 * <p>Does not perform HTTP. Pair with {@link LlmPoolClient} for request-level assignment.
 */
public final class LlmPool {

    public static final long DEFAULT_COOLDOWN_SECONDS = 60L;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger cursor = new AtomicInteger();
    private final Map<String, KeyState> states = new ConcurrentHashMap<>();
    private volatile LLMConfig baseConfig;
    private volatile List<String> keys = List.of();
    private final long cooldownSeconds;

    private LlmPool(LLMConfig baseConfig, long cooldownSeconds) {
        this.baseConfig = Objects.requireNonNull(baseConfig, "baseConfig").withApiKey("");
        this.cooldownSeconds = cooldownSeconds > 0 ? cooldownSeconds : DEFAULT_COOLDOWN_SECONDS;
    }

    public static LlmPool of(LLMConfig base) {
        return new LlmPool(base, DEFAULT_COOLDOWN_SECONDS);
    }

    public static LlmPool of(LLMConfig base, Collection<String> keys) {
        LlmPool pool = of(base);
        pool.replaceKeys(keys);
        return pool;
    }

    public static LlmPool of(LLMConfig base, Collection<String> keys, long cooldownSeconds) {
        LlmPool pool = new LlmPool(base, cooldownSeconds);
        pool.replaceKeys(keys);
        return pool;
    }

    public void setBaseConfig(LLMConfig base) {
        Objects.requireNonNull(base, "base");
        this.baseConfig = base.withApiKey("");
    }

    public LLMConfig baseConfig() {
        return baseConfig;
    }

    public void addKey(String key) {
        if (key == null || key.isBlank() || key.contains("*")) return;
        lock.lock();
        try {
            LinkedHashSet<String> next = new LinkedHashSet<>(keys);
            String k = key.trim();
            if (next.add(k)) {
                states.putIfAbsent(k, new KeyState());
                keys = List.copyOf(next);
            }
        } finally {
            lock.unlock();
        }
    }

    public void addKeys(Collection<String> more) {
        if (more == null || more.isEmpty()) return;
        for (String k : more) addKey(k);
    }

    public boolean removeKey(String key) {
        if (key == null || key.isBlank()) return false;
        lock.lock();
        try {
            LinkedHashSet<String> next = new LinkedHashSet<>(keys);
            boolean removed = next.remove(key.trim());
            if (removed) {
                keys = List.copyOf(next);
                states.remove(key.trim());
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    public int removeKeys(Collection<String> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) return 0;
        int n = 0;
        for (String k : toRemove) {
            if (removeKey(k)) n++;
        }
        return n;
    }

    public void clearKeys() {
        lock.lock();
        try {
            keys = List.of();
            states.clear();
        } finally {
            lock.unlock();
        }
    }

    public void replaceKeys(Collection<String> nextKeys) {
        lock.lock();
        try {
            LinkedHashSet<String> next = new LinkedHashSet<>();
            if (nextKeys != null) {
                for (String k : nextKeys) {
                    if (k == null) continue;
                    String s = k.trim();
                    if (!s.isBlank() && !s.contains("*")) next.add(s);
                }
            }
            keys = List.copyOf(next);
            states.keySet().retainAll(next);
            for (String k : next) states.putIfAbsent(k, new KeyState());
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public List<String> maskedKeys() {
        List<String> out = new ArrayList<>();
        for (String k : keys) out.add(maskKey(k));
        return List.copyOf(out);
    }

    /** Snapshot of plaintext keys (Host persistence / reload only — never log). */
    public List<String> snapshotKeys() {
        return keys;
    }

    public Optional<String> acquire() {
        List<String> snapshot = keys;
        if (snapshot.isEmpty()) return Optional.empty();
        Instant now = Instant.now();
        int n = snapshot.size();
        int start = Math.floorMod(cursor.getAndIncrement(), n);

        // Prefer non-cooling keys.
        for (int i = 0; i < n; i++) {
            String key = snapshot.get((start + i) % n);
            KeyState st = states.get(key);
            if (st == null || !st.isCooling(now)) {
                if (st != null) st.markAcquired(now);
                return Optional.of(key);
            }
        }
        // All cooling — fail open on the round-robin pick.
        String fallback = snapshot.get(start);
        KeyState st = states.get(fallback);
        if (st != null) st.markAcquired(now);
        return Optional.of(fallback);
    }

    public void reportSuccess(String key) {
        if (key == null) return;
        KeyState st = states.get(key);
        if (st != null) st.markSuccess(Instant.now());
    }

    public void reportFailure(String key, int httpStatus, String message) {
        if (key == null) return;
        KeyState st = states.get(key);
        if (st == null) return;
        boolean cooldown = httpStatus == 401 || httpStatus == 429;
        st.markFailure(Instant.now(), cooldown ? cooldownSeconds : 0L, message);
    }

    public static String maskKey(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    public static boolean isRetryableStatus(int httpStatus) {
        return httpStatus == 401 || httpStatus == 429;
    }

    /** Parse {@code LLM API error 401: ...} style messages from {@link LLMCaller}. */
    public static Optional<Integer> parseHttpStatus(Throwable error) {
        if (error == null || error.getMessage() == null) return Optional.empty();
        String msg = error.getMessage();
        int idx = msg.indexOf("LLM API error ");
        if (idx < 0) return Optional.empty();
        int start = idx + "LLM API error ".length();
        int end = start;
        while (end < msg.length() && Character.isDigit(msg.charAt(end))) end++;
        if (end == start) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(msg.substring(start, end)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static final class KeyState {
        private volatile Instant cooldownUntil = Instant.EPOCH;
        private volatile Instant lastUsedAt = Instant.EPOCH;
        private volatile int failureCount;
        private volatile String lastError = "";

        synchronized boolean isCooling(Instant now) {
            return cooldownUntil != null && now.isBefore(cooldownUntil);
        }

        synchronized void markAcquired(Instant now) {
            lastUsedAt = now;
        }

        synchronized void markSuccess(Instant now) {
            lastUsedAt = now;
            cooldownUntil = Instant.EPOCH;
            lastError = "";
        }

        synchronized void markFailure(Instant now, long cooldownSec, String message) {
            failureCount++;
            lastUsedAt = now;
            lastError = message == null ? "" : message;
            if (cooldownSec > 0) {
                cooldownUntil = now.plusSeconds(cooldownSec);
            }
        }
    }
}
