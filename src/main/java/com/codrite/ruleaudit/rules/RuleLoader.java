package com.codrite.ruleaudit.rules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Component responsible for fetching active rules and compiling them for the evaluation engine.
 * <p>
 * This class bridge the gap between persistent storage (Redis/Mongo) and 
 * the high-performance evaluation cache.
 */
@Slf4j
@Component
public class RuleLoader {

    private final RuleRedisStore redisStore;
    private final RuleCache cache;

    public RuleLoader(RuleRedisStore redisStore, RuleCache cache) {
        this.redisStore = redisStore;
        this.cache = cache;
    }

    /**
     * Synchronizes the local rule cache with the centralized rule store.
     * <p>
     * It performs the following steps:
     * <ol>
     *     <li>Fetches active rules from the {@link RuleRedisStore}.</li>
     *     <li>Compiles each rule's SpEL expression.</li>
     *     <li>Validates the compilation; invalid rules are skipped and logged.</li>
     *     <li>Replaces the content of the {@link RuleCache} with the new compiled set.</li>
     * </ol>
     * 
     * @return The number of successfully compiled and loaded rules.
     */
    public int reload() {
        // Step 1: Fetch active rule definitions
        List<Rule> rules = redisStore.getActiveRules();
        List<CompiledRule> compiled = new ArrayList<>();

        // Step 2: Compile expressions into executable SpEL objects
        for (Rule r : rules) {
            try {
                compiled.add(CompiledRule.compile(String.valueOf(r.getId()),
                        r.getDescription(), r.getSpelExpression()));
            } catch (RuntimeException e) {
                // If a rule is syntactically invalid, we ignore it to prevent crashing the pipeline
                log.warn("Skipping un-parseable rule id={}: {}", r.getId(), e.getMessage());
            }
        }

        // Step 3: Swap the cache with the fresh rule set
        cache.replace(compiled);
        log.info("Loaded {} active rules into cache", compiled.size());
        return compiled.size();
    }
}
