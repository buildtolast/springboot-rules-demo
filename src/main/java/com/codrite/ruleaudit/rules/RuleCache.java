package com.codrite.ruleaudit.rules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory local cache for compiled SpEL rules.
 * <p>
 * This component provides low-latency, thread-safe access to rules for the 
 * Kafka Streams processor. It uses an {@link AtomicReference} to allow
 * atomic "hot" swaps of the entire rule set without interrupting processing.
 */
@Slf4j
@Component
public class RuleCache {

    /**
     * The current set of compiled and active rules.
     */
    private final AtomicReference<List<CompiledRule>> ref = new AtomicReference<>(List.of());

    public RuleCache() {
        // Default constructor required by Spring
    }

    /**
     * Returns the current immutable list of compiled rules.
     * @return List of active compiled rules.
     */
    public List<CompiledRule> get() {
        return ref.get();
    }

    /**
     * Atomically replaces the current rule set with a new one.
     * @param rules The new set of compiled rules.
     */
    public void replace(List<CompiledRule> rules) {
        List<CompiledRule> toSet = (rules == null) ? List.of() : List.copyOf(rules);
        int count = toSet.size();
        log.info("Swapping local rule cache: {} rules are now active", count);
        ref.set(toSet);
    }
}
