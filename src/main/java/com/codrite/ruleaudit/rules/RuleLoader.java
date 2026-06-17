package com.codrite.ruleaudit.rules;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

@Component
public class RuleLoader {
    private static final Logger log = LoggerFactory.getLogger(RuleLoader.class);

    private final RuleRedisStore redisStore;
    private final RuleCache cache;

    public RuleLoader(RuleRedisStore redisStore, RuleCache cache) {
        this.redisStore = redisStore;
        this.cache = cache;
    }

    public int reload() {
        List<Rule> rules = redisStore.getActiveRules();
        List<CompiledRule> compiled = new ArrayList<>();

        for (Rule r : rules) {
            try {
                compiled.add(CompiledRule.compile(String.valueOf(r.getId()),
                        r.getDescription(), r.getSpelExpression()));
            } catch (RuntimeException e) {
                log.warn("Skipping un-parseable rule id={}: {}", r.getId(), e.getMessage());
            }
        }

        cache.replace(compiled);
        log.info("Loaded {} active rules into cache", compiled.size());
        return compiled.size();
    }
}
