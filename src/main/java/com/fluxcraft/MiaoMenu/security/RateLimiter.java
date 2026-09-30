package com.fluxcraft.MiaoMenu.security;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter {
    // 每隔這麼多次造訪觸發一次過期窗口掃描，避免無界增長的同時不增加每次調用成本
    private static final int CLEANUP_INTERVAL = 256;

    private final long windowMillis;
    private final int maxEvents;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger accessCount = new AtomicInteger();

    public RateLimiter(Duration window, int maxEvents) {
        this.windowMillis = window.toMillis();
        this.maxEvents = maxEvents;
    }

    public boolean allow(UUID uuid) {
        long now = System.currentTimeMillis();
        maybeCleanup(now);

        AtomicBoolean allowed = new AtomicBoolean(false);
        windows.compute(uuid, (_, current) -> {
            if (current == null || now - current.windowStart() >= windowMillis) {
                allowed.set(true);
                return new Window(now, 1);
            }
            if (current.count() >= maxEvents) {
                return current;
            }
            allowed.set(true);
            return new Window(current.windowStart(), current.count() + 1);
        });
        return allowed.get();
    }

    // 移除指定玩家的限流窗口，供玩家離線事件調用
    public void clear(UUID uuid) {
        windows.remove(uuid);
    }

    public void remove(UUID uuid) {
        clear(uuid);
    }

    public void clearAll() {
        windows.clear();
    }

    // 機會式回收：僅在固定造訪間隔觸發，刪除所有已過期窗口。
    // 使用 remove(key, value) 按值比對刪除，避免誤刪並發更新後的新窗口。
    private void maybeCleanup(long now) {
        if (accessCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        windows.forEach((uuid, window) -> {
            if (now - window.windowStart() >= windowMillis) {
                windows.remove(uuid, window);
            }
        });
    }

    private record Window(long windowStart, int count) {
    }
}
