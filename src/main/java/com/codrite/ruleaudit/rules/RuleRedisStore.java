package com.codrite.ruleaudit.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Manages the persistence of active rules in a centralized Redis store.
 * <p>
 * While MongoDB remains the source of truth for all rules (active and inactive),
 * Redis acts as a high-speed distribution layer. This allows all application
 * nodes to fetch the current active rule set quickly during startup or after
 * a "reloaded" signal.
 */
@Slf4j
@Component
public class RuleRedisStore {

    /**
     * The Redis key where the active rules are stored as a serialized JSON array.
     */
    private static final String REDIS_KEY = "rules:active";

    private final StringRedisTemplate redisTemplate;
    private final RuleRepository repository;
    private final ObjectMapper objectMapper;

    public RuleRedisStore(StringRedisTemplate redisTemplate, 
                          RuleRepository repository, 
                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the current active rule set from Redis.
     * <p>
     * If the cache is empty or corrupted, it automatically falls back to 
     * MongoDB, refreshes the Redis cache, and returns the result.
     *
     * @return A list of active rules.
     */
    public List<Rule> getActiveRules() {
        String json = redisTemplate.opsForValue().get(REDIS_KEY);
        if (json != null) {
            try {
                // Deserialize the cached rule set
                return objectMapper.readValue(json, new TypeReference<List<Rule>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize rules from Redis, refreshing cache", e);
            }
        }
        // Cache miss or error: sync from Mongo
        return refresh();
    }

    /**
     * Synchronizes the Redis cache with the active rules from MongoDB.
     * 
     * @return The updated list of active rules.
     */
    public List<Rule> refresh() {
        log.info("Refreshing active rules cache in Redis");
        
        // Query MongoDB for only rules marked as 'active' (blocking for non-reactive service layer)
        List<Rule> activeRules = repository.findByActiveTrue().collectList().block();
        if (activeRules == null) {
            activeRules = Collections.emptyList();
        }
        log.debug("Found {} active rules in MongoDB", activeRules.size());
        
        try {
            // Serialize and update the centralized Redis cache
            String json = objectMapper.writeValueAsString(activeRules);
            redisTemplate.opsForValue().set(REDIS_KEY, json);
            log.info("Successfully updated Redis with {} active rules", activeRules.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rules for Redis", e);
            throw new RuntimeException("Failed to serialize rules for Redis", e);
        }
        return activeRules;
    }
}
