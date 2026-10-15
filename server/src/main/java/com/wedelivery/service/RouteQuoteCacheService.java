package com.wedelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.dto.QuoteRequest;
import com.wedelivery.dto.QuoteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Cache-Aside caching layer for routing quotes.
 * Caches computationally expensive OSRM road distance and transit duration calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteQuoteCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String QUOTE_CACHE_PREFIX = "quote:route:";
    private static final Duration QUOTE_CACHE_TTL = Duration.ofMinutes(15);

    /**
     * Generates a deterministic cache key based on pickup, dropoff, and package parameters.
     */
    public String generateCacheKey(QuoteRequest request) {
        String raw = String.format(
                "%.4f,%.4f->%.4f,%.4f|weight:%.2f|vol:%.4f|station:%s",
                request.getPickupLat() != null ? request.getPickupLat().doubleValue() : 0.0,
                request.getPickupLng() != null ? request.getPickupLng().doubleValue() : 0.0,
                request.getDropoffLat() != null ? request.getDropoffLat().doubleValue() : 0.0,
                request.getDropoffLng() != null ? request.getDropoffLng().doubleValue() : 0.0,
                request.getPackageWeight() != null ? request.getPackageWeight().doubleValue() : 0.0,
                request.getPackageVolume() != null ? request.getPackageVolume().doubleValue() : 0.0,
                request.getStationId() != null ? request.getStationId() : "NONE"
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return QUOTE_CACHE_PREFIX + hexString;
        } catch (Exception e) {
            return QUOTE_CACHE_PREFIX + raw.hashCode();
        }
    }

    /**
     * Cache-Aside Read: attempts to fetch calculated quote from Redis.
     */
    public QuoteResponse getCachedQuote(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json != null && !json.isBlank()) {
                log.info("[RedisCache-Aside] Cache HIT for routing quote: key={}", cacheKey);
                return objectMapper.readValue(json, QuoteResponse.class);
            }
        } catch (Exception e) {
            log.warn("[RedisCache-Aside] Failed to read from Redis cache: {}", e.getMessage());
        }
        log.debug("[RedisCache-Aside] Cache MISS for routing quote: key={}", cacheKey);
        return null;
    }

    /**
     * Cache-Aside Write: writes freshly computed quote into Redis with TTL.
     */
    public void putCachedQuote(String cacheKey, QuoteResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(cacheKey, json, QUOTE_CACHE_TTL);
            log.info("[RedisCache-Aside] Cached routing quote with TTL {}: key={}", QUOTE_CACHE_TTL, cacheKey);
        } catch (Exception e) {
            log.warn("[RedisCache-Aside] Failed to write routing quote to Redis: {}", e.getMessage());
        }
    }
}
