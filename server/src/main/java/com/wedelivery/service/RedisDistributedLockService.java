package com.wedelivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Redis Distributed Lock Service.
 * Implements atomic lock acquisition (SETNX with TTL) and safe Lua-script release
 * to prevent double-allocation races during concurrent vehicle dispatch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

    private final StringRedisTemplate stringRedisTemplate;

    // Lua script to safely release the lock only if the value matches (prevents releasing other threads' locks)
    private static final String UNLOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    // In-memory fallback locks in case Redis is temporarily unreachable in dev/test
    private final ConcurrentHashMap<String, ReentrantLock> fallbackLocks = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire a distributed lock.
     *
     * @param lockKey Key representing the locked resource (e.g. "lock:vehicle:V101")
     * @param expireTimeMs Expiration time in milliseconds to prevent deadlocks
     * @return Lock token (UUID) if acquired, or null if lock acquisition failed
     */
    public String tryAcquireLock(String lockKey, long expireTimeMs) {
        String lockToken = UUID.randomUUID().toString();
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken, expireTimeMs, TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(success)) {
                log.debug("[RedisLock] Successfully acquired distributed lock: key={}, token={}", lockKey, lockToken);
                return lockToken;
            } else {
                log.warn("[RedisLock] Failed to acquire lock for key={}. Resource is currently locked by another worker.", lockKey);
                return null;
            }
        } catch (Exception e) {
            log.warn("[RedisLock] Redis connection failed, falling back to in-memory lock: {}", e.getMessage());
            ReentrantLock localLock = fallbackLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
            if (localLock.tryLock()) {
                return lockToken;
            }
            return null;
        }
    }

    /**
     * Safely releases the distributed lock using a Lua script.
     *
     * @param lockKey The locked resource key
     * @param lockToken The token issued when the lock was acquired
     */
    public void releaseLock(String lockKey, String lockToken) {
        if (lockToken == null) return;

        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(UNLOCK_LUA_SCRIPT);
            redisScript.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(lockKey), lockToken);
            if (Long.valueOf(1L).equals(result)) {
                log.debug("[RedisLock] Released distributed lock: key={}", lockKey);
            } else {
                log.warn("[RedisLock] Lock release skipped: token mismatch or lock expired: key={}", lockKey);
            }
        } catch (Exception e) {
            log.warn("[RedisLock] Fallback unlocking for key={}", lockKey);
            ReentrantLock localLock = fallbackLocks.get(lockKey);
            if (localLock != null && localLock.isHeldByCurrentThread()) {
                localLock.unlock();
            }
        }
    }
}
