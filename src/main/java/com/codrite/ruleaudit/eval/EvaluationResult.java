package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;
import java.util.List;

/**
 * Captures the outcome of evaluating a set of rules against a single record.
 * 
 * @param ruleResults Detailed results for each rule evaluated.
 */
public record EvaluationResult(java.util.List<RuleResult> ruleResults) {

    /**
     * Compact constructor to ensure non-null, immutable lists.
     */
    public EvaluationResult {
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
    }

    /**
     * @return true if at least one rule matched.
     */
    public boolean matched() {
        return ruleResults.stream().anyMatch(r -> r.type() == AuditType.MATCHED);
    }

    /**
     * Derives the overall audit verdict for the evaluation session.
     * 
     * @return {@link AuditType#MATCHED} if any rule matched, 
     *         {@link AuditType#ERRORED} if no matches but errors occurred, 
     *         otherwise {@link AuditType#UNMATCHED}.
     */
    public AuditType verdict() {
        if (matched()) {
            return AuditType.MATCHED;
        }
        if (ruleResults.stream().anyMatch(r -> r.type() == AuditType.ERRORED)) {
            return AuditType.ERRORED;
        }
        return AuditType.UNMATCHED;
    }
}
