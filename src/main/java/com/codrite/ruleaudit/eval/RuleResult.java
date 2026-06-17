package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;

/**
 * Result of evaluating a single rule.
 */
public record RuleResult(
        String ruleId,
        AuditType type,
        String reason) {
}
