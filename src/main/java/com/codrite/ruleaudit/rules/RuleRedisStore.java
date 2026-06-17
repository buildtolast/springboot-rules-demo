package com.codrite.ruleaudit.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Manages the active rule list in Redis.
 * Key: rules:active
 * Truth: MongoDB (RuleRepository)
 */
@Component
public class RuleRedisStore {

    private static final Logger log = LoggerFactory.getLogger(RuleRedisStore.class);

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

    public List<Rule> getActiveRules() {
        String json = redisTemplate.opsForValue().get(REDIS_KEY);
        if (json != null) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<Rule>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize rules from Redis, refreshing cache", e);
            }
        }
        return refresh();
    }

    public List<Rule> refresh() {
        log.info("Refreshing active rules cache in Redis");
        List<Rule> activeRules = repository.findByActiveTrue();
        log.debug("Found {} active rules in MongoDB", activeRules.size());
        try {
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
