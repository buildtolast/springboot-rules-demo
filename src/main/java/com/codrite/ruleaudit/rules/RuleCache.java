package com.codrite.ruleaudit.rules;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RuleCache {

    private final AtomicReference<List<CompiledRule>> ref = new AtomicReference<>(List.of());

    public RuleCache() {
        // Default constructor required by Spring
    }

    public List<CompiledRule> get() {
        return ref.get();
    }

    public void replace(List<CompiledRule> rules) {
        List<CompiledRule> toSet = (rules == null) ? List.of() : List.copyOf(rules);
        ref.set(toSet);
    }
}
