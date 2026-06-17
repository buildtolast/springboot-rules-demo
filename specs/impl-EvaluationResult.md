# GENERATE: src/main/java/com/codrite/ruleaudit/eval/EvaluationResult.java

Implement the EvaluationResult record EXACTLY as in the contract above.
Requirements:
- Compact constructor: if a component is null, replace with an empty collection;
  then wrap each in an unmodifiable copy (List.copyOf / Map.copyOf). After this,
  the components are unmodifiable and not affected by later mutation of the inputs.
- CRITICAL: this is a RECORD COMPACT CONSTRUCTOR. Assign the bare parameter names,
  NOT `this.x`. Using `this.matchedRuleIds = ...` is a compile error. Correct form:
      public EvaluationResult {
          matchedRuleIds  = matchedRuleIds  == null ? List.of() : List.copyOf(matchedRuleIds);
          evaluatedRuleIds = evaluatedRuleIds == null ? List.of() : List.copyOf(evaluatedRuleIds);
          errors          = errors          == null ? Map.of()  : Map.copyOf(errors);
      }
- matched() returns !matchedRuleIds.isEmpty().
- verdict() returns AuditType.MATCHED if matched(); else AuditType.ERRORED if
  errors is not empty; else AuditType.UNMATCHED.
- Import com.codrite.ruleaudit.audit.AuditType.
