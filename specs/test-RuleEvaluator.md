# GENERATE: src/test/java/com/codrite/ruleaudit/eval/RuleEvaluatorTest.java

JUnit 5 + AssertJ. Class: RuleEvaluatorTest in package com.codrite.ruleaudit.eval.
Imports: com.codrite.ruleaudit.rules.CompiledRule, com.codrite.ruleaudit.audit.AuditType.

Subject: new RuleEvaluator(). Build rules with CompiledRule.compile(id, desc, spel).
Build roots as java.util.Map<String,Object> directly (e.g. Map.of("amount",5000,"region","EU")).
Use List.of(...) for rule lists. One @Test per behavior:

1. Single rule that matches: rule r1 = "['amount'] > 1000", root amount=5000.
   -> verdict()==MATCHED, matchedRuleIds == ["r1"], evaluatedRuleIds == ["r1"], errors empty.
2. Single rule that does not match: r1 = "['amount'] > 1000", root amount=10.
   -> verdict()==UNMATCHED, matchedRuleIds empty, evaluatedRuleIds==["r1"], errors empty.
3. Match-wins: r1="['amount'] > 1000" (matches, amount=5000), r2 references a
   missing method to force a throw, e.g. "['amount'].noSuchMethod()".
   -> verdict()==MATCHED, matchedRuleIds contains "r1", errors contains key "r2".
4. Errored only: r1="['amount'] > 1000" with amount=10 (no match) plus r2 that
   throws ("['amount'].noSuchMethod()"). -> verdict()==ERRORED,
   matchedRuleIds empty, errors contains "r2", evaluatedRuleIds==["r1","r2"].
5. Empty rule list: -> verdict()==UNMATCHED, all collections empty.
6. SpEL injection is blocked: rule r1 =
   "T(java.lang.Runtime).getRuntime().exec('echo pwned') != null".
   With SimpleEvaluationContext this must NOT execute; expect it is treated as a
   non-match with an error recorded for r1 (errors contains "r1"), verdict()!=MATCHED.
7. Wrong-typed field does not crash the evaluator: r1="['amount'] > 1000" but
   root amount="not-a-number" (a String). The evaluator must return a result
   (either non-match or error for r1) and must NOT throw out of evaluate().

Output exactly one fenced java code block: the complete test file.
