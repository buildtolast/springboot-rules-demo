# GENERATE: src/main/java/com/codrite/ruleaudit/eval/RuleEvaluator.java

Implement the RuleEvaluator class EXACTLY as in the contract above.
Requirements:
- Package com.codrite.ruleaudit.eval.
- Imports: com.codrite.ruleaudit.rules.CompiledRule,
  org.springframework.expression.spel.support.SimpleEvaluationContext,
  org.springframework.expression.EvaluationContext, java.util.* .
- evaluate(Map<String,Object> root, List<CompiledRule> rules):
  - matchedRuleIds = new ArrayList<>(); evaluatedRuleIds = new ArrayList<>();
    errors = new LinkedHashMap<>();
  - Build context once: EvaluationContext context =
    SimpleEvaluationContext.forReadOnlyDataBinding().build();
  - For each rule in rules (preserve order):
      evaluatedRuleIds.add(rule.id());
      try {
        Boolean v = rule.expression().getValue(context, root, Boolean.class);
        if (Boolean.TRUE.equals(v)) matchedRuleIds.add(rule.id());
        // null or false -> not matched, no error
      } catch (RuntimeException e) {   // includes SpEL EvaluationException
        errors.put(rule.id(), e.getMessage() == null ? e.toString() : e.getMessage());
      }
  - return new EvaluationResult(matchedRuleIds, evaluatedRuleIds, errors);
- Never let an exception escape evaluate(). The security of SimpleEvaluationContext
  is what blocks T(...) type references (they throw, are caught, and recorded).
