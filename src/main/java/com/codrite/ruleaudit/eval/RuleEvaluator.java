package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.rules.CompiledRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleEvaluator {
    private static final Logger log = LoggerFactory.getLogger(RuleEvaluator.class);
    private final EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

    public EvaluationResult evaluate(java.util.Map<String, Object> root,
                                     java.util.List<CompiledRule> rules) {
        log.debug("Evaluating {} rules against root object", rules.size());
        java.util.List<String> matchedRuleIds = new java.util.ArrayList<>();
        java.util.List<String> evaluatedRuleIds = new java.util.ArrayList<>();
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();

        for (CompiledRule rule : rules) {
            evaluatedRuleIds.add(rule.id());

            try {
                Boolean value = rule.expression().getValue(context, root, Boolean.class);
                if (Boolean.TRUE.equals(value)) {
                    log.debug("Rule matched: {} ({})", rule.description(), rule.id());
                    matchedRuleIds.add(rule.id());
                }
            } catch (RuntimeException e) {
                log.warn("Error evaluating rule {} ({}): {}", rule.description(), rule.id(), e.getMessage());
                String message = e.getMessage();
                errors.put(rule.id(), message == null ? e.toString() : message);
            }
        }

        log.debug("Evaluation finished: {} matches out of {} rules", matchedRuleIds.size(), rules.size());
        return new EvaluationResult(matchedRuleIds, evaluatedRuleIds, errors);
    }
}
