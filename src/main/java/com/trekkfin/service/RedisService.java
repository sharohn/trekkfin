package com.trekkfin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trekkfin.dto.TransferResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class RedisService {

    private static final String KEY_PREFIX = "idempotency:transfer:";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Tries to initiate a request with an idempotency key.
     * Uses atomic SETNX (set-if-not-exists) with a 5-minute TTL.
     * Returns true if the key did not exist and was successfully marked as IN_PROGRESS.
     */
    public boolean startRequest(String key) {
        String redisKey = KEY_PREFIX + key;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, LOCK_TTL);
        return success != null && success;
    }

    /**
     * Checks if a cached response already exists for the given key and is not IN_PROGRESS.
     */
    public Optional<TransferResponse> getCachedResponse(String key) {
        String redisKey = KEY_PREFIX + key;
        String val = redisTemplate.opsForValue().get(redisKey);
        if (val == null || IN_PROGRESS.equals(val)) {
            return Optional.empty();
        }
        try {
            TransferResponse cachedResponse = objectMapper.readValue(val, TransferResponse.class);
            return Optional.of(cachedResponse);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Serializes and saves the final TransferResponse to Redis, overwriting the IN_PROGRESS marker.
     * Sets a TTL of 24 hours.
     */
    public void saveResponse(String key, TransferResponse response) {
        String redisKey = KEY_PREFIX + key;
        try {
            String jsonVal = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, jsonVal, CACHE_TTL);
        } catch (JsonProcessingException e) {
            removeKey(key);
            throw new RuntimeException("Failed to serialize transaction response", e);
        }
    }

    /**
     * Deletes the key from Redis. Used for system/database exceptions.
     */
    public void removeKey(String key) {
        String redisKey = KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
    }
}
