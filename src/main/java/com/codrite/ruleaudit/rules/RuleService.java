package com.codrite.ruleaudit.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    public static final String CHANNEL = "rules-changed";

    private final RuleRepository repository;
    private final RuleRedisStore redisStore;
    private final StringRedisTemplate redisTemplate;

    public RuleService(RuleRepository repository, RuleRedisStore redisStore, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisStore = redisStore;
        this.redisTemplate = redisTemplate;
    }

    public List<Rule> getAllRules() {
        return repository.findAll();
    }

    public Rule getRule(String id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Rule not found: " + id));
    }

    public Rule saveRule(Rule rule) {
        log.info("Saving rule: {}", rule.getDescription());
        rule.setUpdatedAt(Instant.now());
        Rule saved = repository.save(rule);
        notifyChange();
        return saved;
    }

    public void deleteRule(String id) {
        log.info("Deleting rule with id: {}", id);
        repository.deleteById(id);
        notifyChange();
    }

    private void notifyChange() {
        log.debug("Notifying rule change on channel: {}", CHANNEL);
        redisStore.refresh();
        redisTemplate.convertAndSend(CHANNEL, "reloaded");
    }
}
