package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;
import com.codrite.ruleaudit.rules.CompiledRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a set of compiled SpEL rules against a JSON-derived data structure.
 * <p>
 * This class uses Spring Expression Language (SpEL) to evaluate logic against the input map.
 */
@Slf4j
public class RuleEvaluator {
    
    /**
     * Shared evaluation context configured for safe, read-only access to root object properties.
     * Uses StandardEvaluationContext to support complex features like selection and projection,
     * but disables type referencing (T()) for security.
     */
    private final EvaluationContext context;

    public RuleEvaluator() {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        // Disable type referencing to prevent security issues (like T(java.lang.Runtime))
        ctx.setTypeLocator(typeName -> {
            throw new org.springframework.expression.EvaluationException("Type referencing is disabled");
        });
        this.context = ctx;
    }

    /**
     * Evaluates all provided rules against the root data object.
     *
     * @param root  The data object (usually a Map) representing the record to evaluate.
     * @param rules The list of {@link CompiledRule}s to apply.
     * @return An {@link EvaluationResult} containing per-rule results.
     */
    public EvaluationResult evaluate(Map<String, Object> root, List<CompiledRule> rules) {
        log.debug("Evaluating {} rules against root object", rules.size());
        
        List<RuleResult> results = new ArrayList<>();

        // Iterate through each rule and evaluate its expression against the root data
        for (CompiledRule rule : rules) {
            try {
                // Execute SpEL expression. Expects a Boolean result.
                Boolean matched = rule.expression().getValue(context, root, Boolean.class);
                
                if (Boolean.TRUE.equals(matched)) {
                    log.debug("Rule matched: {} ({})", rule.description(), rule.id());
                    // For matched rules, no reason is required
                    results.add(new RuleResult(rule.id(), AuditType.MATCHED, null));
                } else {
                    // For unmatched rules, provide details about the comparison and values
                    results.add(new RuleResult(rule.id(), AuditType.UNMATCHED, createUnmatchedReason(rule, root)));
                }
            } catch (RuntimeException e) {
                // If evaluation fails (e.g. property not found), we record it as an error for auditing
                log.warn("Error evaluating rule {} ({}): {}", rule.description(), rule.id(), e.getMessage());
                String message = e.getMessage();
                results.add(new RuleResult(rule.id(), AuditType.ERRORED, message == null ? e.toString() : message));
            }
        }

        log.debug("Evaluation finished: {} rules processed", rules.size());
        return new EvaluationResult(results);
    }

    /**
     * Creates a descriptive reason for why a rule did not match.
     * Includes the expression and the values of the fields referenced in it.
     */
    private String createUnmatchedReason(CompiledRule rule, Map<String, Object> root) {
        String expression = rule.expression().getExpressionString();

        // Collect values of fields mentioned in the expression to provide context
        List<String> contextParts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String key = entry.getKey();
            // Simple check to see if the key is likely used in the expression
            if (expression.contains(key)) {
                contextParts.add(key + "=" + entry.getValue());
            }
        }

        StringBuilder reason = new StringBuilder("Condition not met: ").append(expression);
        if (!contextParts.isEmpty()) {
            reason.append(" (actual values: ").append(String.join(", ", contextParts)).append(")");
        }
        return reason.toString();
    }
}
