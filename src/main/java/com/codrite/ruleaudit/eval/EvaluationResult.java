package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;
import java.util.List;
import java.util.Map;

public record EvaluationResult(
        java.util.List<String> matchedRuleIds,
        java.util.List<String> evaluatedRuleIds,
        java.util.Map<String,String> errors) {

    public EvaluationResult {
        matchedRuleIds = matchedRuleIds == null ? List.of() : List.copyOf(matchedRuleIds);
        evaluatedRuleIds = evaluatedRuleIds == null ? List.of() : List.copyOf(evaluatedRuleIds);
        errors = errors == null ? Map.of() : Map.copyOf(errors);
    }

    public boolean matched() {
        return !matchedRuleIds.isEmpty();
    }

    public AuditType verdict() {
        if (matched()) {
            return AuditType.MATCHED;
        }
        if (!errors.isEmpty()) {
            return AuditType.ERRORED;
        }
        return AuditType.UNMATCHED;
    }
}
