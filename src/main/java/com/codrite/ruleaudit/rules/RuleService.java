package com.codrite.ruleaudit.rules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing business rules and coordinating their distribution.
 * <p>
 * This service provides CRUD operations for rules stored in MongoDB and
 * ensures that any changes are propagated to the Redis cache and other 
 * application instances via Redis Pub/Sub.
 */
@Slf4j
@Service
public class RuleService {

    /**
     * Redis channel name used for broadcasting rule change notifications.
     */
    public static final String CHANNEL = "rules-changed";

    private final RuleRepository repository;
    private final RuleRedisStore redisStore;
    private final StringRedisTemplate redisTemplate;

    public RuleService(RuleRepository repository, RuleRedisStore redisStore, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisStore = redisStore;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Retrieves all rules from the primary MongoDB repository.
     * @return A list of all rules.
     */
    public List<Rule> getAllRules() {
        return repository.findAll().collectList().block();
    }

    /**
     * Retrieves a single rule by its unique ID.
     * @param id The ID of the rule to retrieve.
     * @return The rule if found.
     * @throws RuntimeException if the rule does not exist.
     */
    public Rule getRule(String id) {
        return repository.findById(id).blockOptional().orElseThrow(() -> new RuntimeException("Rule not found: " + id));
    }

    /**
     * Persists or updates a rule and triggers a global refresh.
     * 
     * @param rule The rule to save.
     * @return The persisted rule.
     */
    public Rule saveRule(Rule rule) {
        log.info("Saving rule: {}", rule.getDescription());
        
        // Ensure the rule has a readable English ID instead of an auto-generated hex string
        if (rule.getId() == null || rule.getId().isBlank()) {
            String readableId = rule.getDescription().toLowerCase().replaceAll("[^a-z0-9]+", "-");
            // Remove leading/trailing dashes
            readableId = readableId.replaceAll("^-|-$", "");
            if (readableId.isBlank()) {
                readableId = "rule-" + System.currentTimeMillis();
            }
            rule.setId(readableId);
        }
        
        rule.setUpdatedAt(Instant.now());
        Rule saved = repository.save(rule).block();
        notifyChange();
        return saved;
    }

    /**
     * Deletes a rule and triggers a global refresh.
     * @param id The ID of the rule to delete.
     */
    public void deleteRule(String id) {
        log.info("Deleting rule with id: {}", id);
        repository.deleteById(id).block();
        notifyChange();
    }

    /**
     * Refreshes the local Redis store and broadcasts a notification to all 
     * instances to reload their local rule caches.
     */
    private void notifyChange() {
        log.debug("Notifying rule change on channel: {}", CHANNEL);
        // Step 1: Force an immediate update of the centralized Redis rule store
        redisStore.refresh();
        // Step 2: Send a broadcast message to all application nodes (including this one)
        redisTemplate.convertAndSend(CHANNEL, "reloaded");
    }
}
