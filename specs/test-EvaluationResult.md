# GENERATE: src/test/java/com/codrite/ruleaudit/eval/EvaluationResultTest.java

JUnit 5 (org.junit.jupiter.api) + AssertJ. Test the EvaluationResult record from
the contract. Class: EvaluationResultTest in package com.codrite.ruleaudit.eval.
Import AuditType from com.codrite.ruleaudit.audit.AuditType.

ASSERTJ STYLE (mandatory): `import org.assertj.core.api.Assertions;` and call
`Assertions.assertThat(...)` / `Assertions.assertThatThrownBy(...)` fully
qualified. Do NOT use static imports (no `import static ...`).

Cover, one @Test per behavior, descriptive method names:
1. matched() is true when matchedRuleIds is non-empty; false when empty.
2. verdict() == MATCHED when there is at least one matched rule (even if errors is non-empty).
3. verdict() == ERRORED when no rules matched AND errors is non-empty.
4. verdict() == UNMATCHED when no rules matched AND errors is empty.
5. Constructing with null collections yields empty (not null) components and matched()==false, verdict()==UNMATCHED.
6. The record is defensively copied / unmodifiable. IMPORTANT: build the input
   as a MUTABLE list so the mutation is meaningful:
       java.util.List<String> input = new java.util.ArrayList<>();
       input.add("r1");
       EvaluationResult result = new EvaluationResult(input, List.of("r1"), Map.of());
       input.add("r2");                               // must NOT affect result
       Assertions.assertThat(result.matchedRuleIds()).containsExactly("r1");
   Then assert calling add() on a returned collection throws
   UnsupportedOperationException (use Assertions.assertThatThrownBy).

Use realistic data (rule ids like "r1","r2"; errors like Map.of("r3","boom")).
Output exactly one fenced java code block: the complete test file.
